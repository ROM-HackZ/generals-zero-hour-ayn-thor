// GeneralsX @test Codex 19/10/2026 Reproduce delayed controller skips crossing video/load boundaries.
#include "GameClient/MoviePlaybackControl.h"
#include <cassert>
#include <iostream>
#include <thread>
int main() {
    MoviePlaybackControl control;
    control.request(0); assert(!control.consume(true));
    auto first = control.begin(true);
    control.request(first); control.request(first);
    assert(!control.consume(false)); assert(control.consume(true)); assert(!control.consume(true));
    control.request(first); control.end(first);
    assert(!control.session()); assert(!control.consume(true));
    auto second = control.begin(false);
    assert(first != second);
    control.request(first); assert(!control.consume(false));
    control.end(first); assert(control.session() == second);
    control.request(second); assert(control.consume(false));
    control.end(second);
    // Race a JNI request against ending playback; no request survives in a later video.
    for (int i=0;i<1000;++i) {
        auto old = control.begin(true);
        std::thread input([&]{control.request(old);});
        control.end(old); auto next = control.begin(true); input.join();
        assert(!control.consume(true)); assert(control.session() == next); control.end(next);
    }
    std::cout << "Movie playback session and concurrent skip checks passed\n";
}
