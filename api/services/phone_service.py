import re

# South African mobile numbers, 9 digits after the country code.
_LOCAL_DIGITS = 9


def format_sa_number(raw: str) -> str:
    # Returns +27XXXXXXXXX for 0XXXXXXXXX, 27XXXXXXXXX, 0027XXXXXXXXX, +27 (0)XX XXX XXXX and the like.
    # Anything that cannot be a South African number is returned as it was, so validation can reject it.
    if not isinstance(raw, str):
        return raw
    digits = re.sub(r"\D", "", raw.replace("(0)", ""))
    if digits.startswith("0027"):
        digits = digits[2:]
    if digits.startswith("27"):
        local = digits[2:]
    elif digits.startswith("0"):
        local = digits[1:]
    else:
        return raw
    # A stray trunk zero after the country code, as in +27 0XX XXX XXXX.
    if len(local) == _LOCAL_DIGITS + 1 and local.startswith("0"):
        local = local[1:]
    if len(local) != _LOCAL_DIGITS:
        return raw
    return f"+27{local}"


def gateway_number(raw: str) -> str:
    # The email to SMS gateway takes the number without the plus, as 27XXXXXXXXX.
    formatted = format_sa_number(raw)
    if not re.fullmatch(r"\+27[0-9]{9}", formatted):
        raise ValueError("phone must be a South African mobile number")
    return formatted.removeprefix("+")
