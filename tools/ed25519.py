"""Ed25519 (RFC 8032) на чистом Python: сборка подписывает новые версии скрипта для iPhone.

Медленно по меркам криптобиблиотек (десятки миллисекунд на подпись), зато без зависимостей: сайт собирается
на GitHub Actions из одного python. Код повторяет эталонную реализацию из RFC 8032, раздел 6.
tools/test_site.py сверяет подписи с libsodium (PyNaCl), если она установлена, а виджет проверяет их
своей реализацией (ios/Uroki7B.js, edVerify) в автотестах."""
import hashlib

p = 2 ** 255 - 19
q = 2 ** 252 + 27742317777372353535851937790883648493  # порядок базовой точки (L)


def _inv(x):
    return pow(x, p - 2, p)


d = -121665 * _inv(121666) % p
_SQRT_M1 = pow(2, (p - 1) // 4, p)


def _add(P, Q):
    A, B = (P[1] - P[0]) * (Q[1] - Q[0]) % p, (P[1] + P[0]) * (Q[1] + Q[0]) % p
    C, D = 2 * P[3] * Q[3] * d % p, 2 * P[2] * Q[2] % p
    E, F, G, H = B - A, D - C, D + C, B + A
    return (E * F % p, G * H % p, F * G % p, E * H % p)


def _mul(s, P):
    Q = (0, 1, 1, 0)
    while s > 0:
        if s & 1:
            Q = _add(Q, P)
        P = _add(P, P)
        s >>= 1
    return Q


def _equal(P, Q):
    return (P[0] * Q[2] - Q[0] * P[2]) % p == 0 and (P[1] * Q[2] - Q[1] * P[2]) % p == 0


def _recover_x(y, sign):
    if y >= p:
        return None
    x2 = (y * y - 1) * _inv(d * y * y + 1)
    if x2 % p == 0:
        return None if sign else 0
    x = pow(x2, (p + 3) // 8, p)
    if (x * x - x2) % p != 0:
        x = x * _SQRT_M1 % p
    if (x * x - x2) % p != 0:
        return None
    if (x & 1) != sign:
        x = p - x
    return x


_GY = 4 * _inv(5) % p
_GX = _recover_x(_GY, 0)
G = (_GX, _GY, 1, _GX * _GY % p)


def _compress(P):
    zi = _inv(P[2])
    x, y = P[0] * zi % p, P[1] * zi % p
    return int.to_bytes(y | ((x & 1) << 255), 32, "little")


def _decompress(s):
    if len(s) != 32:
        return None
    y = int.from_bytes(s, "little")
    sign = y >> 255
    y &= (1 << 255) - 1
    x = _recover_x(y, sign)
    return None if x is None else (x, y, 1, x * y % p)


def _hq(*parts):
    return int.from_bytes(hashlib.sha512(b"".join(parts)).digest(), "little") % q


def _expand(seed):
    if len(seed) != 32:
        raise ValueError("ключ Ed25519: нужно ровно 32 байта")
    h = hashlib.sha512(seed).digest()
    a = int.from_bytes(h[:32], "little")
    a &= (1 << 254) - 8
    a |= 1 << 254
    return a, h[32:]


def public_key(seed):
    """Открытый ключ (32 байта) по закрытому (32 байта)."""
    return _compress(_mul(_expand(seed)[0], G))


def sign(seed, msg):
    """Подпись (64 байта) сообщения msg (bytes)."""
    a, prefix = _expand(seed)
    A = _compress(_mul(a, G))
    r = _hq(prefix, msg)
    R = _compress(_mul(r, G))
    s = (r + _hq(R, A, msg) * a) % q
    return R + int.to_bytes(s, 32, "little")


def verify(pub, msg, sig):
    """Подпись sig сообщения msg сделана закрытым ключом к pub."""
    if len(pub) != 32 or len(sig) != 64:
        return False
    A, R = _decompress(pub), _decompress(sig[:32])
    if not A or not R:
        return False
    s = int.from_bytes(sig[32:], "little")
    if s >= q:
        return False
    return _equal(_mul(s, G), _add(R, _mul(_hq(sig[:32], pub, msg), A)))
