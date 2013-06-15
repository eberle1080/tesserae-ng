#!/usr/bin/env python2.7

import os, sys

sys.path.append("/home/tesserae")
os.environ["DJANGO_SETTINGS_MODULE"] = "website.settings"

# create a super user
from django.contrib.auth.models import User
u = User.objects.create(
    username='admin',
    first_name='Administrator',
    last_name='',
    email='admin@tesserae.org',
    is_superuser=True,
    is_staff=True,
    is_active=True
)

u.set_password('admin')
u.save()

print "Admin user account created"
