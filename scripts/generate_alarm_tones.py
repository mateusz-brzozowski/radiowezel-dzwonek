#!/usr/bin/env python3
"""Generate distinguishable school alarm tones as WAV, then convert to MP3."""
from __future__ import annotations

import math
import struct
import subprocess
import sys
import wave
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "alarms" / "tones"
SAMPLE_RATE = 44100


def write_wav(path: Path, samples: list[float]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    with wave.open(str(path), "w") as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(SAMPLE_RATE)
        frames = bytearray()
        for s in samples:
            v = max(-1.0, min(1.0, s))
            frames += struct.pack("<h", int(v * 32767))
        w.writeframes(frames)


def sine(freq: float, t: float, amp: float = 0.55) -> float:
    return amp * math.sin(2 * math.pi * freq * t)


def tone_modulated(duration: float = 12.0) -> list[float]:
    """MSWiA-like rising/falling siren."""
    out: list[float] = []
    for i in range(int(SAMPLE_RATE * duration)):
        t = i / SAMPLE_RATE
        # 0.5 Hz sweep between 600–1200 Hz
        sweep = 0.5 + 0.5 * math.sin(2 * math.pi * 0.45 * t)
        freq = 600 + 600 * sweep
        out.append(sine(freq, t, 0.5))
    return out


def tone_continuous(duration: float = 12.0, freq: float = 880.0) -> list[float]:
    out: list[float] = []
    for i in range(int(SAMPLE_RATE * duration)):
        t = i / SAMPLE_RATE
        # soft edges
        env = 1.0
        if t < 0.05:
            env = t / 0.05
        elif t > duration - 0.05:
            env = (duration - t) / 0.05
        out.append(sine(freq, t, 0.45 * env))
    return out


def tone_evacuate(duration: float = 12.0) -> list[float]:
    """ISO 8201-like: 0.5s on / 0.5s off pulses."""
    out: list[float] = []
    period = 1.0
    on = 0.5
    for i in range(int(SAMPLE_RATE * duration)):
        t = i / SAMPLE_RATE
        phase = t % period
        if phase < on:
            out.append(sine(800, t, 0.55))
        else:
            out.append(0.0)
    return out


def tone_lockdown(duration: float = 10.0) -> list[float]:
    """Short dense bursts: 0.12s on / 0.08s off."""
    out: list[float] = []
    period = 0.2
    on = 0.12
    for i in range(int(SAMPLE_RATE * duration)):
        t = i / SAMPLE_RATE
        phase = t % period
        if phase < on:
            out.append(sine(1000, t, 0.6) + sine(1400, t, 0.2))
        else:
            out.append(0.0)
    return out


def tone_shelter(duration: float = 12.0) -> list[float]:
    """Longer, calmer continuous with slow pulse."""
    out: list[float] = []
    for i in range(int(SAMPLE_RATE * duration)):
        t = i / SAMPLE_RATE
        pulse = 0.75 + 0.25 * math.sin(2 * math.pi * 0.25 * t)
        out.append(sine(520, t, 0.4 * pulse))
    return out


def tone_bomb_chime(duration: float = 3.0) -> list[float]:
    """Short attention chime — not an aggressive siren."""
    out: list[float] = []
    notes = [(660, 0.0, 0.7), (880, 0.85, 0.7), (660, 1.7, 0.8)]
    for i in range(int(SAMPLE_RATE * duration)):
        t = i / SAMPLE_RATE
        s = 0.0
        for freq, start, length in notes:
            if start <= t < start + length:
                local = t - start
                env = min(1.0, local / 0.02) * max(0.0, 1.0 - local / length)
                s += sine(freq, t, 0.5 * env)
        out.append(s)
    return out


def to_mp3(wav: Path, mp3: Path) -> None:
    ffmpeg = "ffmpeg"
    # Refresh PATH for newly installed ffmpeg
    cmd = [
        ffmpeg, "-y", "-i", str(wav),
        "-codec:a", "libmp3lame", "-b:a", "160k", str(mp3)
    ]
    subprocess.run(cmd, check=True, capture_output=True)


def main() -> int:
    OUT.mkdir(parents=True, exist_ok=True)
    jobs = {
        "tone_modulated_announce": tone_modulated(12),
        "tone_continuous_clear": tone_continuous(12, 880),
        "tone_evacuate": tone_evacuate(12),
        "tone_lockdown": tone_lockdown(10),
        "tone_shelter": tone_shelter(12),
        "tone_bomb_chime": tone_bomb_chime(3),
    }
    for name, samples in jobs.items():
        wav = OUT / f"{name}.wav"
        mp3 = OUT / f"{name}.mp3"
        write_wav(wav, samples)
        to_mp3(wav, mp3)
        wav.unlink(missing_ok=True)
        print(f"OK {mp3.name}")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except FileNotFoundError:
        # retry with common winget shim path
        print("ffmpeg not on PATH — retrying after PATH refresh", file=sys.stderr)
        raise
