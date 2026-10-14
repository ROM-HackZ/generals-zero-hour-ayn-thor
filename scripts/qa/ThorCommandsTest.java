package com.generalsx.zerohour;

// GeneralsX @test Codex 14/10/2026 Verify availability and cooldown precedence for duplicated powers.
public final class ThorCommandsTest {
    static void check(boolean ok, String reason) { if (!ok) throw new AssertionError(reason); }
    public static void main(String[] args) {
        check(ThorCommands.preferReplacement(false, 1, true, .2f), "Usable shortcut replaces disabled contextual action");
        check(!ThorCommands.preferReplacement(true, .2f, false, 1), "Disabled shortcut cannot shadow usable action");
        check(ThorCommands.preferReplacement(false, .1f, false, .8f), "Most-ready cooldown is shown");
        check(!ThorCommands.preferReplacement(true, 1, true, 1), "Ties preserve native slot order");
        check(!ThorCommands.preferReplacement(true, .5f, true, Float.NaN), "Invalid cooldown cannot shadow a valid action");
        System.out.println("Command availability and duplicate-power tests passed");
    }
}
