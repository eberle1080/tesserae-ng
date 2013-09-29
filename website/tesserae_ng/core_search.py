import StringIO
import re
import logging

from django.http import HttpResponse, Http404
from django.views.decorators.http import require_GET, require_POST
from django.conf import settings
from views import _render
from website.tesserae_ng.models import SourceText, SourceTextSentence
from website.tesserae_ng.forms import SimpleSearchForm
import reversion


# Get an instance of a logger
logger = logging.getLogger(__name__)


def show_search(request, language, level):
    """
    The core search entry point
    """

    args = {'language': language, 'user': request.user,
            'authenticated': request.user.is_authenticated(),
            'form': SimpleSearchForm()}

    if language in ('latin', 'greek', 'english'):
        if language == 'latin' and level == 'basic':
            return _render(request, 'basic_search.html', args)
        elif level == 'advanced':
            return _render(request, 'advanced_search.html', args)

    raise Http404()


def do_search(request, language, level):
    """
    A search form was submitted
    """

    if level == 'basic':
        form = SimpleSearchForm(request.GET)
        if form.is_valid():
            return _search_basic(request, form, language)
        else:
            args = {'language': language, 'user': request.user,
                    'authenticated': request.user.is_authenticated(),
                    'form': form }
            return _render(request, 'basic_search.html', args)

    raise Http404()


def _window_pages(page_info, maximum_pages):
    if len(page_info) == 0:
        return None, None, page_info 
    if len(page_info) <= maximum_pages:
        return page_info[0], page_info[-1], page_info

    from collections import deque
    d = deque()
    active = None
    appendsSinceActive = 0
    half = maximum_pages // 2

    for pi in page_info:
        if len(d) >= maximum_pages:
            d.popleft()
        d.append(pi)

        if pi['active']:
            active = pi
        else:
            if active is not None:
                appendsSinceActive += 1

        if len(d) >= maximum_pages and appendsSinceActive >= half:
            break

    results = []
    first = True
    for pi in d:
        not_first = not first
        first = False
        pi['not_first'] = not_first
        results.append(pi)

    return page_info[0], page_info[-1], results


def _search_basic(request, form, language):
    """
    The user wants to do a basic search
    """

    from custom_solr import basic_search
    source = form.cleaned_data['source']
    target = form.cleaned_data['target']
    initial_offset = form.cleaned_data['start']
    rows_per_page = form.cleaned_data['rows']

    results = basic_search(source, target, language, start=initial_offset, rows=rows_per_page)

    if results.has_key('error'):
        raise RuntimeError(results['error']['msg'])

    qtime = results['responseHeader']['QTime']
    matches = results['matches']
    matchTotal = results['matchTotal']
    matchStart = results['matchOffset'] + 1
    matchEnd = results['matchOffset'] + results['matchCount']
    stopList = results['stopList']
    stopListStr = ', '.join(sorted(stopList))

    myFirstIndex = results['matchOffset']
    myLastIndex = results['matchOffset'] + results['matchCount'] - 1

    def isThisPage(beginMatchIndex, endMatchIndex):
        if beginMatchIndex >= myFirstIndex and endMatchIndex <= myLastIndex:
            return True
        return False

    indicesRemaining = matchTotal
    currentStartIndex = None

    pageInfo = []
    pageCounter = 0
    first = True

    while indicesRemaining > 0:
        if currentStartIndex is None:
            currentStartIndex = 0
        else:
            currentStartIndex += rows_per_page
        if indicesRemaining >= rows_per_page:
            currentEndIndex = currentStartIndex + rows_per_page - 1
        else:
            currentEndIndex = currentStartIndex + indicesRemaining - 1
        collected = currentEndIndex - currentStartIndex + 1
        indicesRemaining -= collected
        currentPageCounter = pageCounter
        pageCounter += 1

        href = '/search/' + language + '/basic/search?start=' + \
               str(currentStartIndex) + '&rows=' + str(rows_per_page) + \
               '&source=' + str(source.id) + '&target=' + str(target.id)

        page = {'num': pageCounter, 'start': currentStartIndex,
                'active': isThisPage(currentStartIndex, currentEndIndex),
                'not_first': not first, 'href': href}

        pageInfo.append(page)
        first = False

    firstPage, lastPage, windowedPages = _window_pages(pageInfo, 9)

    args = { 'language': language, 'user': request.user,
             'authenticated': request.user.is_authenticated(),
             'source': source, 'target': target,
             'qtime': qtime, 'matches': matches,
             'matchTotal': matchTotal, 'matchStart': matchStart,
             'matchEnd': matchEnd, 'pageInfo': windowedPages,
             'firstPage': firstPage, 'lastPage': lastPage,
             'stopList': stopList, 'stopListStr': stopListStr }

    return _render(request, 'search_results.html', args)


def create_source_text_from_form(form):
    """
    Create a new source text given a form
    """

    model_args = {
        'language': form.cleaned_data['language'],
        'author': form.cleaned_data['author'],
        'title': form.cleaned_data['title'],
        'enabled': form.cleaned_data['enabled']
    }

    for field in ('online_source_name', 'online_source_link', 'print_source_name', 'print_source_link'):
        if field in form.cleaned_data:
            model_args[field] = form.cleaned_data[field]

    return SourceText(**model_args)


