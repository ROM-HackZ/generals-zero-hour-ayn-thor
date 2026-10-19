#!/usr/bin/env python3
"""Audit owned retail campaign scripts/media without launching the game.

Usage: audit-campaign-cinematics.py GAME_FOLDER --out artifacts/campaign-audit
Add --ffmpeg PATH for a full video/audio decode of each referenced movie.
English retail assets are inspected. The output contains owned script dumps;
keep it out of source history. Short timers describe the original assets before
runtime corrections; check-campaign-dialogue-timing.py verifies reviewed fixes.
Short timers need context, not automatic rewriting.
"""
# GeneralsX @test Codex 19/10/2026 Repeatable campaign-wide cinematic review.
import argparse
import ast
import contextlib
import importlib.util
import io
import json
import math
from pathlib import Path
import re
import struct
import subprocess
import tempfile


def key(name):
    return name.replace("/", "\\").lower()


class RetailFiles:
    def __init__(self, root):
        self.entries = {}
        # Base assets, expansion assets, then loose-file overrides. Patch archives
        # have priority within each layer. Report archive origins for review.
        for folder in [root / "ZH_Generals", root]:
            for archive in sorted(folder.glob("*.big"), key=lambda p: (p.stem.lower().startswith("patch"), p.name.lower())):
                with archive.open("rb") as stream:
                    header = stream.read(16)
                    if header[:4] not in (b"BIGF", b"BIG4"):
                        continue
                    for _ in range(int.from_bytes(header[8:12], "big")):
                        offset, size = struct.unpack(">II", stream.read(8))
                        name = bytearray()
                        while True:
                            char = stream.read(1)
                            if not char:
                                raise ValueError(f"Unterminated archive entry: {archive}")
                            if char == b"\0":
                                break
                            name.extend(char)
                        self.entries[key(name.decode("latin1"))] = (archive, offset, size)
            for directory in ["Data", "Maps"]:
                for path in (folder / directory).rglob("*"):
                    if path.is_file():
                        self.entries[key(str(path.relative_to(folder)))] = (path, 0, path.stat().st_size)

    def read(self, name, limit=None):
        path, offset, size = self.entries[key(name)]
        with path.open("rb") as stream:
            stream.seek(offset)
            return stream.read(size if limit is None else min(size, limit))

    def ini(self, name):
        return self.read(name).decode("latin1").replace("\r", "")

    def definitions(self, directory, kind):
        result = {}
        # Match VideoPlayer::init / AudioManager::init: defaults, then overrides.
        for prefix in [f"data\\ini\\default\\{directory}", f"data\\ini\\{directory}"]:
            for name in sorted(self.entries):
                if name == prefix + ".ini" or (name.startswith(prefix + "\\") and name.endswith(".ini")):
                    for block in ini_blocks(self.ini(name), kind):
                        result[block[1].lower()] = block[2]
        return result


def ini_blocks(text, kind):
    return re.finditer(r"^\s*" + kind + r"\s+(\S+)\s*\n(.*?)^\s*End\s*$", text, re.I | re.M | re.S)


def quoted(params):
    return [ast.literal_eval(s) for s in re.findall(r"'(?:[^'\\]|\\.)*'|\"(?:[^\"\\]|\\.)*\"", params)]


def number(params):
    tail = params.rsplit(",", 1)[-1].strip()
    return float(tail.removeprefix("f=").removeprefix("i="))


