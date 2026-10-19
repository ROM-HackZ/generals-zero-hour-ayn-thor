#!/usr/bin/env python3
"""Verify reviewed dialogue dependencies against owned English retail maps/audio.

Usage: check-campaign-dialogue-timing.py GAME_FOLDER --out artifacts/dialogue-review
Writes owned script dumps and host-test vectors to the ignored output directory.
Run test-thor.sh first, then build/thor-tests/briefing OUTPUT/timing-vectors.tsv
to verify these actual asset durations through the engine's C++ timing code.
"""
# GeneralsX @test Codex 19/10/2026 Prove each correction targets the reviewed
# dialogue dependency, including speech/timers in separate sibling scripts.
import argparse
import ast
import contextlib
import csv
import importlib.util
import io
import json
import math
from pathlib import Path
import re


def load_module(name, path):
    spec = importlib.util.spec_from_file_location(name, path)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def scripts_from_dump(text):
    scripts, current = [], None
    for line in text.splitlines():
        match = re.match(r"\s*SCRIPT (.+) active=(\d+) oneShot=\d+ sub=(\d+)", line)
        if match:
            current = {"name": ast.literal_eval(match[1]), "active": bool(int(match[2])), "subroutine": bool(int(match[3])),
                       "conditions": [], "actions": [], "falseActions": []}
            scripts.append(current)
        elif current is not None:
            match = re.match(r"\s*(IF|DO|ELSE) (\w+)\((.*)\)$", line)
            if match:
                bucket = {"IF": "conditions", "DO": "actions", "ELSE": "falseActions"}[match[1]]
                current[bucket].append((match[2], match[3]))
    return scripts


