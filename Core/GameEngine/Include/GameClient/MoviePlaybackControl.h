#pragma once
// GeneralsX @bugfix Codex 19/10/2026 Controller skips belong to one live video, never to a later menu.
#include <atomic>
#include <cstdint>

class MoviePlaybackControl {
    // One atomic word keeps the session, owner and request coherent across JNI and the engine thread.
    enum : std::uint32_t { Active = 1, Blocking = 2, Requested = 4, Step = 8 };
    std::atomic<std::uint32_t> state{0};
public:
    std::uint32_t begin(bool blocking) {
        const auto next = ((state.load() & ~(Step - 1)) + Step) & 0x7ffffff8u;
        state.store(next | Active | (blocking ? Blocking : 0));
        return next;
    }
    void end(std::uint32_t session) {
        auto current = state.load();
        while ((current & ~(Step - 1)) == session && (current & Active)) {
            if (state.compare_exchange_weak(current, session)) return;
        }
    }
    std::uint32_t session() const {
        const auto current = state.load();
        return current & Active ? current & ~(Step - 1) : 0;
    }
    void request(std::uint32_t session) {
        if (!session) return;
        auto current = state.load();
        while ((current & ~(Step - 1)) == session && (current & Active)) {
            if (state.compare_exchange_weak(current, current | Requested)) return;
        }
    }
    bool consume(bool blocking) {
        auto current = state.load();
        while ((current & (Active | Requested)) == (Active | Requested) && bool(current & Blocking) == blocking) {
            if (state.compare_exchange_weak(current, current & ~Requested)) return true;
        }
        return false;
    }
};
inline MoviePlaybackControl &moviePlaybackControl() {
    static MoviePlaybackControl control;
    return control;
}
class BlockingMovieInput {
    std::uint32_t session;
public:
    BlockingMovieInput() : session(moviePlaybackControl().begin(true)) {}
    ~BlockingMovieInput() { moviePlaybackControl().end(session); }
    BlockingMovieInput(const BlockingMovieInput &) = delete;
    BlockingMovieInput &operator=(const BlockingMovieInput &) = delete;
};
