package com.generalsx.zerohour;
// GeneralsX @feature Codex 14/10/2026 Geometry shared by drawing, touch and controller focus tests.
final class ThorDeckLayout {
    static final int COMMANDS_PER_PAGE=12, QUEUE_PER_PAGE=6;
    static float[] command(int slot) {
        float x=24+(slot%6)*200,y=558+(slot/6)*134;
        return new float[]{x,y,x+188,y+122};
    }
    static float[] queue(int slot) { float x=452+slot*122; return new float[]{x,368,x+112,478}; }
    static int neighbor(float[][] rects,int current,int dx,int dy) {
        if(rects.length==0) return -1;
        if(current<0 || current>=rects.length) return 0;
        float[] origin=rects[current]; float cx=(origin[0]+origin[2])/2,cy=(origin[1]+origin[3])/2;
        double best=Double.MAX_VALUE; int found=current;
        for(int i=0;i<rects.length;++i) {
            if(i==current) continue;
            float[] r=rects[i]; float x=(r[0]+r[2])/2-cx,y=(r[1]+r[3])/2-cy,forward=x*dx+y*dy;
            if(forward<=1) continue;
            double score=forward+Math.abs(x*dy-y*dx)*3;
            if(score<best) { best=score; found=i; }
        }
        return found;
    }
    private ThorDeckLayout() {}
}
