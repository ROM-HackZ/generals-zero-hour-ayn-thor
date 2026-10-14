// GeneralsX @feature Codex 14/10/2026 CPU atlas cropping shared with host verification.
#pragma once
#include <algorithm>
#include <vector>
namespace ThorArtwork {
inline std::vector<unsigned int> crop(const std::vector<unsigned int> &page, int pageWidth, int pageHeight,
    int imageWidth, int imageHeight, float left, float top, float right, float bottom, bool rotated) {
    if (pageWidth <= 0 || pageHeight <= 0 || imageWidth <= 0 || imageHeight <= 0 ||
        page.size() != size_t(pageWidth) * pageHeight) return {};
    float scale = 128.f / std::max(imageWidth, imageHeight);
    int width = std::max(1, int(imageWidth * scale)), height = std::max(1, int(imageHeight * scale));
    std::vector<unsigned int> result(128 * 128, 0);
    for (int y = 0; y < height; ++y) for (int x = 0; x < width; ++x) {
        float u = (x + .5f) / width, v = (y + .5f) / height;
        float su = rotated ? 1 - v : u, sv = rotated ? u : v;
        int sx = std::max(0, std::min(pageWidth - 1, int((left + (right - left) * su) * pageWidth)));
        int sy = std::max(0, std::min(pageHeight - 1, int((top + (bottom - top) * sv) * pageHeight)));
        result[((128 - height) / 2 + y) * 128 + (128 - width) / 2 + x] = page[sy * pageWidth + sx];
    }
    return result;
}
}