def unique_script(scripts, name):
    matches = [script for script in scripts if script["name"] == name]
    if len(matches) != 1:
        raise ValueError(f"Expected one script named {name!r}, found {len(matches)}")
    return matches[0]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("game", type=Path)
    parser.add_argument("--out", type=Path, required=True)
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)
    qa = Path(__file__).resolve().parent
    repo = qa.parent.parent
    audit = load_module("cinematic_audit", qa / "audit-campaign-cinematics.py")
    dump = load_module("map_dump", qa / "replay/dump-map-scripts.py")
    retail = audit.RetailFiles(args.game)
    speech = retail.definitions("speech", "DialogEvent")
    catalog = repo / "Core/GameEngine/Include/Common/CampaignDialogueCues.def"
    rows = [next(csv.reader([match[1]], skipinitialspace=True)) for match in
            re.finditer(r"^THOR_DIALOGUE_CUE\((.*)\)$", catalog.read_text(encoding="utf-8"), re.M)]
    maps, results, vectors, seen = {}, [], [], set()
    for mapid, writer_name, timer, authored, event, source_name, reader_name in rows:
        identity = (mapid, writer_name, timer)
        if identity in seen:
            raise ValueError(f"Duplicate cue: {identity}")
        seen.add(identity)
        if mapid not in maps:
            path = next(name for name in retail.entries if name == audit.key(f"Maps/{Path(mapid).stem}/{mapid}"))
            local = args.out / mapid
            local.write_bytes(retail.read(path))
            output = io.StringIO()
            with contextlib.redirect_stdout(output):
                dump.main(str(local), 16)
            if re.search(r"^\s*BAD ", output.getvalue(), re.M):
                raise ValueError(f"Map parser rejected {mapid}")
            (args.out / (mapid + ".scripts.txt")).write_text(output.getvalue(), encoding="utf-8")
            maps[mapid] = scripts_from_dump(output.getvalue())
        scripts = maps[mapid]
        writer, source, reader = [unique_script(scripts, name) for name in [writer_name, source_name, reader_name]]
        authored_seconds = float(authored.removesuffix("f"))
        writes = [(action, params) for script in scripts for action, params in script["actions"] + script["falseActions"]
                  if action in ["SET_TIMER", "SET_MILLISECOND_TIMER", "SET_RANDOM_TIMER", "SET_RANDOM_MSEC_TIMER"]
                  and audit.quoted(params) and audit.quoted(params)[0] == timer]
        own_writes = [(action, params) for action, params in writer["actions"]
                      if action in ["SET_TIMER", "SET_MILLISECOND_TIMER"] and audit.quoted(params)[0] == timer]
        if len(writes) != 1 or len(own_writes) != 1:
            raise ValueError(f"Ambiguous timer writer: {identity}")
        action, params = own_writes[0]
        seconds = audit.number(params) / (30 if action == "SET_TIMER" else 1)
        if abs(seconds - authored_seconds) > .0001:
            raise ValueError(f"Authored wait changed: {identity}: {seconds}")
        if not any(
                action == "SPEECH_PLAY" and audit.quoted(params)[0] == event for action, params in source["actions"]):
            raise ValueError(f"Speech is absent from its reviewed source: {event}")
        if source is not writer and source["conditions"] != writer["conditions"]:
            raise ValueError(f"Sibling cue triggers differ: {identity}")
        readers = [script for script in scripts if ("TIMER_EXPIRED", repr(timer)) in script["conditions"]]
        if reader not in readers:
            raise ValueError(f"Next dialogue does not wait on this timer: {identity}")
        # USA 3's other readers wait for HAS_FINISHED_SPEECH too, then set the next
        # dialogue wait. They must not acquire camera/combat actions unnoticed.
        presentation = {"SPEECH_PLAY", "SET_TIMER", "SET_MILLISECOND_TIMER", "ENABLE_SCRIPT", "DISABLE_SCRIPT",
                        "SHOW_MILITARY_CAPTION", "SET_FLAG", "SETUP_CAMERA", "MUSIC_SET_TRACK", "NO_OP"}
        referenced_names = {value for script in scripts for _, params in script["actions"] + script["falseActions"]
                            for value in audit.quoted(params)}
        reachable_readers = [script for script in readers if script["active"] or script["subroutine"] or script["name"] in referenced_names]
        if any(action not in presentation for script in reachable_readers for action, _ in script["actions"] + script["falseActions"]):
            raise ValueError(f"Timer has an unreviewed non-presentation reader: {identity}")
        # These are human dialogue cues, never the separately authored camera/aircraft FX.
        if not event.startswith(("Mis", "Eva")):
            raise ValueError(f"Non-dialogue event in catalog: {event}")
        body = speech[event.lower()]
        filename = re.search(r"^\s*Filename\s*=\s*(\S+)", body, re.I | re.M)[1]
        candidates = [f"Data/Audio/Speech/English/{filename}", f"Data/Audio/Speech/{filename}"]
        sound_path = next(path for path in candidates if audit.key(path) in retail.entries)
        speech_ms = audit.wav_duration_ms(retail.read(sound_path))
        frames = math.ceil(seconds * 30)
        corrected = max(frames, math.ceil(speech_ms * 30 / 1000) + 3)
        results.append({"map": mapid, "script": writer_name, "timer": timer, "speech": event,
                        "speechMs": speech_ms, "originalFrames": frames, "correctedFrames": corrected,
                        "readers": [script["name"] for script in readers],
                        "inactiveUnreferencedReaders": [script["name"] for script in readers if script not in reachable_readers]})
        vectors.append("\t".join([mapid, writer_name, timer, str(seconds), str(speech_ms)]))
    (args.out / "timing-vectors.tsv").write_text("\n".join(vectors) + "\n", encoding="utf-8")
    (args.out / "dialogue-review.json").write_text(json.dumps(results, indent=2), encoding="utf-8")
    print(f"PASS: {len(rows)} reviewed dialogue dependencies in {len(maps)} maps; "
          f"{sum(r['correctedFrames'] > r['originalFrames'] for r in results)} waits need extension.")
    print(f"Native test vectors: {args.out / 'timing-vectors.tsv'}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
