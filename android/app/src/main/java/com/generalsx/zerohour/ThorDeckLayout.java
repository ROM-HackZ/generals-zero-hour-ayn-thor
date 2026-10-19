package com.generalsx.zerohour;
// GeneralsX @feature Codex 14/10/2026 Geometry shared by drawing, touch and controller focus tests.
final class ThorDeckLayout {
    static final int COMMANDS_PER_PAGE=12, QUEUE_PER_PAGE=6;
    static float[] command(int slot) {
        float x=24+(slot%6)*200,y=558+(slot/6)*134;
        return new float[]{x,y,x+188,y+122};
    }
    // GeneralsX @bugfix Codex 18/10/2026 Menus use readable text buttons, separate from the battlefield icon grid.
    static float[] menu(int slot,int columns) {
        if(columns==1) { float y=206+slot*80; return new float[]{24,y,1216,y+70}; }
        float x=24+(slot%2)*608,y=206+(slot/2)*106;
        return new float[]{x,y,x+584,y+94};
    }
    // GeneralsX @bugfix Codex 19/10/2026 Vertical native menus stay vertical on both displays.
    static int menuColumns(float[][] nativeRects) {
        if(nativeRects.length<2) return 1;
        float left=Float.NEGATIVE_INFINITY,right=Float.POSITIVE_INFINITY;
        for(float[] r:nativeRects) { float mid=(r[0]+r[2])/2,quarter=(r[2]-r[0])/4; left=Math.max(left,mid-quarter); right=Math.min(right,mid+quarter); }
        return left<=right?1:2;
    }
    static int menuPageSize(int columns) { return columns==1?8:COMMANDS_PER_PAGE; }
    static float[] queue(int slot) { float x=452+slot*122; return new float[]{x,368,x+112,478}; }
    static int neighbor(float[][] rects,int current,int dx,int dy) {
        return neighbor(rects,null,current,dx,dy);
    }
    static int neighbor(float[][] rects,boolean[] eligible,int current,int dx,int dy) {
        if(rects.length==0) return -1;
        if(current<0 || current>=rects.length) {
            for(int i=0;i<rects.length;++i) if(eligible==null || eligible[i]) return i;
            return -1;
        }
        float[] origin=rects[current]; float cx=(origin[0]+origin[2])/2,cy=(origin[1]+origin[3])/2;
        double best=Double.MAX_VALUE; int found=current;
        for(int i=0;i<rects.length;++i) {
            if(i==current || (eligible!=null && !eligible[i])) continue;
            float[] r=rects[i]; float x=(r[0]+r[2])/2-cx,y=(r[1]+r[3])/2-cy,forward=x*dx+y*dy;
            if(forward<=1) continue;
            double score=forward+Math.abs(x*dy-y*dx)*3;
            if(score<best) { best=score; found=i; }
        }
        return found;
    }
    private ThorDeckLayout() {}
}
