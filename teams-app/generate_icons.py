#!/usr/bin/env python3
"""Teams 앱 아이콘(color.png 192x192, outline.png 32x32)을 표준 라이브러리만으로 생성.

외부 의존성 없음(zlib만 사용). 색/모양을 바꾸려면 ACCENT 와 draw 로직을 수정 후 재실행:
    python3 teams-app/generate_icons.py
실제 로고가 있으면 이 PNG 두 개를 같은 파일명으로 교체하면 된다.
"""
import os
import struct
import zlib

ACCENT = (91, 95, 199)        # #5B5FC7 (manifest accentColor 와 동일)
WHITE = (255, 255, 255, 255)
HERE = os.path.dirname(os.path.abspath(__file__))


def _png(width, height, rgba):
    def chunk(typ, data):
        body = typ + data
        return struct.pack(">I", len(data)) + body + struct.pack(">I", zlib.crc32(body) & 0xffffffff)

    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)  # 8-bit RGBA
    raw = bytearray()
    for y in range(height):
        raw.append(0)  # filter: none
        raw += rgba[y * width * 4:(y + 1) * width * 4]
    return (b"\x89PNG\r\n\x1a\n"
            + chunk(b"IHDR", ihdr)
            + chunk(b"IDAT", zlib.compress(bytes(raw), 9))
            + chunk(b"IEND", b""))


def _check_grid(size, bg):
    """배경 bg(RGBA) 위에 흰색 체크마크를 그린 size×size 그리드를 반환."""
    grid = [[bg for _ in range(size)] for _ in range(size)]
    thick = max(1, round(size * 0.045))

    def line(x0, y0, x1, y1):
        steps = max(abs(x1 - x0), abs(y1 - y0), 1)
        for i in range(steps + 1):
            t = i / steps
            cx, cy = round(x0 + (x1 - x0) * t), round(y0 + (y1 - y0) * t)
            for dx in range(-thick, thick + 1):
                for dy in range(-thick, thick + 1):
                    x, y = cx + dx, cy + dy
                    if 0 <= x < size and 0 <= y < size:
                        grid[y][x] = WHITE

    line(round(size * 0.28), round(size * 0.52), round(size * 0.44), round(size * 0.70))
    line(round(size * 0.44), round(size * 0.70), round(size * 0.74), round(size * 0.32))
    return grid


def _write(name, size, bg):
    grid = _check_grid(size, bg)
    rgba = bytearray()
    for row in grid:
        for px in row:
            rgba += bytes(px if len(px) == 4 else (px[0], px[1], px[2], 255))
    with open(os.path.join(HERE, name), "wb") as f:
        f.write(_png(size, size, bytes(rgba)))
    print("wrote", name, f"({size}x{size})")


if __name__ == "__main__":
    # color: accent 배경 + 흰색 체크
    _write("color.png", 192, (ACCENT[0], ACCENT[1], ACCENT[2], 255))
    # outline: 투명 배경 + 흰색 체크 (Teams 가 테마색을 입힌다)
    _write("outline.png", 32, (255, 255, 255, 0))
