# Thor cinematic review — October 19, 2026

USA 1's opening and objective briefing were checked on the Thor. The owner
confirmed the corrected missile shots, black-and-white footage and narration.
Later missions were reviewed offline, without opening them on the device. No
retail map, speech file or movie was edited.

## Shared fixes

| Path | Change and evidence |
|---|---|
| Movie playback | Retain decoded video frames while audio prefetches; present each frame in order using a steady clock and fractional frame durations. The earlier device recording presented all 1,919 USA briefing frames; the startup movie presented all 1,961 frames. |
| Scripted camera | Apply the widescreen height adjustment to the player's camera, while preserving map-authored cinematic zoom. USA 1's missile now leaves the shot instead of exposing its held final animation pose. Controller zero-axis updates no longer stop camera scripts. |
| Scene filters | Allocate Android render targets despite the legacy unknown-chipset classification; clear their separate depth storage before each scene. A measured black target became a rendered scene after the depth fix. Correct render-target Y orientation and winding through the GL command queue. Upright black-and-white shots were verified on the Thor. |
| Audio gain | Apply the original effective-volume calculation before starting OpenAL streams and samples, and adjust the actual stream source when category volumes change. |
| Frozen surveillance briefings | Reserve enough fixed simulation frames for the selected speech asset. Shift a cue's fixed timers equally so its fade/cut offsets survive. This is limited to local single-player frozen black-and-white briefings; ordinary gameplay and other cinematics keep authored timers. |
| Fallback loading | Correct the final still's index from frame count to count minus one. Pump audio during the two voice-only waits, and service the Challenge VS animation during its fallback wait. Add an abort check to the base-game still wait. |
| Decoder seeking | Provide an archive-relative FFmpeg seek adapter. Rewind and decode through dependent Bink pictures to obtain the requested still, flush decoder state, suppress skipped audio and propagate seek failure. Reject demux errors before using packet stream indices. Both separately compiled FFmpeg copies carry the same fix. |

The render-target allocation also restores the prerequisite for the original
crossfade and motion-blur filters. Their initialization was checked in the device
log; this is not a visual verification of every later use of those filters.
The fallback-loading/seek changes compile in the APK but have not had a separate
low-memory device run.

## Offline coverage

The repeatable audit is `scripts/qa/audit-campaign-cinematics.py`. It examined
46 maps, 15,888 scripts and 2,045 named speech-action references, including
Challenge, training, shell/cinematic helpers and unused scripts. Within the
15 playable Zero Hour campaign missions:

| Campaign | Mission maps | Speech references resolved | Intro movies fully decoded | Video frames decoded |
|---|---:|---:|---:|---:|
| USA | 5 | 295 / 295 | 5 / 5 | 7,401 |
| GLA | 5 | 260 / 260 | 5 / 5 | 6,379 |
| China | 5 | 193 / 193 | 5 / 5 | 6,580 |
| Total | 15 | 748 / 748 | 15 / 15 | 20,360 |

The 15 movies decoded both audio and video without decoder errors, and each
video-frame count matched its retail Bink header. The shared Challenge background
also decoded all 180 frames. This was a local FFmpeg file check, not Android
frame-presentation or shot-synchronization verification.

Every camera, movie and speech action found by the script audit has an engine
handler. The main missions that request black-and-white are USA 1 and USA 5;
USA 5 and several Challenge/helper maps also request motion-blur jumps. These
all use the shared rendering paths above.

The broader inventory contains 85 unresolved speech names in helper/unused or
Challenge scripts; none occur in the 15 main mission maps. It also retains two
campaign references (`TrainingCampaign`, `USACampaignVictory`) whose definitions
are absent from the expansion's effective Video.ini. These are reported as
references requiring context, not evidence that playable campaign intros are
missing. No guessed replacement dialogue or retail data override was added.

## Dialogue timing review

