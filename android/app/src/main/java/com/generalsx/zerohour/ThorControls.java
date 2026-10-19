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
    // GeneralsX @bugfix Codex 19/10/2026 Released taps are edges, never throttled by the previous tap's repeat timer.
    static final class Navigation {
        int dx, dy;
        long repeatAt;
        boolean step(int x,int y,long now,boolean repeat) {
            // Resolve diagonal hats to one direction, with vertical priority.
            x=y==0?Integer.signum(x):0; y=Integer.signum(y);
            boolean changed=x!=dx || y!=dy;
            dx=x; dy=y;
            if(x==0 && y==0) { repeatAt=0; return false; }
            if(changed) { repeatAt=now+400; return true; }
            if(repeat && now>=repeatAt) { repeatAt=now+140; return true; }
            return false;
        }
        void reset() { dx=dy=0; repeatAt=0; }
    }
    private ThorControls() {}
}
