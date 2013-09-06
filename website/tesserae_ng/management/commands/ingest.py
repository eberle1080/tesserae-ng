import os
import stat
import reversion

from os.path import dirname, basename, abspath, exists, isfile, join, isabs
from django.core.management.base import BaseCommand, CommandError
from website.tesserae_ng.models import SourceText, SourceTextSentence
from website.tesserae_ng.core_search import parse_text


class Command(BaseCommand):

    args = '<yaml_path> ...'
    help = 'Ingest text from a batch yaml file'

    def handle(self, *args, **options):
        if len(args) == 0:
            raise CommandError('At least one path is required')
        for arg in args:
            self._sanity_check_path(arg)
        for arg in args:
            self._process_file(arg)


    def _process_file(self, path):
        import yaml

        base_path = dirname(abspath(path))
        documents = None

        with open(path, 'r') as fh:
            documents = yaml.load_all(fh)
            self.stdout.write('Successfully loaded ' + str(path) + "\n")

            for doc in documents:
                self._process_document(base_path, path, doc)

            self.stdout.write('Finished processing ' + str(path) + "\n")


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

        return {'name': name, 'path': tess_path}


    def _process_document(self, base_path, path, document):
        lang = self._get_required_field(document, path, 'language')
        author = self._get_required_field(document, path, 'author')
        title = self._get_required_field(document, path, 'title')
        online_source_name = self._get_optional_field(document, 'online_source_name')
        online_source_link = self._get_optional_field(document, 'online_source_link')
        print_source_name = self._get_optional_field(document, 'print_source_name')
        print_source_link = self._get_optional_field(document, 'print_source_link')
        volumes = self._get_required_field(document, path, 'volumes')

        if not isinstance(volumes, (list, tuple)):
            raise RuntimeError('volumes must be an array (in file ' + str(path) + ')')

        volumes = [ self._sanity_check_volume(vol, base_path, path) for vol in volumes ]
        if len(volumes) == 0:
            raise RuntimeError('you must have at least one volume defined (in file ' + str(path) + ')')

        source_text = self._source_text_from_data(lang, author, title, True,
                                                  online_source_name, online_source_link,
                                                  print_source_name, print_source_link)
        if source_text.is_dirty():
            source_text.save()

        self.stdout.write(' -> Successfully added / updated source text metadata' + "\n")

        for volume_info in volumes:
            volume_name = volume_info['name']
            tess_path = volume_info['path']
            with open(tess_path, 'r') as text_handle:
                (text, sentences) = parse_text(text_handle.read())

            if None in (text, sentences):
                raise RuntimeError('Invalid file (probably not .tess format): ' + str(tess_path))

            self.stdout.write(' -> Successfully parsed ' + str(tess_path) + "\n")
            volume = self._volume_from_data(source_text, volume_name, text)
            if volume.is_dirty():
                volume.save()

            volume.sourcetextsentence_set.all().delete()
            with reversion.create_revision():
                #reversion.set_user(request.user)
                for sent in sentences:
                    (text, begin, end) = sent
                    SourceTextSentence.objects.create(volume=volume, sentence=text, start_line=begin, end_line=end)

            self.stdout.write(' -> Ingested ' + str(tess_path) + "\n")


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

        for k, v in optional_args.iteritems():
            if v is not None:
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

        for k, v in optional_args.iteritems():
            if v is not None:
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