The audit flags 114 speech-plus-timer batches for review across the entire
inventory, including 87 in the main mission maps. These are not 114 confirmed
bugs. Timers may control unrelated events; cheers, radio chatter, aircraft sound
effects and short camera-flash sounds can intentionally overlap. Inactive and
subroutine scripts are included. The report retains the script context and
timer readers that start another speech cue.

The follow-up review traced each direct spoken-line dependency and matching
sibling-script triggers. It added an explicit catalog of 37 reviewed waits,
29 of which are shorter than their English speech plus the three-frame startup/
drain margin:

| Mission | Reviewed dependencies | Extended English waits |
|---|---:|---:|
| China 2 | 4 | 4 |
| GLA 1 | 1 | 1 |
| GLA 2 | 4 | 3 |
| GLA 4 | 14 | 13 |
| USA 1 | 2 | 2 |
| USA 2 | 4 | 2 |
| USA 3 | 3 | 3 |
| USA 5 | 5 | 1 |

The registry is `Core/GameEngine/Include/Common/CampaignDialogueCues.def`, with
identifiers and timing metadata rather than copied retail scripts. Only the
named timer in its exact map/script context is extended, and only when its
authored duration still matches. The selected asset's duration is read at runtime;
already-long waits are preserved. No playback state, hardware clock or RNG is used.
The guard requires actual single-player campaign mode, excluding the replay
fallback accepted by the engine's broader `isInSinglePlayerGame()` helper.

This covers the opening/briefing chains in China 2, USA 2 and GLA 4, GLA 4's
outro dialogue chains, USA 1's reinforcement/MOAB speech handoffs, USA 3's race
exchange and two sibling-script briefing waits, USA 5's opening exchange, GLA 1's
objective dialogue and GLA 2's cinematic return/final narration. USA 3's speech
and timer can be in different scripts with identical triggers, so a same-action-
batch check alone missed them. GLA 1's shorter dialogue wait was masked by a
longer unrelated timer in its action batch. Neither unrelated timer is changed.

The separate GLA 2 `End game` script is inactive and has no activation/call
reference in the map. The reachable final-narration timer readers were checked;
the offline verifier reports the unused reader and fails if it gains a reference.

`scripts/qa/check-campaign-dialogue-timing.py` verifies all 37 records against
owned maps, checks every timer reader and speech asset, and generates test vectors.
Those real asset durations passed the engine's C++ timing/identity functions:
every corrected wait fits its speech, unchanged waits remain long enough, and
adjacent camera/FX timers do not match a correction.

Some other short waits are deliberately left authored: USA 1's phase-three
objective branches also create radar objects and schedule several independent
voice reminders; USA 3's POW-help follow-up consumes gameplay RNG for another
timer; GLA 4's ferry handoff also transfers ownership. Reordering those branches
needs more than extending a dialogue timer. Cheers, aircraft FX, camera cuts,
unit orders, spawns and the Challenge/helper timing candidates were also reviewed
without broadly stretching them. These remaining mixed branches are not claimed
fixed or verified by the dialogue-only correction.

USA 1's confirmed frozen briefing is covered by the narrower engine correction.
A real playback log showed all seven expected lines completing in order with
no overlap and roughly 100–169 ms between successive lines; the owner confirmed
the shots and speech now matched. The log checker is
`scripts/qa/check-briefing-playback.py`.

## Validation boundary

All seven host checks, the actual-asset timing vectors and the local retail-30-Hz
native/APK build pass. No owned media is included in the repository. Extracted
maps, script dumps, recordings, temporary PC audio tools and test captures are
disposable outputs; the cleanup keeps the current APK and matching symbols.

This establishes file availability, decodability, action-handler coverage and
the shared fixes described above. It does not certify every mission, subtitle,
language, camera shot or transition against the PC game. Later scenes still
need normal playthrough observation. The original offline review ended with a local build. Alpha.6 later packaged
these corrections and was installed with the game closed. Its main menu opened
and the existing save was byte-identical after the update; no later mission was
opened during deployment.
