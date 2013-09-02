import decimal
from django import template

register = template.Library()

_ROUNDING_MAP = {
    'ru': decimal.ROUND_UP,
    'rf': decimal.ROUND_FLOOR,
    'rd': decimal.ROUND_DOWN,
    'rhd': decimal.ROUND_HALF_DOWN,
    'rhe': decimal.ROUND_HALF_EVEN,
    'rhu': decimal.ROUND_HALF_UP,
    'r05u': decimal.ROUND_05UP,
    decimal.ROUND_UP: decimal.ROUND_UP,
    decimal.ROUND_HALF_UP: decimal.ROUND_HALF_UP,
    decimal.ROUND_HALF_EVEN: decimal.ROUND_HALF_EVEN,
    decimal.ROUND_CEILING: decimal.ROUND_CEILING,
    decimal.ROUND_FLOOR: decimal.ROUND_FLOOR,
    decimal.ROUND_UP: decimal.ROUND_UP,
    decimal.ROUND_HALF_DOWN: decimal.ROUND_HALF_DOWN,
    decimal.ROUND_05UP: decimal.ROUND_05UP
}

@register.filter
def quantize(value,arg=None):
    """
    Takes a float number (23.456) and uses the
    decimal.quantize to round it to a fixed
    exponent. This allows you to specify the
    exponent precision, along with the
    rounding method.

    Examples (assuming value="7.325"):
    {% value|quantize %} -> 7.33
    {% value|quantize:".01,ru" %} -> 7.33 (this is the same as the default behavior)
    {% value|quantize:".01,rd" %} -> 7.32
    
    Available rounding options (taken from the decimal module):
    ROUND_CEILING (rc), ROUND_DOWN (rd), ROUND_FLOOR (rf), ROUND_HALF_DOWN (rhd),
    ROUND_HALF_EVEN (rhe), ROUND_HALF_UP (rhu), and ROUND_UP (ru)
    
    Arguments cannot have spaces in them.
    
    See the decimal module for more info:
    http://docs.python.org/library/decimal.html
    """

    num = decimal.Decimal(value)
    precision = '.01'
    rounding = decimal.ROUND_UP

    if arg:
        args = arg.split(',')
        precision = args[0]
        if len(args) > 1:
            rounding = args[1]

    if rounding not in _ROUNDING_MAP:
        raise ValueError('Invalid rounding value: ' + rounding)

    return num.quantize(decimal.Decimal(precision), rounding=_ROUNDING_MAP[rounding])
