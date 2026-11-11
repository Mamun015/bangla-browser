#!/usr/bin/env python3
"""Generate the launcher icon PNGs (pure stdlib). Render once large, then downscale."""
import math, os, struct, zlib

TEAL = (0, 196, 140)        # bright top-left
GREEN = (0, 106, 78)        # brand green
GREEN_DARK = (0, 58, 42)    # deep bottom-right
WHITE = (255, 255, 255)
RED = (244, 42, 65)
HIGH = 768


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def render_high(n=HIGH):
    px = [[(0, 0, 0, 0)] * n for _ in range(n)]
    r = n * 0.22
    cx = cy = n / 2.0
    corner = n - r

    for y in range(n):
        dy = max(r - y, y - corner, 0)
        row = px[y]
        for x in range(n):
            dx = max(r - x, x - corner, 0)
            if dx * dx + dy * dy > r * r:
                continue
            # diagonal gradient: teal top-left -> green -> deep green bottom-right
            t = ((x / n) * 0.55 + (y / n) * 0.45)
            if t < 0.5:
                col = lerp(TEAL, GREEN, t / 0.5)
            else:
                col = lerp(GREEN, GREEN_DARK, (t - 0.5) / 0.5)
            # soft light from the top-left corner
            gl = max(0.0, 1.0 - math.hypot(x, y) / (n * 1.15))
            col = tuple(min(255, int(c + 38 * gl)) for c in col)
            row[x] = col + (255,)

    R = n * 0.345
    w = n * 0.026          # stroke half-width
    a_mer = R * 0.46
    b_par = R * 0.60

    def ell_dist(dx, dy, a, b):
        f2 = (dx / a) ** 2 + (dy / b) ** 2
        if f2 <= 0:
            return 1e9
        g = math.sqrt((dx / (a * a)) ** 2 + (dy / (b * b)) ** 2)
        if g <= 0:
            return 1e9
        return abs(math.sqrt(f2) - 1) / g

    for y in range(n):
        dy = y - cy
        row = px[y]
        for x in range(n):
            dx = x - cx
            d = math.sqrt(dx * dx + dy * dy)
            hit = False
            if abs(d - R) <= w:
                hit = True                                        # outer rim
            elif ell_dist(dx, dy, a_mer, R) <= w:
                hit = True                                        # meridian
            elif abs(dy) <= w and abs(dx) <= R:                   # equator
                hit = True
            if hit:
                row[x] = WHITE + (255,)

    # red dot inside the globe, upper right
    dotr = n * 0.082
    ox, oy = cx + R * 0.42, cy - R * 0.42
    for y in range(n):
        dy = y - oy
        if abs(dy) > dotr:
            continue
        row = px[y]
        half = math.sqrt(dotr * dotr - dy * dy)
        for x in range(int(ox - half), int(ox + half) + 1):
            if 0 <= x < n:
                row[x] = RED + (255,)

    return px


def downscale(src, size):
    n = len(src)
    scale = n / float(size)
    out = []
    for y in range(size):
        y0 = int(y * scale)
        y1 = max(y0 + 1, int((y + 1) * scale))
        row = []
        for x in range(size):
            x0 = int(x * scale)
            x1 = max(x0 + 1, int((x + 1) * scale))
            rr = gg = bb = aa = 0
            cnt = 0
            for sy in range(y0, y1):
                srow = src[sy]
                for sx in range(x0, x1):
                    p = srow[sx]
                    rr += p[0] * p[3]
                    gg += p[1] * p[3]
                    bb += p[2] * p[3]
                    aa += p[3]
                    cnt += 1
            if aa == 0:
                row.append((0, 0, 0, 0))
            else:
                row.append((rr // aa, gg // aa, bb // aa, aa // cnt))
        out.append(row)
    return out


def write_png(path, img):
    h, w = len(img), len(img[0])
    raw = b"".join(b"\x00" + b"".join(struct.pack("BBBB", *p) for p in row) for row in img)

    def chunk(tag, data):
        return (struct.pack(">I", len(data)) + tag + data
                + struct.pack(">I", zlib.crc32(tag + data) & 0xFFFFFFFF))

    png = b"\x89PNG\r\n\x1a\n"
    png += chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
    png += chunk(b"IDAT", zlib.compress(raw, 9))
    png += chunk(b"IEND", b"")
    open(path, "wb").write(png)


BASE = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "res")
DENS = {"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192}

big = render_high(HIGH)
for name, size in DENS.items():
    d = os.path.join(BASE, "mipmap-" + name)
    os.makedirs(d, exist_ok=True)
    write_png(os.path.join(d, "ic_launcher.png"), downscale(big, size))
    print("wrote", name, size)