def source_text_from_form(form):
    """
    Get the SourceText model given input data from a form.
    Creates a model if one can't be found in the database.
    """

    source_text = None
    for o in SourceText.objects.all().filter(language=form.cleaned_data['language'],
                                             author=form.cleaned_data['author'],
                                             title=form.cleaned_data['title']):
        source_text = o
        break

    if source_text is None:
        return create_source_text_from_form(form)

    source_text.enabled = form.cleaned_data['enabled']
    for field in ('online_source_name', 'online_source_link', 'print_source_name', 'print_source_link'):
        if field in form.cleaned_data:
            setattr(source_text, field, form.cleaned_data[field])

    return source_text


def volume_from_form(source_text, form, full_text):
    """
    Get the SourceTextVolume model given input data from a form.
    Creates a model if one can't be found in the database.
    """

    for vol in source_text.sourcetextvolume_set.filter(volume__iexact=form.cleaned_data['volume']):
        vol.text = full_text
        return vol

    return source_text.sourcetextvolume_set.create(
        volume=form.cleaned_data['volume'],
        text=full_text
    )


def _parse_tess_line(text_value):
    """
    Read and parse text from the user, must be in .tess format
    """

    if text_value is None:
        return (None, None)

    # Input files must be in .tess format
    lines = []
    rex = r'^<([^>]+)>[\t](.*)$'
    lrex = r'([0-9]+)(-([0-9]+))?$'

    for line in re.split(r'[\n\r]+', text_value):
        line = line.strip()
        if len(line) == 0:
            continue
        match = re.match(rex, line)
        if match is None:
            continue
        left = match.group(1)
        right = match.group(2)

        line_info = re.search(lrex, left)
        if line_info is not None:
            start = line_info.group(1)
            if line_info.group(3) is not None:
                end = line_info.group(3)
            else:
                end = start
        else:
            start = 0
            end = 0

        lines.append((right, start, end))

    full_text = '\n'.join([l[0] for l in lines])

    return (full_text, lines)


def _parse_tess_sentence(text_value):
    """
    Read and parse text from the user, must be in .tess format
    """

    if text_value is None:
        return (None, None)

    (full_text, lines) = _parse_tess_line(text_value)

    sentences = []
    phrase_delimiter = r'([.?!;:])'
    only_delimeter = re.compile(r'^[.?!;:]$')

    current_sentence = None
    for text, start, end in lines:
        text = text.strip()
        parts = re.split(phrase_delimiter, text)

        for part in parts:
            if only_delimeter.match(part) is not None:
                # This is a delimeter
                if current_sentence is None:
                    # Ignore it
                    pass
                else:
                    sentence = (current_sentence[0] + part).strip()
                    sent_start = current_sentence[1]
                    sent_end = end
                    if len(current_sentence[0].strip()) > 0:
                        sentences.append((sentence, sent_start, sent_end))
                    current_sentence = None
            else:
                # Not a delimeter
                if current_sentence is None:
                    current_sentence = (part, start, end)
                else:
                    current_sentence = (current_sentence[0] + ' ' + part, current_sentence[1], end)

    return (full_text, sentences)


def get_tess_mode():
    """
    Get a string describing how to parse .tess files (line or sentence)
    """

    mode = 'line'
    try:
        if settings.TESS_PARSE_MODE != None:
            if settings.TESS_PARSE_MODE != 'default':
                mode = settings.TESS_PARSE_MODE
    except AttributeError:
        # They didn't define this setting, no biggie
        pass

    if mode in ('line', 'lines'):
        return 'line'
    elif mode in ('sentence', 'sentences'):
        return 'sentence'
    else:
        logger.warn("Invalid tess mode: '" + str(mode) + "', falling back to 'line'")
        return 'line'


def parse_text(text_value):
    """
    Read and parse text from the user, must be in .tess format
    """

    if get_tess_mode() == 'sentence':
        return _parse_tess_sentence(text_value)
    else:
        return _parse_tess_line(text_value)


def parse_text_from_request(request):
    """
    Buffer a request into a string, and try to parse it.
    Should be in .tess format
    """

    text_value = None
    # Buffer the whole thing into memory
    if 'source_file' in request.FILES:
        io = StringIO.StringIO()
        for chunk in request.FILES['source_file'].chunks():
            io.write(chunk)
        text_value = io.getvalue()
        io.close()

    return parse_text(text_value)


def submit(request, form):
    """
    Ingest a piece of text into the database
    """

    args = {'user': request.user, 'form': form,
            'authenticated': request.user.is_authenticated()}

    (text, sentences) = parse_text_from_request(request)
    if None in (text, sentences):
        return _render(request, 'invalid.html', args)

    source_text = source_text_from_form(form)
    if source_text.is_dirty():
        source_text.save()

    volume = volume_from_form(source_text, form, text)
    if volume.is_dirty():
        volume.save()

    volume.sourcetextsentence_set.all().delete()
    with reversion.create_revision():
        reversion.set_user(request.user)
        for sent in sentences:
            (text, begin, end) = sent
            SourceTextSentence.objects.create(volume=volume, sentence=text, start_line=begin, end_line=end)

    return _render(request, 'submitted.html', args)
