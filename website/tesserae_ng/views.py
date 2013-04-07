from django.http import HttpResponse, Http404
from django.views.decorators.http import require_GET, require_POST

from website.tesserae_ng.forms import SourceTextSubmitForm

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
    args = {'user': request.user, 'authenticated': request.user.is_authenticated(), 'has_errors': False}
    return _render(request, 'index.html', args)

@require_POST
def login(request):
    """
    Log in to the page
    """
    from django.contrib.auth import login as django_login
    from django.contrib.auth import authenticate

    username = request.POST['login']
    password = request.POST['password']

    user = authenticate(username=username, password=password)
    if user is not None:
        if user.is_active:
            django_login(request, user)
            args = {'user': user, 'authenticated': True, 'has_errors': False}
            return _render(request, 'index.html', args)
        else:
            args = {'user': user, 'authenticated': True, 'has_errors': True,
                    'error_message': 'Account is disabled'}
            return _render(request, 'index.html', args)
    else:
        args = {'user': user, 'authenticated': True, 'has_errors': True,
                'error_message': 'Invalid username or password. Try again.'}
        return _render(request, 'index.html', args)

@require_GET
def logout(request):
    """
    Log out from the page
    """
    from django.contrib.auth import logout as django_logout
    django_logout(request)
    return index(request)

def search(request, language, level):
    """
    The core search entry point
    """

    import core_search
    return core_search.search(request, language, level)

@require_GET
def upload(request):
    args = {'user': request.user, 'authenticated': request.user.is_authenticated(),
            'form': SourceTextSubmitForm()}
    if request.user.is_authenticated():
        return _render(request, 'upload.html', args)
    else:
        return _render(request, 'unauthenticated.html', {})

@require_POST
def ingest(request):
    if request.user.is_authenticated():
        form = LinkSubmitForm(request.POST)
        if form.is_valid():
            pass
        else:
            pass
    else:
        return _render(request, 'unauthenticated.html', {})
