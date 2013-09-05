from django.core.management.base import BaseCommand, CommandError

class Command(BaseCommand):
    args = '<yaml_path>'
    help = 'Ingest text from a batch yaml file'

    def handle(self, *args, **options):
        pass
