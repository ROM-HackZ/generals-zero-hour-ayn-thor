#pragma once
#include <algorithm>
#include <cmath>

// GeneralsX @bugfix Codex 14/10/2026 Inverse of the displayed north-up radar, including letterboxing.
namespace DeckRadarGeometry {
inline bool toCells(float x, float y, float left, float top, float right, float bottom,
    int width, int height, int &cellX, int &cellY) {
    if (!std::isfinite(x) || !std::isfinite(y) || !(right > left) || !(bottom > top) ||
        width <= 0 || height <= 0 || x < left || x > right || y < top || y > bottom) return false;
    const float u = (x - left) / (right - left), v = (y - top) / (bottom - top);
    cellX = std::clamp(int(u * width), 0, width - 1);
    cellY = std::clamp(int((1.f - v) * height), 0, height - 1);
    return true;
}
}
