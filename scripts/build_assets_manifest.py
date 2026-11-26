#!/usr/bin/env python3
"""Build updates/assets.json with SHA-256 for all alarm MP3s."""
from __future__ import annotations

import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
REPO = "mateusz-brzozowski/radiowezel-dzwonek"
TAG = "assets-v1"
OUT = ROOT / "updates" / "assets.json"

FILES = [
    ("tone_modulated_announce", "alarms/tones/tone_modulated_announce.mp3", "alarm_tone"),
    ("tone_continuous_clear", "alarms/tones/tone_continuous_clear.mp3", "alarm_tone"),
    ("tone_evacuate", "alarms/tones/tone_evacuate.mp3", "alarm_tone"),
    ("tone_lockdown", "alarms/tones/tone_lockdown.mp3", "alarm_tone"),
    ("tone_shelter", "alarms/tones/tone_shelter.mp3", "alarm_tone"),
    ("tone_bomb_chime", "alarms/tones/tone_bomb_chime.mp3", "alarm_tone"),
    ("voice_evac_fire", "alarms/voices/voice_evac_fire.mp3", "alarm_voice"),
    ("voice_evac_drill", "alarms/voices/voice_evac_drill.mp3", "alarm_voice"),
    ("voice_bomb", "alarms/voices/voice_bomb.mp3", "alarm_voice"),
    ("voice_lockdown", "alarms/voices/voice_lockdown.mp3", "alarm_voice"),
    ("voice_shelter", "alarms/voices/voice_shelter.mp3", "alarm_voice"),
    ("voice_air_raid", "alarms/voices/voice_air_raid.mp3", "alarm_voice"),
    ("voice_all_clear", "alarms/voices/voice_all_clear.mp3", "alarm_voice"),
    ("voice_test_short", "alarms/voices/voice_test_short.mp3", "alarm_voice"),
]


def sha256(path: Path) -> str:
    h = hashlib.sha256()
    with path.open("rb") as f:
        for chunk in iter(lambda: f.read(65536), b""):
            h.update(chunk)
    return h.hexdigest()


def main() -> None:
    entries = []
    for file_id, rel, kind in FILES:
        path = ROOT / rel
        if not path.exists():
            raise SystemExit(f"Missing {path}")
        digest = sha256(path)
        name = path.name
        entries.append(
            {
                "id": file_id,
                "path": rel.replace("\\", "/"),
                "url": f"https://github.com/{REPO}/releases/download/{TAG}/{name}",
                "sha256": digest,
                "kind": kind,
            }
        )
    payload = {"bundleVersion": 1, "files": entries}
    OUT.parent.mkdir(parents=True, exist_ok=True)
    OUT.write_text(json.dumps(payload, indent=2, ensure_ascii=False) + "\n", encoding="utf-8")
    print(f"Wrote {OUT} ({len(entries)} files)")


if __name__ == "__main__":
    main()
