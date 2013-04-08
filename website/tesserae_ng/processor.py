from website.tesserae_ng.models import SourceTextSentence
from django.db import models
from haystack import signals

class SourceTextSentenceSignalProcessor(signals.BaseSignalProcessor):
    """
    Handles automatically indexing any data as it's saved or deleted from the main DB
    """

    def setup(self):
        models.signals.post_save.connect(self.handle_save, sender=SourceTextSentence)
        models.signals.post_delete.connect(self.handle_delete, sender=SourceTextSentence)

    def teardown(self):
        models.signals.post_save.disconnect(self.handle_save, sender=SourceTextSentence)
        models.signals.post_delete.disconnect(self.handle_delete, sender=SourceTextSentence)
