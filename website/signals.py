import logging
import reversion
import celery_haystack
import celery_haystack.signals as chsignals
import celery_haystack.tasks as chtasks

from django.conf import settings
from django.db.models import signals as djsignals
from django.core.exceptions import ImproperlyConfigured
from django.utils.importlib import import_module

from haystack.signals import BaseSignalProcessor
from haystack.exceptions import NotHandled
from haystack.utils import get_identifier

from celery_haystack.indexes import CelerySearchIndex

import website.tasks
from website.tasks import TesseraeNGSignalHandler

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
        batch_jobs = {}
        if instances is not None:
            for inst in instances:
                if inst is not None:
                    cls = type(inst)
                    if not batch_jobs.has_key(cls):
                        batch_jobs[cls] = []
                    batch_jobs[cls].append(inst)

        if len(batch_jobs) > 0:
            for _, lst in batch_jobs.iteritems():
                self.enqueue('update', lst, sender, **kwargs)


    def _is_sequence(self, arg):
        return hasattr(arg, "__getitem__")


    def enqueue(self, action, batch_list, sender, **kwargs):
        """
        Given a list of model instances, determine if a backend
        handles the model, check if the index is Celery-enabled and
        enqueue task.
        """

        if not self._is_sequence(batch_list):
            batch_list = [ batch_list ]

        using_backends = self.connection_router.for_write(instance=batch_list[0])
        for using in using_backends:
            try:
                connection = self.connections[using]
                ui = connection.get_unified_index()
                ui.reset()
                ui.build()

                index = ui.get_index(batch_list[0].__class__)
            except NotHandled:
                continue  # Check next backend

            if isinstance(index, CelerySearchIndex):
                if action == 'update':
                    tmp_list = [inst for inst in batch_list if index.should_update(inst)]
                    batch_list = [inst for inst in batch_list if not inst in tmp_list]
                    if len(tmp_list) > 0:
                        self._enqueue_task(action, tmp_list)


    def _enqueue_task(self, action, instance_list):
        """
        Common utility for enqueing a task for the given action and
        model instance.
        """
        if not self._is_sequence(instance_list):
            instance_list = [ instance_list ]
        task = self._get_update_task()
        identifiers = [ get_identifier(inst) for inst in instance_list ]
        max_size = settings.SIGNAL_PROCESSOR_BATCH_SIZE
        current_index = 0

        while True:
            tmp_list = identifiers[current_index:current_index+max_size]
            sz = len(tmp_list)
            if sz == 0:
                break
            current_index += sz
            task.delay(action, tmp_list)

    def _get_update_task(self, task_path=None):
        import_path = task_path or settings.CELERY_HAYSTACK_DEFAULT_TASK
        module, attr = import_path.rsplit('.', 1)
        try:
            mod = import_module(module)
        except ImportError, e:
            raise ImproperlyConfigured('Error importing module %s: "%s"' %
                                       (module, e))
        try:
            task = getattr(mod, attr)
        except AttributeError:
            raise ImproperlyConfigured('Module "%s" does not define a "%s" '
                                       'class.' % (module, attr))
        return task
