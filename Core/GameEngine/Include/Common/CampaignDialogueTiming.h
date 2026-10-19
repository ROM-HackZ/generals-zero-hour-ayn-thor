#pragma once
#include "Common/BriefingTiming.h"
#include <cstring>

// GeneralsX @bugfix Codex 19/10/2026 Retail dialogue waits need the selected
// speech asset's duration. The reviewed catalog identifies dialogue dependencies;
// short camera, combat and sound-effect timers are not inferred from their names.
struct ThorDialogueCue
{
    const char *map, *script, *timer;
    float authoredSeconds;
    const char *speech, *speechScript, *nextScript;
};

inline const ThorDialogueCue ThorDialogueCues[] = {
#define THOR_DIALOGUE_CUE(map, script, timer, seconds, speech, source, next) \
    {map, script, timer, seconds, speech, source, next},
#include "Common/CampaignDialogueCues.def"
#undef THOR_DIALOGUE_CUE
};

inline const ThorDialogueCue *ThorFindDialogueCue(bool localCampaign, const char *mapPath,
    const char *script, const char *timer, float authoredSeconds)
{
    if (!localCampaign || !mapPath || !script || !timer) return nullptr;
    const char *leaf = mapPath;
    for (const char *p = mapPath; *p; ++p) if (*p == '/' || *p == '\\') leaf = p + 1;
    for (const auto &cue : ThorDialogueCues) {
        const char *p = leaf, *q = cue.map;
        while (*p && *q) {
            const char lower = *p >= 'A' && *p <= 'Z' ? *p + ('a' - 'A') : *p;
            if (lower != *q) break;
            ++p; ++q;
        }
        if (*p || *q || std::strcmp(script, cue.script) || std::strcmp(timer, cue.timer)) continue;
        // A changed/modded script's different authored wait does not match this correction.
        if (std::fabs(authoredSeconds - cue.authoredSeconds) < .0001f) return &cue;
    }
    return nullptr;
}