def wav_duration_ms(data):
    if data[:4] != b"RIFF" or data[8:12] != b"WAVE":
        raise ValueError("Expected RIFF/WAVE speech")
    pos, fmt, sample_bytes = 12, None, 0
    while pos + 8 <= len(data):
        tag, size = struct.unpack_from("<4sI", data, pos)
        body = data[pos + 8:pos + 8 + size]
        if tag == b"fmt ":
            fmt = struct.unpack_from("<HHIIHH", body)
        elif tag == b"data":
            sample_bytes = len(body)
        pos += 8 + size + (size & 1)
    if not fmt:
        raise ValueError("Missing WAVE format")
    encoding, channels, rate, _, alignment, bits = fmt
    if encoding == 1:
        samples = sample_bytes // (channels * bits // 8)
    elif encoding == 17:
        # Matches the native cache and FFmpeg, including padded final blocks.
        samples = sum(1 + ((min(alignment, sample_bytes - p) - 4 * channels) // (4 * channels)) * 8
                      for p in range(0, sample_bytes - 4 * channels + 1, alignment))
    else:
        raise ValueError(f"Unsupported WAVE encoding {encoding}")
    return 1000 * samples / rate


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("game", type=Path)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--ffmpeg", type=Path, help="Also fully decode referenced campaign movies with this local FFmpeg executable")
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)
    retail = RetailFiles(args.game)
    repo = Path(__file__).resolve().parents[2]
    spec = importlib.util.spec_from_file_location("map_dump", Path(__file__).parent / "replay/dump-map-scripts.py")
    dump = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(dump)
    speech = retail.definitions("speech", "DialogEvent")
    durations, missing = {}, set()

    def duration(event):
        if event in durations:
            return durations[event]
        body = speech.get(event.lower(), "")
        match = re.search(r"^\s*Filename\s*=\s*(\S+)", body, re.I | re.M)
        filename = match[1] if match else None
        candidates = [f"Data\\Audio\\Speech\\English\\{filename}", f"Data\\Audio\\Speech\\{filename}"] if filename else []
        for candidate in candidates:
            if key(candidate) in retail.entries:
                durations[event] = wav_duration_ms(retail.read(candidate))
                return durations[event]
        missing.add(event)
        durations[event] = 0
        return 0

    campaign_ini = retail.ini("Data\\INI\\Campaign.ini")
    campaign_ini = re.sub(r";[^\n]*", "", campaign_ini)
    campaign_maps = {key(p) for p in re.findall(r"^\s*Map\s+(\S+)", campaign_ini, re.M)}
    selected = sorted(name for name in retail.entries if name.endswith(".map") and (
        name in campaign_maps or re.match(r"maps\\(md_|gc_|training)", name)))
    sources = "\n".join((repo / p).read_text(encoding="utf-8") for p in [
        "GeneralsMD/Code/GameEngine/Source/GameLogic/ScriptEngine/ScriptEngine.cpp",
        "GeneralsMD/Code/GameEngine/Source/GameLogic/ScriptEngine/ScriptActions.cpp"])
    handled = set(re.findall(r"case ScriptAction::(\w+)", sources))
    report = {"scope": "Static review; short timers are review candidates, not proven bugs. Inactive/helper scripts are included. No playthrough or certification of visual parity.",
              "maps": [], "unresolvedSpeechReferences": [], "durationsMs": durations, "movies": []}
    movie_uses = {}
    for title in re.findall(r"^\s*(?:IntroMovie|FinalVictoryMovie)\s+(\S+)", campaign_ini, re.M):
        movie_uses.setdefault(title.lower(), set()).add("Campaign.ini")
    for name in selected:
        leaf = name.split("\\")[-1]
        map_path = args.out / leaf
        map_path.write_bytes(retail.read(name))
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            dump.main(str(map_path), 16)
        text = output.getvalue()
        (args.out / (leaf + ".scripts.txt")).write_text(text, encoding="utf-8")
        if re.search(r"^\s*BAD ", text, re.M):
            raise ValueError(f"Map parser rejected {name}")
        scripts, current, group = [], None, ""
        for line in text.splitlines():
            if "[Group] " in line:
                group = line.strip()
            match = re.match(r"\s*SCRIPT (.+) active=(\d+) oneShot=(\d+) sub=(\d+)", line)
            if match:
                current = {"name": ast.literal_eval(match[1]), "group": group, "active": bool(int(match[2])),
                           "subroutine": bool(int(match[4])), "actions": [], "conditions": []}
                scripts.append(current)
            match = re.match(r"\s*IF (\w+)\((.*)\)$", line)
            if match and current is not None:
                current["conditions"].append((match[1], match[2]))
            match = re.match(r"\s*DO (\w+)\((.*)\)$", line)
            if match and current is not None:
                current["actions"].append((match[1], match[2]))
        actions = {action for script in scripts for action, _ in script["actions"]}
        record = {"map": leaf, "archive": retail.entries[name][0].name, "scripts": len(scripts),
                  "effects": sorted(a for a in actions if a.startswith(("CAMERA_", "MOVIE_"))),
                  "unhandledCinematicActions": sorted(a for a in actions if (a.startswith(("CAMERA_", "MOVIE_")) or a == "SPEECH_PLAY") and a not in handled),
                  "speechCues": 0, "shortCues": [], "unresolvedSpeechReferences": [],
                  "mainCampaignMission": bool(re.fullmatch(r"md_(usa|gla|chi)0[1-5]\.map", leaf))}
        timer_readers = {}
        for script in scripts:
            for condition, params in script["conditions"]:
                if condition == "TIMER_EXPIRED" and quoted(params):
                    timer_readers.setdefault(quoted(params)[0], []).append(script)
        for script in scripts:
            voices = [quoted(p)[0] for a, p in script["actions"] if a == "SPEECH_PLAY" and quoted(p)]
            record["speechCues"] += len(voices)
            voice_ms = max((duration(v) for v in voices), default=0)
            for voice in voices:
                if not duration(voice):
                    record["unresolvedSpeechReferences"].append({"event": voice, "script": script["name"],
                                                               "active": script["active"], "group": script["group"]})
            for action, params in script["actions"]:
                if action in ["MOVIE_PLAY_FULLSCREEN", "MOVIE_PLAY_RADAR"] and quoted(params):
                    movie_uses.setdefault(quoted(params)[0].lower(), set()).add(leaf)
            timers = [(quoted(p)[0], number(p) if a == "SET_MILLISECOND_TIMER" else number(p) / 30)
                      for a, p in script["actions"] if a in ["SET_TIMER", "SET_MILLISECOND_TIMER"] and quoted(p)]
            longest = max((seconds for _, seconds in timers), default=0)
            if voice_ms > 0 and longest and voice_ms + 100 > longest * 1000:
                extra = max(0, math.ceil(voice_ms * 30 / 1000) + 3 - math.ceil(longest * 30))
                record["shortCues"].append({"script": script["name"], "group": script["group"], "voices": voices,
                                           "active": script["active"], "subroutine": script["subroutine"],
                                           "speechMs": round(voice_ms, 2), "timers": timers, "extensionFrames": extra,
                                           "speechTimerReaders": [{"timer": timer, "script": reader["name"],
                                                                   "voices": [quoted(p)[0] for a, p in reader["actions"] if a == "SPEECH_PLAY" and quoted(p)]}
                                                                  for timer, _ in timers for reader in timer_readers.get(timer, [])
                                                                  if any(a == "SPEECH_PLAY" for a, _ in reader["actions"])]})
        report["maps"].append(record)
    videos = retail.definitions("video", "Video")
    for title, uses in sorted(movie_uses.items()):
        body = videos.get(title, "")
        match = re.search(r"^\s*Filename\s*=\s*(\S+)", body, re.I | re.M)
        paths = [f"Data\\English\\Movies\\{match[1]}.bik", f"Data\\Movies\\{match[1]}.bik"] if match else []
        path = next((p for p in paths if key(p) in retail.entries), None)
        movie = {"title": title, "usedBy": sorted(uses), "file": path,
                 "mainCampaignIntro": bool(re.fullmatch(r"md_(usa|gla|china)0[1-5]", title))}
        if not path:
            movie["unresolvedReason"] = "Video definition absent" if not match else "Referenced Bink file absent"
        if path:
            data = retail.read(path, 44)
            if data[:3] != b"BIK" or len(data) < 44:
                raise ValueError(f"Invalid Bink header: {path}")
            size, frames, _, _, width, height, rate, divisor = struct.unpack_from("<8I", data, 4)
            movie.update(frames=frames, width=width, height=height, fps=rate / divisor,
                         durationSeconds=frames * divisor / rate, bytes=retail.entries[key(path)][2])
            if not frames or size + 8 != movie["bytes"]:
                raise ValueError(f"Truncated/empty Bink movie: {path}")
            if args.ffmpeg:
                with tempfile.TemporaryDirectory(prefix="cinematic-", dir=args.out) as temp:
                    clip = Path(temp) / "clip.bik"
                    clip.write_bytes(retail.read(path))
                    result = subprocess.run([str(args.ffmpeg.resolve()), "-nostdin", "-v", "error", "-xerror", "-i", str(clip),
                                             "-map", "0:v:0", "-map", "0:a?", "-progress", "pipe:1", "-f", "null", "-"],
                                            capture_output=True, text=True, timeout=300)
                    counts = re.findall(r"^frame=(\d+)$", result.stdout, re.M)
                    movie["decodedFrames"] = int(counts[-1]) if counts else 0
                    movie["decodePassed"] = result.returncode == 0 and movie["decodedFrames"] == frames
                    movie["decoderErrors"] = result.stderr.strip()
                    print(f"Decoded {title}: {movie['decodedFrames']}/{frames}", flush=True)
        report["movies"].append(movie)
    report["unresolvedSpeechReferences"] = sorted(missing)
    (args.out / "audit.json").write_text(json.dumps(report, indent=2), encoding="utf-8")
    print(json.dumps({"maps": len(report["maps"]), "speechCues": sum(m["speechCues"] for m in report["maps"]),
                      "shortCues": sum(len(m["shortCues"]) for m in report["maps"]),
                      "mainCampaignMissions": sum(m["mainCampaignMission"] for m in report["maps"]),
                      "unresolvedSpeechReferences": len(missing), "movies": len(report["movies"]),
                      "unresolvedMovies": [m["title"] for m in report["movies"] if not m["file"]],
                      "decodeFailures": [m["title"] for m in report["movies"] if m.get("decodePassed") is False],
                      "report": str(args.out / "audit.json")}, indent=2))
    # Retail retains references to base-game training/victory movies absent from
    # expansion Video.ini. Report them, but only fail availability for the fifteen
    # playable Zero Hour campaign intros. Decoder errors always fail.
    return int(any((m["mainCampaignIntro"] and not m["file"]) or m.get("decodePassed") is False for m in report["movies"]) or
               any(m["unhandledCinematicActions"] for m in report["maps"]))


if __name__ == "__main__":
    raise SystemExit(main())
