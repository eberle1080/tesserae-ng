from django import forms

class SourceTextSubmitForm(forms.Form):

    LANGUAGE_CHOICES = (
        ('latin', 'Latin'),
        ('greek', 'Greek'),
        ('english', 'English')
    )

    enabled = forms.BooleanField(label='Indexed', required=True, initial=True)
    language = forms.ChoiceField(label='Text language', choices=LANGUAGE_CHOICES, required=True)
    author = forms.CharField(label='Work author', max_length=255, required=True)
    title = forms.CharField(label='Work name', max_length=255, required=True)
    volume = forms.CharField(label='Volume name', max_length=255, required=False)

    online_source_name = forms.CharField(label='Online source name', max_length=255, required=False)
    online_source_link = forms.URLField(label='Online source URL', required=False)
    print_source_name = forms.CharField(label='Print source name', max_length=255, required=False)
    print_source_link = forms.URLField(label='Print source URL', required=False)

    source_file = forms.FileField(allow_empty_file=False, required=True, label='Source file')
