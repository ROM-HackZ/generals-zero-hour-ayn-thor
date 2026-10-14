package com.generalsx.zerohour;
// GeneralsX @bugfix Codex 14/10/2026 Measured display scaling and captured minimap drag boundaries.
public final class ThorRadarTouchTest {
    static void close(float actual,float expected) { if(Math.abs(actual-expected)>.0001) throw new AssertionError(actual+" != "+expected); }
    public static void main(String[] args) {
        float[] bounds={0,.25f,1,.75f};
        float[] p=ThorRadarTouch.point(236,328,.5f,128,174,36,128,396,488,bounds,false);
        close(p[0],.5f); close(p[1],.5f);
        if(ThorRadarTouch.point(236,254,.5f,128,174,36,128,396,488,bounds,false)!=null) throw new AssertionError("Letterbox tap must not jump camera");
        p=ThorRadarTouch.point(-100,999,.5f,128,174,36,128,396,488,bounds,true);
        close(p[0],0); close(p[1],.75f);
        if(ThorRadarTouch.point(Float.NaN,0,1,0,0,36,128,396,488,bounds,true)!=null) throw new AssertionError("Invalid touch must not pan");
        System.out.println("Minimap touch scaling, letterbox and captured-drag tests passed");
    }
}
