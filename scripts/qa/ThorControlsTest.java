package com.generalsx.zerohour;
public final class ThorControlsTest {
    static void check(boolean value, String reason) { if (!value) throw new AssertionError(reason); }
    public static void main(String[] args) {
        check(ThorControls.deadZone(.14f, .15f) == 0, "Idle sticks cannot pan");
        check(ThorControls.deadZone(Float.NaN, .15f) == 0, "Invalid input cannot pan");
        check(ThorControls.deadZone(2, .15f) == 1, "Overrange axes clamp");
        check(ThorControls.deadZone(-1, .15f) == -1, "Negative axis preserved");
        check(ThorControls.wedge(0, 0) == -1, "Resting stick cannot choose a command");
        check(ThorControls.wedge(0, -1) == 0, "North wedge");
        check(ThorControls.wedge(1, 0) == 2, "East wedge");
        check(ThorControls.wedge(0, 1) == 4, "South wedge");
        check(ThorControls.wedge(-1, 0) == 6, "West wedge");
        for (int i = 0; i < 8; i++) check(ThorControls.group(0, i) == i, "First group page");
        check(ThorControls.group(1, 0) == 8 && ThorControls.group(1, 1) == 9, "Last two groups");
        for (int i = 2; i < 8; i++) check(ThorControls.group(1, i) == -1, "Empty wedges cannot alias group ten");
        System.out.println("Controller normalization and radial boundary tests passed");
    }
}
