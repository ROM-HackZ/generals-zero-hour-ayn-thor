# Playing on the Thor

The top screen is the battlefield. The bottom screen holds the radar, selection,
commands, production and General's Powers. Buttons and touch use the same commands.

## Camera and selection

| Button | What it does |
|---|---|
| Left stick | Move the camera |
| Right stick left/right | Rotate the camera |
| Right stick up/down | Zoom in/out |
| Hold R3 + right stick | Move the targeting cursor precisely |
| A | Select a unit, give an order, or confirm |
| Double A | Select matching units |
| Hold A + move the camera | Select units in a box |
| B | Cancel or go back |
| X | Attack-move |
| Y | Stop |
| LT | Add to the selection; queue orders as waypoints |
| L3 | Center the camera on the selection |
| D-pad Up | Next idle builder |
| D-pad Down | Command center |
| D-pad Left | Cycle army units |
| D-pad Right | Cycle production buildings |
| Start | Pause/menu |

Dead zone, speed, pan/zoom direction, snapping and button mappings are in
**Controller settings** on the launcher.

## Command wheel: hold RT

Move the left stick towards an icon and press A to use it. D-pad left/right changes
page. The center shows the name, cost, readiness and a short description.
Hold Y for the full description; D-pad up/down scrolls it if needed.

For a targeted ability, release RT after choosing it, aim on the battlefield,
then press A. The game still checks range and prerequisites. B cancels.
Selling opens a confirmation first.

## Building placement (game preview 0.1.0-alpha.2)

1. Select a worker/dozer and choose the building, then release RT.
2. Move the preview into position. **A locks that spot**.
3. The **left stick chooses its facing direction**. Right-stick camera rotation
   and zoom remain available; the building stays at its chosen world position.
4. **A confirms construction**. The game checks the site, orientation, builder,
   money and prerequisites before accepting it.

**B cancels the preview at either step**, including while rotating. Choosing a
spot or rotating the preview spends nothing and sends no construction order.
An illegal confirmation keeps the preview available for correction/cancellation.
Return the left stick to center after locking the site, then point it in the
direction you want the building to face.

With touch, one finger moves the preview. Lifting chooses the spot; the next
one-finger touch/drag chooses facing. **Build** on the bottom screen (or A)
confirms; **Cancel** (or B) discards. Two-finger camera gestures remain available.
The bottom screen also has **Choose spot** for locking the current preview.

### Cancel a building already under construction (alpha.3)

Select the unfinished building itself. **Cancel Construction** appears in the
RT command wheel and bottom-screen command grid. Choose it to remove the site
and get the game's construction refund. Once the building finishes, its normal
commands replace this button. B still cancels an unconfirmed placement preview;
it does not demolish a building you have already ordered.

## Touch controls (game preview 0.1.0-alpha.2)

| Gesture on the battlefield | Action |
|---|---|
| One-finger tap | Select a unit or issue an order |
| Double tap | Select matching units |
| One-finger drag | Draw a selection rectangle; select on release |
| Two-finger drag | Pan the camera |
| Pinch | Zoom |
| Two-finger twist | Rotate the camera |

A small movement threshold separates taps from drags. Adding a second finger
cancels an unfinished selection rectangle and begins camera control, preserving
the previous selection. After a camera gesture, lift the remaining finger before
starting another selection. A canceled touch does not commit the box.

While placing a building or targeting an ability, one finger aims that preview
instead of drawing a selection box. Menu/list scrolling keeps its usual behavior.

## Groups: hold LB

Choose a group with the left stick. A recalls it, X replaces it with your selection,
LT + X adds the selection, and Y centers the camera on it.

There are ten groups: 1-8 on the first page, 9-10 on the second. D-pad left/right
changes page. The wheel shows how many units each group holds; unused groups say Empty.

## Radar and command deck

**RB** switches to map control. The left stick moves its cursor; Y looks there and
A orders there. B returns. The outline on the radar shows the camera's view.
You only get radar when your faction has earned it, with the game's normal fog.

**Tap the bottom-screen minimap to look at that spot. Drag directly on it to pan
across the touched spots.** Touch always controls the camera there; it does not
order the selected army. Rectangular maps keep their aspect ratio, and tapping
the empty border does nothing. A captured drag stays on the map and clamps to
its edge if your finger leaves it. These fixes are in game preview 0.1.0-alpha.2.

**View** focuses the bottom screen. D-pad moves between the controls you can see;
A uses one, B/View returns. Left/right stays within a strip; up/down moves between
commands, production and the Powers button. At a strip's edge, left/right changes
its page. Touch arrows work too.

Production has its own strip. Focus a queued item and press A to cancel it through
the game's normal queue. The detail panel says what the focused action does.
Unavailable actions can be inspected but cannot be used.

The **Powers** button opens General's Powers. Choose a power to spend its displayed
point cost. Learned powers are marked; B or the Back button returns to commands.

## Menus and text

D-pad/left stick chooses a menu control; A confirms and B goes back. A enters an
editable field, D-pad changes its value, and A finishes.

For text, D-pad chooses a character, A types it, X deletes, Y accepts and B cancels.
The on-screen keyboard works without opening Android's keyboard over the game.

Some controller paths are still being tested; see [the preview status](../../README.md#what-has-been-tested).
