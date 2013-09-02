from haystack import connection_router
from django.conf import settings
import logging
import requests

# Get an instance of a logger
logger = logging.getLogger(__name__)

COMPARE_URL='{0}/compare'

def basic_search(source, target, language, start=0, rows=10):

    if language != 'latin':
        raise Exception('Only latin is supported for now. Sorry.')

    conn_alias = connection_router.for_read()
    if isinstance(conn_alias, (list, tuple)) and len(conn_alias):
        # We can only effectively read from one engine
        conn_alias = conn_alias[0]
    hs_info = settings.HAYSTACK_CONNECTIONS[conn_alias]
    solr_url = hs_info['URL']

    get_params = {
        'wt': 'python', # bitchin
        'tess.sq': 'volume_id:{0}'.format(source.id),
        'tess.sf': 'text',
        'tess.sfl': 'volume,author,text,title',
        'tess.tq': 'volume_id:{0}'.format(target.id),
        'tess.tf': 'text',
        'tess.tfl': 'volume,author,text,title',
        'start': str(start),
        'rows': str(rows)
    }

    response = requests.get(COMPARE_URL.format(solr_url), params=get_params)
    response.raise_for_status()

    # This couldn't possibly be abused... cough
    return eval(str(response.text))
