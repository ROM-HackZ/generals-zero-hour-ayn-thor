package com.generalsx.zerohour;

// GeneralsX @feature Codex 14/10/2026 The UI never reads live C++ game objects.
final class ThorBridge {
    static native void nativeAxes(float lx, float ly, float rx, float ry, boolean precision, boolean queue);
    static native void nativeIntent(int type, int value, float x, float y);
    static native String nativeSnapshot();
    static native int[] nativeRadar();
    static native int[] nativeIcon(String key);
    static native void nativeText(String text);
    static native void nativeActivate(int id, String action);
    // GeneralsX @bugfix Codex 14/10/2026 Validate the snapshot's object/occupant/production binding before acting.
    static native void nativeActivateChecked(int id, String action, String activation);
    static native void nativeOptions(boolean snap, boolean externalDeck);
    static void intent(int type, int value) { nativeIntent(type, value, -1, -1); }
    private ThorBridge() {}
}
