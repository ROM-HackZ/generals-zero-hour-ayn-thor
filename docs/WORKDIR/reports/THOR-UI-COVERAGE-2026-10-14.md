# Thor command and UI coverage audit

The cancel-construction fix prompted a check of every ControlBar context and
the game's registered GUI command types. The old export recognised regular
command slots and a few special buttons. The new export carries every visible
registered game action, keeps production separate, and copies auxiliary controls
from beacon/observer panels and live dialogs. Original visibility, enablement,
callbacks, targeting and gameplay validation remain authoritative.

## Findings and changes

| Original UI | Coverage after this pass |
|---|---|
| Normal unit/building commands | All visible registered action types are exported, including targeting, upgrades, rally points, weapons, guard, force attack and builder pages. |
| Multiple selection | Uses the original shared command windows; each action slot is retained. |
| Structure/transport inventory | Every occupant gets its own entry, original unit image, name and veterancy. Empty slots remain disabled. IDs bind activation to the intended occupant. |
| Production | Unit/upgrade cancellation stays in its own strip. A production ID prevents a stale slot from cancelling the next item. |
| Under construction | Dedicated cancel button and construction percentage are copied. |
| OCL reinforcement timer | Dedicated Sell/Rally Point button, countdown and progress are copied. |
| Beacon | Visible placement, delete, clear-text and text-entry controls reach the deck. Text uses the original edit callback. |
| Observer player list/info | Player choices, Back and stats reach the deck; resources use the observed player. |
| General's Powers | Purchases, point costs, learning and cooldowns remain. Rank progress is restored. An unavailable contextual power cannot hide an available shortcut. |
| Global HUD | Communicator/campaign briefing are exposed. Start, D-pad Up and the dedicated Powers button retain options, idle-builder and science access. The old group panel moves off the top screen with the external HUD. |
| Menus/live modals | Original controls, entry values, checkbox/radio state and slider values are copied. A modal gets menu navigation even when it does not pause the simulation. |
| Radar | Existing texture layers/camera outline remain. Separate hero reticles and rotating/fading event triangles are copied using the original filtering and projection. Alert audio plays once when the external HUD is active. |

Tactical Tools adds original keyboard commands for Last Alert, Select Army,
Select Aircraft, Select Hero, Select Matching, Scatter, Create Formation,
Reset Camera and Next Builder. Open it from the deck header or RT wheel; B
returns to normal commands. D-pad Up remains idle-builder selection.

The inherited Deploy and Follow meta commands are unimplemented in the engine;
they are not presented as working tools. Debug, simulation-speed and render
diagnostic hotkeys are not player commands. The simulation stays at retail 30 Hz.
World overlays, mission messages, timers and shell/campaign dialogs continue to
draw through their original UI; they are not hidden with the ControlBar.

## Validation scope

The context/command-type audit covers the source paths above. Local native/Java
builds and six host programs check input, layout, artwork, radar geometry and
duplicate-power availability. The separate device harness can start a fresh
skirmish so tests do not depend on the owner's evolving base. It creates its
fixtures through ordinary construction and production, without game-data
overrides, cheats or production-engine test hooks.

Fresh USA checks cover placement/cancellation, touch and radar navigation,
Tactical Tools, queue refunds and stale production tokens. The extended fixture
builds a reactor, barracks and fire base, produces two Rangers, garrisons them
and checks individual exits and stale occupant tokens. A separate China fixture
checks Overcharge on/off and its active state.
These targeted device checks passed on the Thor. The inventory fixture waits
for its saved group to commit and for the original command panel to refresh
after containment, rather than treating an earlier object count as UI readiness.

Source coverage is broader than device acceptance. Multiplayer beacon/observer
flows, OCL timer structures, every unit ability, heroic reticles in combat,
campaign-specific dialogs and long sessions still need player testing. This is
an audit and targeted regression pass, not a claim that every game situation has
been played. Retail data, saves, test APKs and signing keys stay out of the repo.
