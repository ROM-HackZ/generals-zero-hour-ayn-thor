<!-- Moved out of the README on 27/09/2026 to keep the README about playing on Android. -->
# What this port actually involved

"Porting" undersells how weird this journey was, so here's the honest shape of it.
The lineage below built the foundation: EA's source release, the community's
modernization, Fighter19's original Unix port, GeneralsX's macOS/Linux work.
None of that included a mobile online-multiplayer backend, or Android at all —
and both are hostile territory for a 2003 Windows RTS:

- **GameSpy is dead. The retail multiplayer stack assumes it isn't.** Zero
  Hour's entire online layer — matchmaking, lobbies, buddy lists, stats — was
  built on GameSpy SDK calls to servers EA shut down over a decade ago. The
  community answer is [GeneralsOnline](https://www.playgenerals.online) (by the
  GeneralsOnline Development Team: NGMP-based, REST + WebSocket, its own
  auth/session/lobby/stats/social services). Bringing it to Android meant
  porting its client and re-wiring the original `.wnd` UI screens and GUI
  callbacks — largely untouched since 2003 — one menu at a time (Welcome
  screen, Custom Match, Quickmatch, My Persona, Communicator), and then matching
  the PC client closely enough — wire format, checksums, simulation — that a
  phone and a PC can play the same lockstep match.
- **Async callbacks + screen teardown is a loaded gun.** Almost every real
  crash chased down on real devices during multiplayer bring-up turned out to
  be the same shape: an HTTP or WebSocket completion callback captured a raw
  pointer (a `GameWindow*`, a roster entry, a listbox) that was still valid
  when the request was *sent*, but the user had already backed out of the
  screen — or a second refresh had already torn it down — by the time the
  response landed. The fix pattern that recurred: capture a stable ID, not a
  pointer, and re-resolve (or bail) inside the callback.
- **The engine assumes a writable filesystem wherever it lives, and a mouse.**
  Android's scoped storage and SDL3's raw touch events needed the same kind of
  rerouting. Touch started as gesture-to-mouse translation, the way the iOS
  port pioneered it, and was later rebuilt as native touch input (see
  [Touch controls](TOUCH_CONTROLS.md)): a finger is not a mouse, and the
  difference was behind nearly every control bug.
- **Old data, new parser, sharp edges.** Zero Hour's `.ini` data layers on top
  of base Generals data, and the two games were split into separate build
  targets at some point in this codebase's history — with a couple of
  genuinely-still-used tokens (`DamageType=FLESHY_SNIPER`, `KindOf=AIRFIELD`)
  accidentally compiled out of the Zero Hour build in that split. Both looked
  like "someone's mod is doing something weird" until traced back to a
  preprocessor guard on the wrong side of an `#if`.
- **And a memory-corruption hunt that went all the way to the allocator.**
  Unresolved-crash-PC segfaults with no clean call stack, days apart, no
  obvious pattern — eventually traced to the engine's global `operator
  delete` override having no way to tell "one of ours" from a pointer a
  separately-linked `.so` (OpenAL, DXVK) allocated through its own copy of
  `new`. Fixed with an ownership cookie instead of blind trust.

**→ The Android engineering log: [docs/port/ANDROID_PORT.md](ANDROID_PORT.md)**
**→ The macOS/iOS war stories: [Porting Playbook §8 — the bug archaeology](PORTING_PLAYBOOK.md#8-post-ship-bug-hunts-junejuly-2026--the-archaeology-section)**
**→ The complete macOS/iOS engineering log: [docs/port/PORTING_PLAYBOOK.md](PORTING_PLAYBOOK.md)**
**→ How to do this to another game: [docs/port/PORTING_PATTERNS.md](PORTING_PATTERNS.md)**

Worth saying plainly: this was a **human + AI collaboration**. The engineering —
the C++, the cross-builds, the device debugging, the multiplayer backend — was
done by [Claude Code](https://claude.com/claude-code) (Anthropic's Claude),
directed and playtested by a human who described symptoms like *"the lobby
list is empty"* and *"it crashes right after I press Back"* and owned every
decision. Neither half ships this alone: one of us can't write C++, and the
other can't play-test on a real phone.
