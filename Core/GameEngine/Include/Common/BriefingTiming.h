#pragma once
#include <cmath>

// GeneralsX @bugfix Codex 19/10/2026 Extend a frozen briefing cue, preserving its
// relative fade/cut offsets. Three retail frames cover stream startup and drain.
inline int ThorBriefingDelayFrames(float speechMs, float longestTimerSeconds, int fps)
{
    if (speechMs <= 0.0f || longestTimerSeconds <= 0.0f) return 0;
    const int speechFrames = (int)std::ceil(speechMs * fps / 1000.0f) + 3;
    const int timerFrames = (int)std::ceil(longestTimerSeconds * fps);
    return speechFrames > timerFrames ? speechFrames - timerFrames : 0;
}
