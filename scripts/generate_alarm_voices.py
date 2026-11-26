#!/usr/bin/env python3
"""Generate Polish alarm voice messages with Edge TTS."""
from __future__ import annotations

import asyncio
from pathlib import Path

import edge_tts

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / "alarms" / "voices"
VOICE = "pl-PL-ZofiaNeural"
VOICE_CLEAR = "pl-PL-MarekNeural"

MESSAGES = {
    "voice_evac_fire": (
        VOICE,
        "Uwaga! Uwaga! Pożar! Proszę natychmiast opuścić budynek najbliższym wyjściem ewakuacyjnym. "
        "Zachowajcie spokój. To nie jest ćwiczenie.",
    ),
    "voice_evac_drill": (
        VOICE,
        "Uwaga! Uwaga! To jest ćwiczenie ewakuacyjne. Proszę opuścić budynek najbliższym wyjściem "
        "ewakuacyjnym. Zachowajcie spokój.",
    ),
    "voice_bomb": (
        VOICE,
        "Uwaga! Alarm bombowy. Proszę spokojnie opuścić budynek zgodnie z poleceniami nauczycieli. "
        "Nie używajcie wind. Nie zabierajcie zbędnych rzeczy.",
    ),
    "voice_lockdown": (
        VOICE,
        "Uwaga! Blokada szkoły. Natychmiast zamknijcie drzwi, zgaście światła, schowajcie się "
        "i zachowajcie absolutną ciszę. Czekajcie na dalsze komunikaty.",
    ),
    "voice_shelter": (
        VOICE,
        "Uwaga! Pozostańcie w budynku. Zamknijcie okna i drzwi. Nie wychodźcie na zewnątrz. "
        "Czekajcie na dalsze komunikaty.",
    ),
    "voice_air_raid": (
        VOICE,
        "Uwaga! Uwaga! Uwaga! Ogłaszam alarm powietrzny. Udajcie się do wyznaczonych pomieszczeń "
        "zgodnie z procedurą szkoły.",
    ),
    "voice_all_clear": (
        VOICE_CLEAR,
        "Uwaga! Uwaga! Uwaga! Odwołuję alarm. Można wrócić do normalnego trybu pracy i zajęć.",
    ),
    "voice_test_short": (
        VOICE,
        "Uwaga! Test systemu radiowęzła. To tylko test.",
    ),
}


async def synth(name: str, voice: str, text: str) -> None:
    out = OUT / f"{name}.mp3"
    communicate = edge_tts.Communicate(text, voice)
    await communicate.save(str(out))
    print(f"OK {out.name}")


async def main() -> None:
    OUT.mkdir(parents=True, exist_ok=True)
    for name, (voice, text) in MESSAGES.items():
        await synth(name, voice, text)


if __name__ == "__main__":
    asyncio.run(main())
