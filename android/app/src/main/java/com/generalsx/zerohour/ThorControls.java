package com.generalsx.zerohour;
// GeneralsX @feature Codex 14/10/2026 Controller normalization shared with host tests.
final class ThorControls {
    static float deadZone(float input, float deadZone) {
        if (!Float.isFinite(input)) return 0;
        float value = Math.max(-1, Math.min(1, input));
        return Math.abs(value) <= deadZone ? 0 : Math.signum(value) * (Math.abs(value) - deadZone) / (1 - deadZone);
    }
    static int wedge(float x, float y) {
        if (!Float.isFinite(x) || !Float.isFinite(y) || Math.max(Math.abs(x), Math.abs(y)) <= .35f) return -1;
        return ((int)Math.round((Math.atan2(y, x) + Math.PI / 2) / (Math.PI / 4)) + 8) % 8;
    }
    static int group(int page, int wedge) {
        int index = page * 8 + wedge;
        return page < 0 || page > 1 || wedge < 0 || wedge > 7 || index >= 10 ? -1 : index;
    }
    private ThorControls() {}
}
