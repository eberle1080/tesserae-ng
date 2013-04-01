from django.http import HttpResponse, Http404
from django.views.decorators.http import require_GET, require_POST
from views import _render

def search(request, language, level):
    """
    The core search entry point
    """

    if request.method == 'GET':
        if language in ('latin', 'greek', 'english'):
            if language == 'latin' and level == 'basic':
                return _render(request, 'basic_search.html', {'language': language})
            elif level == 'advanced':
                return _render(request, 'advanced_search.html', {'language': language})

    raise Http404()
