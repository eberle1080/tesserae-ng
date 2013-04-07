import StringIO
import re

from django.http import HttpResponse, Http404
from django.views.decorators.http import require_GET, require_POST
from views import _render
from website.tesserae_ng.models import SourceText, SourceTextSentence

def search(request, language, level):
    """
    The core search entry point
    """

    args = {'language': language, 'user': request.user,
            'authenticated': request.user.is_authenticated()}

    if request.method == 'GET':
        if language in ('latin', 'greek', 'english'):
            if language == 'latin' and level == 'basic':
                return _render(request, 'basic_search.html', args)
            elif level == 'advanced':
                return _render(request, 'advanced_search.html', args)

    raise Http404()

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

def parse_text(request):
    """
    Read and parse text from the user, must be in .tess format
    """

    text_value = None
    # Buffer the whole thing into memory
    if 'source_file' in request.FILES:
        io = StringIO.StringIO()
        for chunk in request.FILES['source_file'].chunks():
            io.write(chunk)
        text_value = io.getvalue()
        io.close()

    if text_value is None:
        return (None, None)

    # Input files must be in .tess format
    rex = r'^<([^>]+)>[\t](.*)$'
    lrex = r'([0-9]+)(-([0-9]+))?$'

    lines = []

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
        start = int(line_info.group(1))
        if line_info.group(3) is not None:
            end = int(line_info.group(3))
        else:
            end = start

        lines.append((right, start, end))

    text_value = '\n'.join([l[0] for l in lines])

    return (text_value, lines)

def submit(request, form):
    """
    Ingest a piece of text into the database
    """

    args = {'user': request.user, 'form': form,
            'authenticated': request.user.is_authenticated()}

    (text, sentences) = parse_text(request)
    if None in (text, sentences):
        return _render(request, 'invalid.html', args)

    source_text = source_text_from_form(form)
    if source_text.is_dirty():
        source_text.save()

    volume = volume_from_form(source_text, form, text)
    if volume.is_dirty():
        volume.save()

    volume.sourcetextsentence_set.all().delete()
    bulk = []
    for sent in sentences:
        (line, begin, end) = sent
        bulk.append(SourceTextSentence(volume=volume, sentence=line, start_line_num=begin, end_line_num=end))
    SourceTextSentence.objects.bulk_create(bulk)

    return _render(request, 'submitted.html', args)
