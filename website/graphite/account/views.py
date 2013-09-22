"""Copyright 2008 Orbitz WorldWide

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License."""

from django.shortcuts import render_to_response
from django.http import HttpResponseRedirect
from django.contrib.auth import authenticate, login, logout
from website.graphite.util import getProfile
from website.graphite.logger import log
from website.graphite.account.models import Profile
from django.views.decorators.csrf import csrf_exempt

@csrf_exempt
def loginView(request):
  username = request.POST.get('username')
  password = request.POST.get('password')
  if request.method == 'GET':
    nextPage = request.GET.get('nextPage','/graphite')
  else:
    nextPage = request.POST.get('nextPage','/graphite')
  if username and password:
    user = authenticate(username=username,password=password)
    if user is None:
      return render_to_response("graphite/login.html",{'authenticationFailed' : True, 'nextPage' : nextPage})
    elif not user.is_active:
      return render_to_response("graphite/login.html",{'accountDisabled' : True, 'nextPage' : nextPage})
    else:
      login(request,user)
      return HttpResponseRedirect(nextPage)
  else:
    return render_to_response("graphite/login.html",{'nextPage' : nextPage})

def logoutView(request):
  nextPage = request.GET.get('nextPage','/graphite')
  logout(request)
  return HttpResponseRedirect(nextPage)

def editProfile(request):
  if not request.user.is_authenticated():
    return HttpResponseRedirect('../..')
  context = { 'profile' : getProfile(request) }
  return render_to_response("graphite/editProfile.html",context)

@csrf_exempt
def updateProfile(request):
  profile = getProfile(request,allowDefault=False)
  if profile:
    profile.advancedUI = request.POST.get('advancedUI','off') == 'on'
    profile.save()
  nextPage = request.POST.get('nextPage','/graphite')
  return HttpResponseRedirect(nextPage)
