# Thor Android controller implementation, 14/10/2026

Base: `v1.4.0`, `576e2265bf271176028d1cef4f2b3fe8caa20b34`.
Local branch: `thor/android-controller`. Package: `com.generalsthor.zerohour`.

The JNI bridge publishes immutable snapshots and queues intents. Only the engine
thread reads game objects, changes selection, dispatches commands and captures
radar data. The Java controller owns focus, radial menus, text entry, preferences,
and display lifecycle. Command activation checks its command name again because
the engine reuses command-window slots when selection changes.

The lower `Presentation` uses all Android displays: the Thor's internal second
display is absent from `DISPLAY_CATEGORY_PRESENTATION`. Relocated HUD windows
retain their command state but opt out of primary drawing and hit testing.
The primary tactical viewport uses the full display, including while paused.

Hardware diagnosis established X/Y/Z/RZ, GAS/BRAKE and HAT axes on the built-in
Odin Controller. `dispatchGenericMotionEvent` is necessary: SDL's surface consumes
joystick events before Activity.onGenericMotionEvent. A/B/X/Y keycodes are standard.
Camera scroll uses the engine's screen-plane scroll path once; converting to world
first reversed vertical movement and reduced speed. Right-stick X now rotates.
The owner confirmed the revised camera and wheel feel good. Right-stick up zooms
in by default; controller settings can reverse zoom independently of the R3 cursor.

The original phone navigation policy reserved 55 pixels at the bottom of the Thor.
The activity now requests immersive layout before surface measurement, and SDL
resize/pixel-size events update the GLES presentation target. Device captures
confirmed 1920x1080 with the prior bottom strip gone, including the pause backdrop.

Game saves use app-scoped external files. The private signing key stays outside
source control. Retail archives are transferred from the user's Steam install
with header/hash validation and are excluded from source/APK exports.

## Verification

- Native and Gradle debug builds pass; controller normalization and ten-slot boundary tests pass.
- Both panels render; native engine, video/audio, shell and AI skirmishes run at approximately 30 fps.
- USA radar exported; China radar remains unavailable until its original unlock requirements are met.
- Builder commands use existing ControlBar processing; illegal placement is rejected.
- A valid USA reactor spent $800; a queued dozer spent $1,000 and cancellation refunded it.
- Queue progress is normalized from the engine's 0-100 percentage to a 0-1 deck fraction.
- Local saves survived package updates. The latest skirmish was saved before the
  zoom update and loaded afterward with the same $7,500 and camera position;
  the engine reset camera height on load. Save/load navigation used touch.

## Remaining acceptance work

Complete campaign/Challenge smoke tests. Exercise
all factions' abilities, all ten groups and merge behavior, text entry/save-load,
disconnect/reconnect, suspend/resume and a single-screen fallback. Run a sustained
stress test. Disabled reasons use known funds, points, learning and cooldown state,
with a generic unavailable state for other restrictions. This is a development
build, not a claim that v1 acceptance is complete.

## Modern Generals presentation pass

Both radial menus share circular segments, faction accents, amber focus,
measured/wrapped labels and brief automatic details. Original command icons and
portraits come from the installed retail mapped-image collection. Hold Y displays
the full description; D-pad up/down scrolls long descriptions. Group pages keep
their eight-plus-two mapping and show counts and empty slots.

The lower dashboard separates radar, selection/health, production, a 6x2 command
grid, focused details and context prompts. Command and production pages are
independent; horizontal navigation stays within a strip and vertical navigation
crosses sections. Uniform scaling keeps the layout within the available window.
General's Powers has a dedicated entry/back control, real science names and point
costs; purchase actions replace the normal command grid while that panel is open.
Contextual and shortcut powers are deduplicated by native power identity.

Artwork requests are queued by JNI, resolved only on the engine thread (two/frame),
and published as immutable 128x128 ARGB copies. Native decoded-page and icon caches
each have an 8 MiB bound; Android bitmaps have a 16 MiB bound. Atlas UVs, rotation,
alpha and portrait aspect are preserved. Engine reset invalidates both caches.
Radar polling/bitmap updates happen outside drawing callbacks at 10 Hz, and the
camera footprint reuses the original radar transform. Missing artwork has a
neutral vector placeholder. Health colors remain independent of faction accents.

Host checks cover image cropping, alpha, rotated UVs, aspect, invalid images,
layout bounds/non-overlap, geometric navigation and controller boundaries.
Device captures cover China/USA/GLA artwork, radar availability and camera footprint,
both wheels and group pages, command pagination, disabled focus, production and
menu companion panels. A controller queue/cancel test returned funds from
$10,000 to $9,000 and back to $10,000. The owner confirmed the visuals read well.
Observed frame rate remained around 30 fps during these checks; this is not a
long-duration performance qualification. An AI match reached the score screen.

Menu regression testing found that movie-skip/Back could send pause-menu messages
in the shell; the bridge now limits those messages to actual matches. Shortcut
cooldowns resolve the original most-ready owning object even with no selection.
Local save files were preserved and new saves loaded across these APK updates.
Final checks covered General's Powers entry/back, a one-point SCUD Launcher
purchase (remaining points 1 -> 0; learned state true), sell-confirmation cancellation
and the full-description panel. China radar became available after its $500 upgrade.
Single-screen fallback, reconnect and a long stress run remain acceptance work.
