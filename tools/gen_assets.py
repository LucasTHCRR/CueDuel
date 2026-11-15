#!/usr/bin/env python3
"""Builds the generated game assets: ball textures, ball icons and sound effects."""
import math
import os
import struct
import wave

from PIL import Image, ImageDraw, ImageFont

ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "..", "assets")
FONT = os.path.join(ROOT, "fonts", "NotoSans-Black.ttf")
COLORS = {1: "#f0c800", 2: "#0046aa", 3: "#c81e1e", 4: "#5a1e82", 5: "#eb7800", 6: "#007832", 7: "#781e1e", 8: "#141414"}
WHITE = "#f2f2ec"


def ball_texture(n):
    s = 2
    w, h = 512 * s, 256 * s
    col = WHITE if n == 0 else COLORS[(n - 1) % 8 + 1]
    img = Image.new("RGB", (w, h), WHITE if n > 8 else col)
    d = ImageDraw.Draw(img)
    if n > 8:
        d.rectangle([0, 60 * s, w, 196 * s], fill=col)
    for cx in (128, 384):
        r = 44 if n > 0 else 10
        d.ellipse([(cx - r) * s, (128 - r) * s, (cx + r) * s, (128 + r) * s], fill=WHITE if n > 0 else "#cc3333")
        if n > 0:
            f = ImageFont.truetype(FONT, (52 if n < 10 else 44) * s)
            d.text((cx * s, 126 * s), str(n), font=f, fill="black", anchor="mm")
    return img.resize((256, 128), Image.LANCZOS)


def ball_icon(n):
    s = 4
    size = 96
    big = size * s
    col = WHITE if n == 0 else COLORS[(n - 1) % 8 + 1]
    img = Image.new("RGBA", (big, big), (0, 0, 0, 0))
    mask = Image.new("L", (big, big), 0)
    ImageDraw.Draw(mask).ellipse([2 * s, 2 * s, big - 2 * s, big - 2 * s], fill=255)
    body = Image.new("RGBA", (big, big), WHITE if n > 8 else col)
    d = ImageDraw.Draw(body)
    if n > 8:
        d.rectangle([0, int(big * 0.24), big, int(big * 0.76)], fill=col)
    if n > 0:
        r = int(big * 0.25)
        d.ellipse([big // 2 - r, big // 2 - r, big // 2 + r, big // 2 + r], fill=WHITE)
        f = ImageFont.truetype(FONT, int(big * (0.34 if n < 10 else 0.28)))
        d.text((big // 2, int(big * 0.49)), str(n), font=f, fill="black", anchor="mm")
    img.paste(body, (0, 0), mask)
    return img.resize((size, size), Image.LANCZOS)


RATE = 22050


class Synth:
    def __init__(self):
        self.seed = 12345
        self.phase = 0.0

    def noise(self):
        self.seed = (self.seed * 1103515245 + 12345) & 0x7FFFFFFF
        return self.seed / 0x3FFFFFFF - 1.0

    def sweep(self, t, f0, f1, d, kind):
        f = f0 * (f1 / f0) ** (t / d)
        self.phase += f / RATE
        env = 0.001 ** (t / d)
        x = self.phase % 1.0
        if kind == "tri":
            return (4.0 * abs(x - 0.5) - 1.0) * env
        if kind == "sq":
            return (1.0 if x < 0.5 else -1.0) * env
        return math.sin(2 * math.pi * x) * env

    def render(self, d, fn):
        self.phase = 0.0
        return [fn(i / RATE, d) for i in range(int(RATE * d))]

    def jingle(self, notes):
        step, dur = 0.17, 0.3
        n = int(RATE * (step * (len(notes) - 1) + dur))
        buf = [0.0] * n
        for k, note in enumerate(notes):
            self.phase = 0.0
            start = int(RATE * step * k)
            for i in range(int(RATE * dur)):
                if start + i < n:
                    buf[start + i] += self.sweep(i / RATE, note, note * 0.98, dur, "tri") * 0.6
        return buf


def write_wav(name, samples):
    with wave.open(os.path.join(ROOT, "sfx", name + ".wav"), "wb") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b"".join(struct.pack("<h", int(max(-1.0, min(1.0, v)) * 32000)) for v in samples))


def main():
    os.makedirs(os.path.join(ROOT, "balls"), exist_ok=True)
    os.makedirs(os.path.join(ROOT, "sfx"), exist_ok=True)
    for n in range(16):
        ball_texture(n).save(os.path.join(ROOT, "balls", "ball_%d.png" % n))
        ball_icon(n).save(os.path.join(ROOT, "balls", "icon_%d.png" % n))
    s = Synth()
    write_wav("ball", s.render(0.08, lambda t, d: s.sweep(t, 2600, 1300, d, "tri") * 0.7 + s.noise() * 0.25 * (1 - t / d)))
    write_wav("rail", s.render(0.12, lambda t, d: s.sweep(t, 190, 70, d, "sine")))
    write_wav("pot", s.render(0.4, lambda t, d: s.sweep(t, 420, 70, d, "sine") * 0.8 + s.noise() * 0.3 * (1 - t / d)))
    write_wav("cue", s.render(0.07, lambda t, d: s.sweep(t, 1600, 600, d, "sq") * 0.4 + s.noise() * 0.5 * (1 - t / d)))
    write_wav("win", s.jingle([523.0, 659.0, 784.0, 1047.0]))
    write_wav("lose", s.jingle([392.0, 330.0, 262.0, 196.0]))
    write_wav("click", s.render(0.03, lambda t, d: s.sweep(t, 900, 700, d, "sine") * 0.5))


if __name__ == "__main__":
    main()
