package com.generalsx.zerohour;

// GeneralsX @bugfix Codex 14/10/2026 A disabled contextual power cannot hide its usable shortcut from another owning unit.
final class ThorCommands {
    static boolean preferReplacement(boolean enabled, float recharge, boolean candidateEnabled, float candidateRecharge) {
        if (enabled != candidateEnabled) return candidateEnabled;
        return Float.isFinite(candidateRecharge) && (!Float.isFinite(recharge) || candidateRecharge > recharge);
    }
    private ThorCommands() {}
}
