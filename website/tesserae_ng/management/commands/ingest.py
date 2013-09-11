import os
import stat
import reversion

from optparse import make_option
from os.path import dirname, basename, abspath, exists, isfile, join, isabs
from django.core.management.base import BaseCommand, CommandError
from website.tesserae_ng.models import SourceText, SourceTextSentence
from website.tesserae_ng.core_search import parse_text, get_tess_mode


class Command(BaseCommand):

    args = '<yaml_path> ...'

    help = 'Ingest text from a batch yaml file'

    option_list = BaseCommand.option_list + (
        make_option('--is-auto',
            dest='is-auto',
            help='Is this an automatic ingest? Should only be toggled by the bootstrap scripts.',
            type='string',
            action='store'
        ),)


    def _print_ln(self, message=None):
        if message is None:
            self.stdout.write("\n")
        elif message.endswith("\n"):
            self.stdout.write(message)
        else:
            self.stdout.write(message + "\n")
        self.stdout.flush()


    def _parse_bool(self, string):
        if string is None:
            raise ValueError("string can't be None")
        if string in (True, False):
            return string
        s = string.strip().lower()
        if s in ('true', 't', 'yes', 'y', '1'):
            return True
        elif s in ('false', 'f', 'no', 'n', '0'):
            return False
        raise ValueError('Invalid boolean value: ' + string)


    def _parse_arguments(self, options):
        returned_options = {}
        is_auto = False

        if options.has_key('is-auto'):
            tmp = options['is-auto']
            if tmp is not None:
                is_auto = self._parse_bool(tmp)

        returned_options['is-auto'] = is_auto
        return returned_options


    def handle(self, *args, **options):
        if len(args) == 0:
            raise CommandError('At least one path is required')
        opts = self._parse_arguments(options)
        for arg in args:
            self._sanity_check_path(arg)
        for arg in args:
            self._process_file(arg, opts)


    def _process_file(self, path, opts):
        import yaml

        base_path = dirname(abspath(path))
        documents = None

        with open(path, 'r') as fh:
            documents = yaml.load_all(fh)
            self._print_ln('Loaded ' + str(path))

            for doc in documents:
                self._process_document(base_path, path, doc, opts)

            self._print_ln('Finished processing ' + str(path))


    def _get_required_field(self, doc, path, key):
        if not doc.has_key(key):
            raise RuntimeError('Missing required field in ' + str(path) + ': ' + str(key))
        value = doc[key]
        if value is None:
            raise RuntimeError('Missing required field in ' + str(path) + ': ' + str(key))
        return value


    def _get_optional_field(self, doc, key):
        if not doc.has_key(key):
            return None
        return doc[key]


    def _sanity_check_volume(self, vol, base_path, path):
        if not isinstance(vol, dict):
            raise RuntimeError('volume entries must be a dict (in file ' + str(path) + ')')
        if not vol.has_key('name'):
            raise RuntimeError('volume entry missing required field in ' + str(path) + ': name')
        if not vol.has_key('path'):
            raise RuntimeError('volume entry missing required field in ' + str(path) + ': path')

        name = vol['name']
        tess_path = vol['path']

        if not isabs(tess_path):
            tess_path = join(base_path, tess_path)

        if not exists(tess_path):
            raise RuntimeError("Path doesn't exist: " + str(tess_path))
        if not isfile(tess_path):
            raise RuntimeError("Path isn't a11 file: " + str(tess_path))
        if not self._can_read(tess_path):
            raise RuntimeError("Path isn't readable: " + str(tess_path))

        should_skip = False
        if vol.has_key('skip'):
            skip = vol['skip']
            if skip is not None:
                should_skip = self._parse_bool(skip)

        return {'name': name, 'path': tess_path, 'skip': should_skip}


    def _process_document(self, base_path, path, document, opts):
        """
        """

        auto_ingest = self._get_optional_field(document, 'auto_ingest')
        if auto_ingest is None:
            auto_ingest = True
        else:
            auto_ingest = self._parse_bool(auto_ingest)

        if opts['is-auto']:
            if not auto_ingest:
                self._print_ln(' -> Skipping ' + str(tess_path) + " because it's marked for manual ingest only")
                return

        lang = self._get_required_field(document, path, 'language')
        author = self._get_required_field(document, path, 'author')
        title = self._get_required_field(document, path, 'title')
        online_source_name = self._get_optional_field(document, 'online_source_name')
        online_source_link = self._get_optional_field(document, 'online_source_link')
        print_source_name = self._get_optional_field(document, 'print_source_name')
        print_source_link = self._get_optional_field(document, 'print_source_link')
        volumes = self._get_required_field(document, path, 'volumes')
        is_indexed = self._get_optional_field(document, 'index')

        if is_indexed is None:
            is_indexed = True
        else:
            is_indexed = self._parse_bool(is_indexed)

        if not isinstance(volumes, (list, tuple)):
            raise RuntimeError('volumes must be an array (in file ' + str(path) + ')')

        volumes = [ self._sanity_check_volume(vol, base_path, path) for vol in volumes ]
        if len(volumes) == 0:
            raise RuntimeError('you must have at least one volume defined (in file ' + str(path) + ')')

        source_text = self._source_text_from_data(lang, author, title, is_indexed,
                                                  online_source_name, online_source_link,
                                                  print_source_name, print_source_link)
        if source_text.is_dirty():
            source_text.save()

        self._print_ln(' -> Stored source text metadata')

        for volume_info in volumes:
            volume_name = volume_info['name']
            tess_path = volume_info['path']
            if volume_info['skip']:
                self._print_ln(' -> Skipping over ' + str(tess_path))
                continue

            with open(tess_path, 'r') as text_handle:
                (text, sentences) = parse_text(text_handle.read())

            if None in (text, sentences):
                raise RuntimeError('Invalid file (probably not .tess format): ' + str(tess_path))

            self._print_ln(' -> Parsed ' + str(tess_path))
            volume = self._volume_from_data(source_text, volume_name, text)
            if volume.is_dirty():
                volume.save()

            sent = ' ' + get_tess_mode()
            if len(sentences) != 1:
                sent += 's'

            self._print_ln(' -> Sending ' + str(len(sentences)) + sent + ' to the queue for indexing...')

            volume.sourcetextsentence_set.all().delete()
            with reversion.create_revision():
                #reversion.set_user(request.user)
                for sent in sentences:
                    (text, begin, end) = sent
                    SourceTextSentence.objects.create(volume=volume, sentence=text, start_line=begin, end_line=end)

            self._print_ln(' -> Ingested ' + str(tess_path))


    def _sanity_check_path(self, path):
        if not exists(path):
            raise CommandError("Path doesn't exist: " + str(path))
        if not isfile(path):
            raise CommandError("Path isn't a11 file: " + str(path))
        if not self._can_read(path):
            raise CommandError("Path isn't readable: " + str(path))


    def _can_read(self, path):
        uid = os.getuid()
        euid = os.geteuid()
        gid = os.getgid()
        egid = os.getegid()

        # This is probably true most of the time, so just let os.access()
        # handle it.  Avoids potential bugs in the rest of this function.
        if uid == euid and gid == egid:
            return os.access(path, os.R_OK)

        st = os.stat(path)

        # This may be wrong depending on the semantics of your OS.
        # i.e. if the file is -------r--, does the owner have access or not?
        if st.st_uid == euid:
            return st.st_mode & stat.S_IRUSR != 0

        # See comment for UID check above.
        groups = os.getgroups()
        if st.st_gid == egid or st.st_gid in groups:
            return st.st_mode & stat.S_IRGRP != 0

        return st.st_mode & stat.S_IROTH != 0


    def _filter_optional(self, options):
        """
        Remove any blank or None values from a dictionary
        """
        filtered = {}
        for k, v in options.iteritems():
            if v is None:
                continue
            if len(v.strip()) == 0:
                continue
            filtered[k] = v
        return filtered


    def _create_source_text_from_data(self, language, author, title, enabled, online_source_name,
                                      online_source_link, print_source_name, print_source_link):
        """
        Create a new source text given input data from a yaml file.
        """

        model_args = {
            'language': language,
            'author': author,
            'title': title,
            'enabled': enabled
        }

        optional_args = {
            'online_source_name': online_source_name,
            'online_source_link': online_source_link,
            'print_source_name': print_source_name,
            'print_source_link': print_source_link
        }

        for k, v in self._filter_optional(optional_args).iteritems():
            model_args[k] = v

        return SourceText(**model_args)


    def _source_text_from_data(self, language, author, title, enabled, online_source_name,
                               online_source_link, print_source_name, print_source_link):
        """
        Get the SourceText model given input data from a yaml file.
        Creates a model if one can't be found in the database.
        """

        source_text = None
        for o in SourceText.objects.all().filter(language=language, author=author, title=title):
            source_text = o
            break

        if source_text is None:
            return self._create_source_text_from_data(
                language,
                author,
                title,
                enabled,
                online_source_name,
                online_source_link,
                print_source_name,
                print_source_link)

        source_text.enabled = enabled

        optional_args = {
            'online_source_name': online_source_name,
            'online_source_link': online_source_link,
            'print_source_name': print_source_name,
            'print_source_link': print_source_link
        }

        for k, v in self._filter_optional(optional_args).iteritems():
            setattr(source_text, k, v)

        return source_text


    def _volume_from_data(self, source_text, volume_name, full_text):
        """
        Get the SourceTextVolume model given input data from a yaml file.
        Creates a model if one can't be found in the database.
        """

        for vol in source_text.sourcetextvolume_set.filter(volume__iexact=volume_name):
            vol.text = full_text
            return vol

        return source_text.sourcetextvolume_set.create(
            volume=volume_name,
            text=full_text
        )
