import logging
import celery_haystack
import celery_haystack.tasks as chtasks

from django.conf import settings

# Get an instance of a logger
logger = logging.getLogger(__name__)

class TesseraeNGSignalHandler(chtasks.CeleryHaystackSignalHandler):

    def _get_instances(self, model_class, keys, **kwargs):
         return model_class._default_manager.filter(pk__in=keys)

    def run(self, action, identifiers, **kwargs):
        # First get the object path and pk (e.g. ('notes.note', 23))
        identifier_batches = {}
        for identifier in identifiers:
            object_path, pk = self.split_identifier(identifier, **kwargs)
            if object_path is None or pk is None:
                msg = "Couldn't handle object with identifier %s" % identifier
                logger.error(msg)
                raise ValueError(msg)
            model_class = self.get_model_class(object_path, **kwargs)
            for current_index in self.get_indexes(model_class, **kwargs):
                if not identifier_batches.has_key(current_index):
                    identifier_batches[current_index] = {}
                if not identifier_batches[current_index].has_key(model_class):
                    identifier_batches[current_index][model_class] = []

                identifier_batches[current_index][model_class].append((identifier, object_path, pk))

        for current_index in identifier_batches.iterkeys():
            current_index_name = ".".join([current_index.__class__.__module__,
                                           current_index.__class__.__name__])
            batch_list = identifier_batches[current_index]

            if action == 'delete':
                deleted = 0
                try:
                    handler_options = self.get_handler_options(**kwargs)
                    for model_class in batch_list.iterkeys():
                        for job in batch_list[model_class]:
                            current_index.remove_object(job[0], **handler_options)
                            deleted += 1
                except Exception, exc:
                    logger.exception(exc)
                    self.retry(exc=exc)
                else:
                    num_d = str(deleted) + ' objects'
                    if deleted == 1:
                        num_d = str(deleted) + ' object'
                    msg = "Deleted %s (with %s)" % (num_d, current_index_name)
                    logger.debug(msg)
                    return msg

            elif action == 'update':
                # and the instance of the model class with the pk
                #instances = self._get_instances(model_class)
                for model_class in batch_list.iterkeys():
                    keys = [ j[2] for j in batch_list[model_class] ]
                    instances = self._get_instances(model_class, keys, **kwargs)
                    instances = [ inst for inst in instances if current_index.should_update(inst, **kwargs) ]
                    if len(instances) == 0:
                        continue

                    # Call the appropriate handler of the current index and
                    # handle exception if neccessary
                    try:
                        handler_options = self.get_handler_options(**kwargs)
                        backend = current_index._get_backend(using=kwargs.get('using'))
                        if backend is not None:
                            backend.update(current_index, instances)
                    except Exception, exc:
                        logger.exception(exc)
                        self.retry(exc=exc)
                    else:
                        updated = len(instances)
                        num_u = str(updated) + ' objects'
                        if updated == 1:
                            num_u = str(updated) + ' object'
                        msg = "Updated %s (with %s)" % (num_u, current_index_name)
                        logger.debug(msg)
                        return msg

            else:
                logger.error("Unrecognized action '%s'. Moving on..." % action)
                raise ValueError("Unrecognized action %s" % action)
