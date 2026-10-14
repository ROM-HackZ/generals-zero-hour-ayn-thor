package com.generalsx.zerohour;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import org.json.JSONObject;

// GeneralsX @feature Codex 14/10/2026 Icon radial menus and shared controller feedback for the tactical display.
final class ThorOverlay extends View {
    final ThorController control;
    final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    boolean wheelVisible;
    long openedAt,focusedAt;
    int lastWedge=-1;
    ThorOverlay(Context context,ThorController control) { super(context); this.control=control; setFocusable(false); setClickable(false); }
    protected void onDraw(Canvas canvas) {
        if(!control.ready) return;
        float s=Math.min(getWidth()/1280f,getHeight()/720f),ox=(getWidth()-1280*s)/2,oy=(getHeight()-720*s)/2;
        if(control.battle && !control.rt && !control.lb) drawAim(canvas,s);
        if(!control.battle) drawNativeFocus(canvas,s);
        boolean visible=control.rt || control.lb;
        long now=android.os.SystemClock.uptimeMillis();
        if(visible && !wheelVisible) { openedAt=now; lastWedge=-1; }
        wheelVisible=visible;
        if(control.wedge!=lastWedge) { focusedAt=now; lastWedge=control.wedge; }
        canvas.save(); canvas.translate(ox,oy); canvas.scale(s,s);
        if(visible) drawWheel(canvas,now);
        if(control.typing) drawKeyboard(canvas);
        if(control.confirmation!=null) drawConfirmation(canvas);
        canvas.restore();
    }
    void drawAim(Canvas c,float s) {
        float x=getWidth()*(float)control.snapshot.optDouble("aimX",.5),y=getHeight()*(float)control.snapshot.optDouble("aimY",.5);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2*s); paint.setColor(ThorUi.FOCUS);
        c.drawCircle(x,y,10*s,paint); c.drawLine(x-19*s,y,x-13*s,y,paint); c.drawLine(x+13*s,y,x+19*s,y,paint); c.drawLine(x,y-19*s,x,y-13*s,paint); c.drawLine(x,y+13*s,x,y+19*s,paint);
        if(control.snapshot.has("boxX")) {
            float bx=control.snapshot.optInt("boxX")*getWidth()/(float)control.snapshot.optInt("width",getWidth()),by=control.snapshot.optInt("boxY")*getHeight()/(float)control.snapshot.optInt("height",getHeight());
            c.drawRect(Math.min(x,bx),Math.min(y,by),Math.max(x,bx),Math.max(y,by),paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }
    void drawNativeFocus(Canvas c,float s) {
        JSONObject w=control.find(control.focusedId); if(w==null) return;
        float sx=getWidth()/(float)control.snapshot.optInt("width",getWidth()),sy=getHeight()/(float)control.snapshot.optInt("height",getHeight());
        paint.setColor(ThorUi.FOCUS); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(3*s);
        c.drawRoundRect(new RectF(w.optInt("x")*sx,w.optInt("y")*sy,(w.optInt("x")+w.optInt("w"))*sx,(w.optInt("y")+w.optInt("h"))*sy),5*s,5*s,paint); paint.setStyle(Paint.Style.FILL);
    }
    Path segment(float cx,float cy,float inner,float outer,float start,float sweep) {
        Path path=new Path(); double a=Math.toRadians(start);
        path.moveTo(cx+(float)Math.cos(a)*outer,cy+(float)Math.sin(a)*outer);
        path.arcTo(new RectF(cx-outer,cy-outer,cx+outer,cy+outer),start,sweep);
        a=Math.toRadians(start+sweep); path.lineTo(cx+(float)Math.cos(a)*inner,cy+(float)Math.sin(a)*inner);
        path.arcTo(new RectF(cx-inner,cy-inner,cx+inner,cy+inner),start+sweep,-sweep); path.close(); return path;
    }
    void drawWheel(Canvas c,long now) {
        float cx=640,cy=332;
        paint.setColor(0x65000000); paint.setAlpha((int)(101*Math.min(1,(now-openedAt)/100f))); c.drawRect(0,0,1280,720,paint); paint.setAlpha(255);
        paint.setColor(0xf00c1219); c.drawCircle(cx,cy,308,paint);
        int wheelPage=control.lb?control.groupPage:control.page;
        int pages=control.lb?2:Math.max(1,(control.commands.size()+7)/8);
        for(int i=0;i<8;++i) {
            int index=wheelPage*8+i;
            boolean exists=control.lb?index<10:index<control.commands.size();
            JSONObject row=exists && !control.lb?control.commands.get(index):null;
            boolean active=exists && (control.lb || row.optBoolean("enabled")),focused=exists && i==control.wedge;
            Path path=segment(cx,cy,160,300,i*45-111,42);
            paint.setColor(!exists?0xff111b24:focused?0xff344655:0xff1c2b37); c.drawPath(path,paint);
            paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(focused?2.5f:1); paint.setColor(focused?ThorUi.FOCUS:ThorUi.LINE); c.drawPath(path,paint); paint.setStyle(Paint.Style.FILL);
            if(!exists) continue;
            double a=Math.toRadians(i*45-90); float x=cx+(float)Math.cos(a)*234,y=cy+(float)Math.sin(a)*234;
            if(control.lb) {
                int count=control.snapshot.optJSONArray("groups")==null?0:control.snapshot.optJSONArray("groups").optInt(index);
                ThorUi.center(c,paint,String.valueOf(index+1),x,y+9,39,count>0?ThorUi.TEXT:ThorUi.MUTED,true);
                ThorUi.center(c,paint,count>0?count+(count==1?" unit":" units"):"Empty",x,y+42,17,count>0?control.ui.accent:ThorUi.MUTED,false);
            } else {
                float pulse=focused?1+Math.max(0,1-(now-focusedAt)/100f)*.06f:1;
                control.ui.icon(c,paint,row.optString("icon"),new RectF(x-34*pulse,y-43*pulse,x+34*pulse,y+25*pulse),active);
                ThorUi.wrap(c,paint,ThorUi.name(row),new RectF(x-79,y+30,x+79,y+76),16,active?ThorUi.TEXT:ThorUi.MUTED,false,2,true);
                if(row.has("recharge")) ThorUi.progress(c,paint,new RectF(x-32,y+23,x+32,y+27),(float)row.optDouble("recharge"),control.ui.accent);
            }
        }
        paint.setColor(0xff101922); c.drawCircle(cx,cy,153,paint);
        paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(1); paint.setColor(control.ui.accent); c.drawCircle(cx,cy,152,paint); paint.setStyle(Paint.Style.FILL);
        ThorUi.center(c,paint,control.lb?"CONTROL GROUPS":control.scienceMenu?"GENERAL'S POWERS":"COMMANDS",cx,cy-104,17,control.ui.accent,true);
        JSONObject chosen=control.highlighted();
        if(control.lb) {
            int index=ThorControls.group(wheelPage,control.wedge);
            int count=index<0 || control.snapshot.optJSONArray("groups")==null?0:control.snapshot.optJSONArray("groups").optInt(index);
            ThorUi.center(c,paint,index<0?"Choose a group":"Group "+(index+1),cx,cy-46,26,ThorUi.TEXT,true);
            ThorUi.center(c,paint,index<0?"Left stick to highlight":count==0?"Empty group":count+(count==1?" unit assigned":" units assigned"),cx,cy-10,18,ThorUi.MUTED,false);
            ThorUi.center(c,paint,"A Recall · X Assign",cx,cy+33,17,ThorUi.TEXT,false);
            ThorUi.center(c,paint,"LT + X Add · Y Center",cx,cy+63,16,ThorUi.MUTED,false);
        } else if(chosen!=null) {
            ThorUi.wrap(c,paint,ThorUi.name(chosen),new RectF(cx-129,cy-82,cx+129,cy-7),25,ThorUi.TEXT,true,2,true);
            ThorUi.center(c,paint,ThorUi.cost(chosen),cx,cy+2,21,control.ui.accent,true);
            ThorUi.center(c,paint,ThorUi.status(chosen,control.snapshot),cx,cy+29,16,ThorUi.MUTED,false);
            ThorUi.wrap(c,paint,chosen.optString("description").replace('\n',' '),new RectF(cx-125,cy+44,cx+125,cy+113),16,ThorUi.MUTED,false,3,true);
        } else {
            ThorUi.center(c,paint,control.commands.isEmpty()?"No commands":"Choose a command",cx,cy-37,24,ThorUi.TEXT,true);
            ThorUi.wrap(c,paint,control.commands.isEmpty()?"Select a unit or building first":"Move the left stick to highlight an action",new RectF(cx-122,cy-9,cx+122,cy+80),18,ThorUi.MUTED,false,3,true);
        }
        ThorUi.center(c,paint,(wheelPage+1)+" / "+pages,cx,cy+131,15,ThorUi.MUTED,false);
        ThorUi.center(c,paint,control.lb?"A Recall   ·   X Assign   ·   LT + X Add   ·   Y Center":"A Confirm   ·   Hold Y Details   ·   B Close",cx,677,20,ThorUi.TEXT,false);
        ThorUi.center(c,paint,"D-pad ‹ / › Page   ·   Left stick Choose",cx,707,17,ThorUi.MUTED,false);
        if(control.rt && control.description && chosen!=null) {
            RectF card=new RectF(245,160,1035,650); control.ui.panel(c,paint,card,true);
            ThorUi.text(c,paint,ThorUi.name(chosen),267,195,25,ThorUi.TEXT,true);
            ThorUi.text(c,paint,ThorUi.cost(chosen)+"   "+ThorUi.status(chosen,control.snapshot),267,227,18,control.ui.accent,false);
            java.util.ArrayList<String> lines=ThorUi.lines(paint,chosen.optString("description"),746,20);
            int start=Math.min(control.descriptionScroll,Math.max(0,lines.size()-13));
            for(int i=0;i<13 && start+i<lines.size();++i) ThorUi.text(c,paint,lines.get(start+i),267,270+i*26,20,ThorUi.TEXT,false);
            ThorUi.text(c,paint,lines.size()>13?"D-pad ↑ / ↓ Scroll · Release Y to return":"Release Y to return",267,629,17,ThorUi.MUTED,false);
        }
    }
    void drawKeyboard(Canvas c) {
        RectF r=new RectF(230,135,1050,623); control.ui.panel(c,paint,r,true);
        ThorUi.text(c,paint,"TEXT ENTRY",258,175,20,control.ui.accent,true);
        ThorUi.wrap(c,paint,control.entered+"_",new RectF(258,195,1022,250),24,ThorUi.TEXT,true,1,false);
        for(int i=0;i<control.keys.length();++i) {
            float x=258+(i%10)*76,y=274+(i/10)*66;
            RectF key=new RectF(x,y,x+65,y+54); control.ui.panel(c,paint,key,i==control.keyboardIndex);
            String label=control.keys.charAt(i)==' '?"␣":String.valueOf(control.keys.charAt(i));
            ThorUi.center(c,paint,label,key.centerX(),y+36,24,ThorUi.TEXT,true);
        }
        ThorUi.center(c,paint,"D-pad Choose · A Type · X Delete · Y Done · B Cancel",640,599,19,ThorUi.MUTED,false);
    }
    void drawConfirmation(Canvas c) {
        paint.setColor(0x85000000); c.drawRect(0,0,1280,720,paint);
        control.ui.panel(c,paint,new RectF(360,240,920,480),true);
        control.ui.icon(c,paint,control.confirmation.optString("icon"),new RectF(392,276,472,356),true);
        ThorUi.wrap(c,paint,"Confirm "+ThorUi.name(control.confirmation)+"?",new RectF(492,282,885,365),28,ThorUi.TEXT,true,2,false);
        ThorUi.center(c,paint,"A Confirm   ·   B Cancel",640,437,23,ThorUi.MUTED,false);
    }
}
