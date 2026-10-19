#!/usr/bin/env python3
"""Check a requested spoken-cue sequence against actual OpenAL start/end tracing.

Enable gx_audio_trace.txt before launching; never infer audio from camera travel.
Usage: check-briefing-playback.py LOG --events NAME,NAME,...
"""
# GeneralsX @test Codex 19/10/2026 Reproduce missing/overlapping briefing speech.
import argparse
from pathlib import Path
import re


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("log", type=Path)
    parser.add_argument("--events", required=True, help="Comma-separated cue names in mission order")
    args = parser.parse_args()
    expected = args.events.split(",")
    starts, ends = [], {}
    pattern = re.compile(r"stream (start|end) wallMs=([0-9.]+).*?event=(\S+) src=(\d+)")
    for line in args.log.read_text(encoding="utf-8", errors="replace").splitlines():
        match = pattern.search(line)
        if not match or match[3] not in expected:
            continue
        kind, wall, name, source = match.groups()
        key = (name, source)
        if kind == "start":
            starts.append((name, source, float(wall)))
        else:
            ends[key] = float(wall)
    errors = []
    actual = [name for name, _, _ in starts]
    if actual != expected:
        errors.append(f"Cue sequence differs: expected {expected}, got {actual}")
    for index, (name, source, start) in enumerate(starts):
        end = ends.get((name, source))
        if end is None:
            errors.append(f"{name} has no completed-playback trace")
            continue
        if end < start:
            errors.append(f"{name} ends before it starts")
        if index + 1 < len(starts) and end > starts[index + 1][2]:
            errors.append(f"{name} overlaps {starts[index + 1][0]} by {end - starts[index + 1][2]:.0f} ms")
        print(f"{name}: played {(end - start) / 1000:.3f}s")
    for error in errors:
        print(f"FAIL: {error}")
    if not errors:
        print(f"PASS: all {len(expected)} cues completed in order without overlap")
    return int(bool(errors))


if __name__ == "__main__":
    raise SystemExit(main())
