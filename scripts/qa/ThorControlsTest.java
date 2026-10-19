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
        ThorControls.Navigation nav=new ThorControls.Navigation();
        check(nav.step(0,1,1000,true),"First hat press moves immediately");
        check(!nav.step(0,0,1020,true),"Release does not move focus");
        check(nav.step(0,1,1040,true),"A quick second tap must not be dropped");
        check(!nav.step(0,1,1100,true),"Unrelated joystick traffic cannot repeat a held hat");
        check(!nav.step(0,1,1439,true) && nav.step(0,1,1440,true),"Hold has a predictable initial delay");
        check(!nav.step(0,1,1500,true) && nav.step(0,1,1580,true),"Repeat follows the clock");
        check(nav.step(0,-1,1590,true),"Direction reversal moves immediately");
        nav.reset(); check(nav.step(1,0,1600,false),"Resume accepts a fresh direction");
        check(!nav.step(1,0,2500,false),"Battlefield shortcuts do not auto-repeat");
        System.out.println("Controller normalization and radial boundary tests passed");
    }
}
