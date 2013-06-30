import logging
import reversion
import celery_haystack
import celery_haystack.signals as chsignals

from django.db.models import signals as djsignals

from haystack.signals import BaseSignalProcessor
from haystack.exceptions import NotHandled

from celery_haystack.utils import enqueue_task
from celery_haystack.indexes import CelerySearchIndex

# Get an instance of a logger
logger = logging.getLogger(__name__)

class TesseraeNGSignalProcessor(chsignals.CelerySignalProcessor):

    def setup(self):
        djsignals.post_delete.connect(self.enqueue_delete)
        reversion.post_revision_commit.connect(self.reversion_enqueue_save)

    def teardown(self):
        djsignals.post_delete.disconnect(self.enqueue_delete)
        reversion.post_revision_commit.disconnect(self.reversion_enqueue_save)

    def reversion_enqueue_save(self, sender, instances, revision, versions, **kwargs):
        if instances is not None:
            for inst in instances:
                self.enqueue('update', inst, sender, **kwargs)

    def enqueue(self, action, instance, sender, **kwargs):
        """
        Given an individual model instance, determine if a backend
        handles the model, check if the index is Celery-enabled and
        enqueue task.
        """

        using_backends = self.connection_router.for_write(instance=instance)

        for using in using_backends:
            try:
                connection = self.connections[using]
                ui = connection.get_unified_index()
                ui.reset()
                ui.build()

                index = ui.get_index(instance.__class__)
            except NotHandled:
                continue  # Check next backend

            if isinstance(index, CelerySearchIndex):
                if action == 'update' and not index.should_update(instance):
                    continue

                enqueue_task(action, instance)
                return  # Only enqueue instance once
