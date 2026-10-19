#!/usr/bin/env python3
# GeneralsX @test Codex 18/10/2026 Check real movie presentation order, completion and deliberate skips.
"""Run with a device engine log recorded with gx_audio_trace.txt enabled.

Use sequential full-screen movies (startup/briefings), without simultaneous window movies.
No retail files are read or distributed by this check.
"""
import argparse
import re
from pathlib import Path


def check(log, require_complete=1, require_skipped=0):
    current = None
    complete = skipped = 0
    for line in log.splitlines():
        start = re.search(r"\[GX-VIDEO\] start frames=(\d+) frame_ms=([\d.]+)", line)
        if start:
            if current is not None:
                raise ValueError("Overlapping movie streams; record sequential playback")
            current = {"frames": int(start[1]), "frame_ms": float(start[2]), "shown": []}
        frame = re.search(r"movie video present=(\d+)", line)
        if frame and current is not None:
            index = int(frame[1])
            if index != len(current["shown"]):
                raise ValueError(f"Video jumped from {len(current['shown'])-1} to {index}")
            current["shown"].append(index)
        end = re.search(r"\[GX-VIDEO\] close presented=(\d+) index=(\d+) frames=(\d+) decoded=(\d+) elapsed_ms=(\d+)", line)
        if end and current is not None:
            rendered, index, frames, decoded, elapsed = map(int, end.groups())
            if rendered != len(current["shown"]):
                raise ValueError("Missing presentation trace; enable gx_audio_trace.txt before launch")
            # A skip can close after frameNext has prepared the following picture.
            if rendered and index not in (rendered-1, rendered):
                raise ValueError("Presentation position disagrees with the rendered frames")
            if decoded < rendered or rendered > frames:
                raise ValueError("Invalid decoder/presentation counts")
            finished = rendered == frames
            complete += finished
            skipped += not finished
            print(f"{'Complete' if finished else 'Skipped'}: {rendered}/{frames} frames, {elapsed/1000:.2f}s")
            current = None
    if current is not None:
        raise ValueError("Recording ends during a movie; capture its close line")
    if complete < require_complete or skipped < require_skipped:
        raise ValueError(f"Need {require_complete} complete and {require_skipped} skipped movies; found {complete} and {skipped}")
    return complete, skipped


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("log", type=Path)
    parser.add_argument("--require-complete", type=int, default=1)
    parser.add_argument("--require-skipped", type=int, default=0)
    args = parser.parse_args()
    try:
        check(args.log.read_text(errors="replace"), args.require_complete, args.require_skipped)
    except ValueError as error:
        parser.exit(1, f"Movie check failed: {error}\n")
