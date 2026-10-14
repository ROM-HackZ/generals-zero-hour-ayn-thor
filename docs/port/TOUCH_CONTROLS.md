# Touch controls

The gesture table is in the [README](../../README.md#touch-controls). This page is the rest: how
gestures are recognised, what is deliberately off on touch, and how to report a control problem.

Every gesture is classified only once the intent is unambiguous (a short
delay or distance threshold), so an ordinary tap never misfires into a
selection box, and pan and zoom don't flicker into each other.

Screen-edge scrolling is **off** on touch. It is defined by a pointer resting
near an edge, and it ends only when a later pointer event reports a position
back inside the safe zone — a condition that cannot occur without a pointer,
which is why it used to leave the camera scrolling on its own. Dragging with
a finger is the touch equivalent and is already direct.

If controls misbehave, turn on **Touch input overlay** in Setup →
Diagnostics: it draws the current gesture, where your finger is, where the
engine thinks the pointer is, and the camera's scroll anchor, and writes
matching `[gxtouch]` lines into the log. Those four things are the same thing
on a desktop and different things on a touchscreen, and their disagreement is
what every control bug here has turned out to be.

For how this is built and how to add a gesture, see
[`docs/WORKDIR/lessons/LESSON-touch-input-is-not-a-mouse.md`](../WORKDIR/lessons/LESSON-touch-input-is-not-a-mouse.md).
