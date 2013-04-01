from django.http import HttpResponse, Http404
from django.views.decorators.http import require_GET, require_POST

def _render(request, *args, **kwargs):
    """
    Returns a HttpResponse whose content is filled with the result of calling
    django.template.loader.render_to_string() with the passed arguments.
    Uses a RequestContext by default.
    """
    from django.template import loader, RequestContext
    httpresponse_kwargs = {
        'content_type': kwargs.pop('content_type', None),
        'status': kwargs.pop('status', None),
    }

    if 'context_instance' in kwargs:
        context_instance = kwargs.pop('context_instance')
        if kwargs.get('current_app', None):
            raise ValueError('If you provide a context_instance you must '
                             'set its current_app before calling render()')
    else:
        current_app = kwargs.pop('current_app', None)
        context_instance = RequestContext(request, current_app=current_app)

    kwargs['context_instance'] = context_instance

    return HttpResponse(loader.render_to_string(*args, **kwargs),
                        **httpresponse_kwargs)

@require_GET
def index(request):
    """
    View for main page
    """
    return _render(request, 'index.html', {})

def search(request, language, level):
    """
    The core search entry point
    """

    import core_search
    return core_search.search(request, language, level)
