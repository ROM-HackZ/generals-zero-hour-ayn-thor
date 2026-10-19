// GeneralsX @test Codex 19/10/2026 Shipped USA briefing audio must fit each shot.
#include "Common/CampaignDialogueTiming.h"
#include <cassert>
#include <fstream>
#include <iostream>
#include <sstream>
#include <string>
int main(int argc, char **argv)
{
    const struct { float speechMs, cutSeconds, fadeSeconds; } cues[] = {
        {7590.0f, 6.5f, 6.0f}, {5600.0f, 5.0f, 4.5f},
        {7636.0f, 5.75f, 5.25f}, {4212.0f, 3.5f, 3.0f}
    };
    for (const auto &cue : cues) {
        const int delay = ThorBriefingDelayFrames(cue.speechMs, cue.cutSeconds, 30);
        const float cut = cue.cutSeconds + delay / 30.0f;
        const float fade = cue.fadeSeconds + delay / 30.0f;
        assert(cut * 1000 >= cue.speechMs + 99);
        assert(std::fabs((cut - fade) - (cue.cutSeconds - cue.fadeSeconds)) < .001f);
    }
    assert(ThorBriefingDelayFrames(1250, 1.75f, 30) == 0);
    assert(ThorBriefingDelayFrames(0, 6.5f, 30) == 0);
    assert(ThorBriefingDelayFrames(7000, 0, 30) == 0);
    assert(ThorBriefingDelayFrames(7000, 20, 30) == 0);
    assert(ThorFindDialogueCue(true, "Maps\\MD_USA02\\MD_USA02.map",
        "INTRO_Audio01 and Briefing", "AudioLine1_PlayTime", 3.5f));
    assert(!ThorFindDialogueCue(false, "md_usa02.map",
        "INTRO_Audio01 and Briefing", "AudioLine1_PlayTime", 3.5f));
    assert(!ThorFindDialogueCue(true, "md_usa02.map",
        "INTRO_Audio01 and Briefing", "AudioLine1_PlayTime", 6.0f));
    assert(!ThorFindDialogueCue(true, "other.map",
        "INTRO_Audio01 and Briefing", "AudioLine1_PlayTime", 3.5f));
    assert(!ThorFindDialogueCue(true, "md_usa01.map",
        "CINE_MOABCuToPlane", "CINE_MOABPanToBaseArea", .01f));
    assert(!ThorFindDialogueCue(true, "md_usa05.map",
        "INTRO_SatShot03", "INTRO_SatAnim04", 2.0f));
    for (const auto &cue : ThorDialogueCues) {
        assert(ThorFindDialogueCue(true, cue.map, cue.script, cue.timer, cue.authoredSeconds) == &cue);
        assert(ThorBriefingDelayFrames(0, cue.authoredSeconds, 30) == 0);
    }
    if (argc == 2) {
        std::ifstream input(argv[1]);
        assert(input.is_open());
        std::string line;
        unsigned checked = 0;
        while (std::getline(input, line)) {
            std::istringstream row(line);
            std::string map, script, timer, secondsText, speechText;
            assert(std::getline(row, map, '\t') && std::getline(row, script, '\t') &&
                   std::getline(row, timer, '\t') && std::getline(row, secondsText, '\t') &&
                   std::getline(row, speechText));
            const float seconds = std::stof(secondsText), speechMs = std::stof(speechText);
            assert(ThorFindDialogueCue(true, map.c_str(), script.c_str(), timer.c_str(), seconds));
            const int before = (int)std::ceil(seconds * 30);
            const int after = before + ThorBriefingDelayFrames(speechMs, seconds, 30);
            assert(after >= before);
            assert(after >= (int)std::ceil(speechMs * 30 / 1000) + 3);
            ++checked;
        }
        assert(checked == sizeof(ThorDialogueCues) / sizeof(ThorDialogueCues[0]));
        std::cout << "Actual retail speech durations fit all " << checked << " reviewed waits\n";
    }
    std::cout << "Briefing speech, cue identity, fade offsets and unchanged-timer checks passed\n";
}
