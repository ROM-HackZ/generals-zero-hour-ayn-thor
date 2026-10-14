package com.generalsx.zerohour;

// GeneralsX @bugfix Codex 14/10/2026 Convert measured deck touches to the shared fitted radar coordinates.
final class ThorRadarTouch {
    static float[] point(float x,float y,float scale,float offsetX,float offsetY,
        float left,float top,float right,float bottom,float[] bounds,boolean clamp) {
        if(!Float.isFinite(x) || !Float.isFinite(y) || !(scale>0) || !(right>left) || !(bottom>top)) return null;
        float u=((x-offsetX)/scale-left)/(right-left),v=((y-offsetY)/scale-top)/(bottom-top);
        if(!clamp && (u<bounds[0] || u>bounds[2] || v<bounds[1] || v>bounds[3])) return null;
        return new float[]{Math.max(bounds[0],Math.min(bounds[2],u)),Math.max(bounds[1],Math.min(bounds[3],v))};
    }
    private ThorRadarTouch() {}
}
