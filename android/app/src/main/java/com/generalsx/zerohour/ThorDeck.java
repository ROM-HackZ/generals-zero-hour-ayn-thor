package com.generalsx.zerohour;
import android.content.Context;
import android.graphics.*;
import android.view.MotionEvent;
import android.view.View;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;

// GeneralsX @feature Codex 14/10/2026 Balanced command dashboard with separate production and spatial focus.
final class ThorDeck extends View {
    final ThorController control;
    final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    final ArrayList<RectF> commandRects=new ArrayList<>();
    final ArrayList<JSONObject> shown=new ArrayList<>();
    final RectF radarRect=new RectF(36,128,396,488),powersRect=new RectF(1020,120,1204,174);
    final RectF toolsRect=new RectF(812,120,1004,174);
    final RectF radarImageRect=new RectF();
    final float[] radarBounds={0,0,1,1};
    boolean radarTouchCaptured;
    int radarPointer=-1;
    final RectF previousRect=new RectF(1016,514,1072,546),nextRect=new RectF(1156,514,1212,546);
    final RectF queuePrevious=new RectF(1080,323,1132,353),queueNext=new RectF(1152,323,1204,353);
    final RectF placementConfirm=new RectF(852,874,1012,958),placementCancel=new RectF(1028,874,1192,958);
    float scale=1,offsetX,offsetY;
    Bitmap radar;
    ThorDeck(Context context,ThorController control) { super(context); this.control=control; setFocusable(false); }
    void updateRadar(int[] pixels) {
        if(pixels==null || pixels.length!=128*128) { radar=null; return; }
        if(radar==null) radar=Bitmap.createBitmap(128,128,Bitmap.Config.ARGB_8888);
        radar.setPixels(pixels,0,128,0,0,128,128);
    }
    void geometry() {
        scale=Math.min(getWidth()/1240f,getHeight()/1080f); offsetX=(getWidth()-1240*scale)/2; offsetY=(getHeight()-1080*scale)/2;
        JSONArray bounds=control.snapshot.optJSONArray("radarBounds");
        for(int i=0;i<4;i++) radarBounds[i]=bounds!=null?(float)bounds.optDouble(i,i<2?0:1):i<2?0:1;
        radarImageRect.set(radarRect.left+radarBounds[0]*radarRect.width(),radarRect.top+radarBounds[1]*radarRect.height(),
            radarRect.left+radarBounds[2]*radarRect.width(),radarRect.top+radarBounds[3]*radarRect.height());
        shown.clear(); commandRects.clear();
        ArrayList<JSONObject> rows=control.battle?control.commands:control.deckRows;
        int start=control.deckPage*ThorDeckLayout.COMMANDS_PER_PAGE;
        for(int i=0;i<ThorDeckLayout.COMMANDS_PER_PAGE && start+i<rows.size();++i) add(rows.get(start+i),ThorDeckLayout.command(i));
        if(!control.battle) return;
        int queueStart=control.queuePage*ThorDeckLayout.QUEUE_PER_PAGE;
        for(int i=0;i<ThorDeckLayout.QUEUE_PER_PAGE && queueStart+i<control.queueRows.size();++i) add(control.queueRows.get(queueStart+i),ThorDeckLayout.queue(i));
        if(control.powers!=null) { shown.add(control.powers); commandRects.add(new RectF(powersRect)); }
        if(control.toolsEntry!=null) { shown.add(control.toolsEntry); commandRects.add(new RectF(toolsRect)); }
    }
    void add(JSONObject row,float[] r) { shown.add(row); commandRects.add(new RectF(r[0],r[1],r[2],r[3])); }
    void navigate(int dx,int dy) {
        geometry(); int current=-1;
        for(int i=0;i<shown.size();++i) if(shown.get(i).optInt("id")==control.focusedId) current=i;
        float[][] rects=new float[commandRects.size()][];
        for(int i=0;i<rects.length;++i) { RectF r=commandRects.get(i); rects[i]=new float[]{r.left,r.top,r.right,r.bottom}; }
        // Horizontal movement stays in its strip; vertical movement crosses sections.
        if(current>=0 && dx!=0) for(int i=0;i<rects.length;++i) if(shown.get(i).optBoolean("queue")!=shown.get(current).optBoolean("queue") || (shown.get(i)==control.powers)!=(shown.get(current)==control.powers)) rects[i]=rects[current];
        int next=ThorDeckLayout.neighbor(rects,current,dx,dy);
        if(next==current && current>=0 && dx!=0) { changePage(shown.get(current).optBoolean("queue"),dx); return; }
        if(next>=0) control.focusedId=shown.get(next).optInt("id");
    }
    void changePage(boolean queue,int direction) {
        ArrayList<JSONObject> rows=queue?control.queueRows:(control.battle?control.commands:control.deckRows);
        int size=queue?ThorDeckLayout.QUEUE_PER_PAGE:ThorDeckLayout.COMMANDS_PER_PAGE,old=queue?control.queuePage:control.deckPage;
        int next=Math.max(0,Math.min(Math.max(0,(rows.size()-1)/size),old+direction));
        if(next==old) return;
        if(queue) control.queuePage=next; else control.deckPage=next;
        control.focusedId=rows.get(Math.min(rows.size()-1,next*size+(direction<0?size-1:0))).optInt("id"); invalidate();
    }
    protected void onDraw(Canvas canvas) {
        geometry(); canvas.drawColor(ThorUi.BACKGROUND); canvas.save(); canvas.translate(offsetX,offsetY); canvas.scale(scale,scale);
        Canvas c=canvas; ThorUi ui=control.ui;
        ui.icon(c,paint,control.snapshot.optString("factionIcon"),new RectF(24,24,88,88),true);
        ThorUi.wrap(c,paint,control.snapshot.optString("factionName","GENERALS"),new RectF(106,25,418,55),21,ui.accent,true,1,false);
        ThorUi.text(c,paint,control.battle?"TACTICAL COMMAND":"MISSION CONTROL",106,79,19,ThorUi.MUTED,false);
        ThorUi.text(c,paint,"$"+control.snapshot.optInt("money"),442,55,30,ThorUi.TEXT,true);
        boolean low=control.snapshot.optInt("power")<0;
        ThorUi.text(c,paint,"POWER  "+control.snapshot.optInt("powerProduced")+" / "+control.snapshot.optInt("powerConsumed"),664,51,21,low?0xffff958b:ThorUi.TEXT,true);
        ThorUi.text(c,paint,low?"LOW POWER":"Production / consumption",664,80,17,low?0xffff958b:ThorUi.MUTED,false);
        ThorUi.text(c,paint,"RANK "+control.snapshot.optInt("rank"),1020,55,24,ThorUi.TEXT,true);
        ThorUi.progress(c,paint,new RectF(1020,74,1204,83),(float)control.snapshot.optDouble("rankProgress"),ui.accent);
        paint.setColor(ui.accent); c.drawRect(24,99,1216,102,paint);
        ui.panel(c,paint,new RectF(24,116,408,500),control.mapMode);
        if(control.battle && radar!=null) {
            paint.setFilterBitmap(false); c.drawBitmap(radar,null,radarImageRect,paint);
            JSONArray view=control.snapshot.optJSONArray("radarView");
            if(view!=null && view.length()==8) {
                c.save(); c.clipRect(radarRect); Path path=new Path();
                for(int i=0;i<4;++i) { float x=radarRect.left+(float)view.optDouble(i*2)*radarRect.width(),y=radarRect.top+(float)view.optDouble(i*2+1)*radarRect.height(); if(i==0) path.moveTo(x,y); else path.lineTo(x,y); }
                path.close(); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2); paint.setColor(ThorUi.FOCUS); c.drawPath(path,paint); paint.setStyle(Paint.Style.FILL); c.restore();
            }
        } else {
            ThorUi.center(c,paint,control.battle?"RADAR UNAVAILABLE":"BATTLEFIELD OVERVIEW",radarRect.centerX(),294,21,ThorUi.MUTED,true);
            ThorUi.wrap(c,paint,control.battle?"Requires your faction's radar capability":"Your tactical map appears during a mission",new RectF(64,314,372,390),18,ThorUi.MUTED,false,3,true);
        }
        // GeneralsX @feature Codex 14/10/2026 Hero reticles and attack/beacon alerts are separate from the radar texture layers.
        JSONArray markers=control.snapshot.optJSONArray("radarMarkers");
        if(radar!=null && markers!=null) {
            c.save(); c.clipRect(radarRect);
            for(int i=0;i<markers.length();++i) {
                JSONObject marker=markers.optJSONObject(i); if(marker==null) continue;
                JSONArray points=marker.optJSONArray("points"); if(points==null || points.length()<2) continue;
                if(marker.optBoolean("hero")) {
                    float x=radarRect.left+(float)points.optDouble(0)*radarRect.width(),y=radarRect.top+(float)points.optDouble(1)*radarRect.height();
                    ui.icon(c,paint,"HeroReticle",new RectF(x-10,y-10,x+10,y+10),true);
                } else if(points.length()==6) {
                    Path path=new Path();
                    for(int j=0;j<3;++j) { float x=radarRect.left+(float)points.optDouble(j*2)*radarRect.width(),y=radarRect.top+(float)points.optDouble(j*2+1)*radarRect.height(); if(j==0) path.moveTo(x,y); else path.lineTo(x,y); }
                    path.close(); paint.setColor((int)marker.optLong("color")); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(2); c.drawPath(path,paint); paint.setStyle(Paint.Style.FILL); paint.setAlpha(255);
                }
            }
            c.restore();
        }
        if(control.mapMode && radar!=null) {
            float x=radarRect.left+control.mapX*radarRect.width(),y=radarRect.top+control.mapY*radarRect.height();
            paint.setColor(ThorUi.FOCUS); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(3); c.drawCircle(x,y,10,paint); c.drawLine(x-18,y,x+18,y,paint); c.drawLine(x,y-18,x,y+18,paint); paint.setStyle(Paint.Style.FILL);
        }
        ui.panel(c,paint,new RectF(432,188,1216,310),false);
        if(control.battle && control.snapshot.optInt("selected")>0) {
            ui.icon(c,paint,control.snapshot.optString("portrait"),new RectF(444,197,548,301),true);
            ThorUi.wrap(c,paint,control.snapshot.optString("selection"),new RectF(566,199,1192,254),25,ThorUi.TEXT,true,2,false);
            int health=control.snapshot.optInt("health"),max=control.snapshot.optInt("maxHealth");
            String summary=control.snapshot.optInt("selected")==1?health+" / "+max+" HP":control.snapshot.optInt("selected")+" selected · Lead unit shown";
            if(control.snapshot.optInt("selected")==1) {
                int capacity=control.snapshot.optInt("passengerCapacity");
                if(capacity>0) summary+=" · "+control.snapshot.optInt("passengers")+" / "+capacity+" aboard";
                int rank=control.snapshot.optInt("veterancy");
                if(rank>=1 && rank<=3) summary+=" · "+new String[]{"","Veteran","Elite","Heroic"}[rank];
            }
            ThorUi.wrap(c,paint,summary,new RectF(566,260,1192,283),18,ThorUi.MUTED,false,1,false);
            float fraction=max>0?health/(float)max:0;
            ThorUi.progress(c,paint,new RectF(566,291,1192,297),fraction,fraction>.5f?0xff83cfa2:fraction>.25f?ThorUi.FOCUS:0xffff958b);
        } else {
            ThorUi.text(c,paint,control.snapshot.has("observerName")?control.snapshot.optString("observerName"):control.battle?"NO SELECTION":"MENU NAVIGATION",456,238,25,ThorUi.TEXT,true);
            ThorUi.wrap(c,paint,control.snapshot.has("observerName")?"Observer view · Select Back to choose another player":control.battle?"A Select · Hold A for area selection · D-pad Up: builder":"D-pad / left stick Navigate · A Confirm · B Back",new RectF(456,256,1192,302),18,ThorUi.MUTED,false,2,false);
        }
        ThorUi.text(c,paint,"SELECTION",442,154,19,ThorUi.MUTED,true);
        if(control.powers!=null && control.battle) { ui.panel(c,paint,powersRect,control.deckFocus && control.focusedId==control.powers.optInt("id")); ThorUi.center(c,paint,(control.scienceMenu?"BACK · ":"POWERS · ")+control.snapshot.optInt("sciencePoints"),powersRect.centerX(),154,18,ui.accent,true); }
        if(control.toolsEntry!=null && control.battle) { ui.panel(c,paint,toolsRect,control.deckFocus && control.focusedId==-1000); ThorUi.center(c,paint,control.toolsMenu?"BACK":"TOOLS",toolsRect.centerX(),154,18,ui.accent,true); }
        ui.panel(c,paint,new RectF(432,320,1216,500),false);
        // GeneralsX @feature Codex 14/10/2026 Dedicated construction/timer contexts keep their information on the external deck.
        boolean construction=control.battle && control.snapshot.has("constructionProgress"),timer=control.battle && control.snapshot.has("timerSeconds");
        ThorUi.text(c,paint,construction?"CONSTRUCTION":timer?"REINFORCEMENTS":"PRODUCTION",442,346,19,ThorUi.MUTED,true);
        if(control.queueRows.isEmpty()) {
            if(construction || timer) {
                float progress=(float)control.snapshot.optDouble(construction?"constructionProgress":"timerProgress");
                int seconds=control.snapshot.optInt("timerSeconds");
                String label=construction?Math.round(progress*100)+"% complete":String.format(java.util.Locale.ROOT,"Next arrival: %d:%02d",seconds/60,seconds%60);
                ThorUi.text(c,paint,label,456,403,24,ThorUi.TEXT,true);
                ThorUi.progress(c,paint,new RectF(456,421,1192,433),progress,ui.accent);
                ThorUi.text(c,paint,construction?"Select Cancel Construction to discard this site":"The original timer controls the next arrival",456,468,18,ThorUi.MUTED,false);
            } else if(control.snapshot.has("observerStats")) {
                JSONArray stats=control.snapshot.optJSONArray("observerStats");
                ThorUi.text(c,paint,"Units: "+stats.optString(0)+" · Buildings: "+stats.optString(1),456,405,22,ThorUi.TEXT,false);
                ThorUi.text(c,paint,"Kills: "+stats.optString(2)+" · Losses: "+stats.optString(3),456,448,22,ThorUi.MUTED,false);
            } else ThorUi.text(c,paint,control.battle?"Queue empty":"Available during a mission",456,414,20,ThorUi.MUTED,false);
        }
        for(int i=0;i<shown.size();++i) {
            JSONObject row=shown.get(i); RectF r=commandRects.get(i); if(row==control.powers || r.equals(toolsRect)) continue;
            ui.panel(c,paint,r,control.deckFocus && control.focusedId==row.optInt("id"));
            if(row.optBoolean("queue")) {
                ui.icon(c,paint,row.optString("icon"),new RectF(r.centerX()-38,r.top+6,r.centerX()+38,r.top+82),true);
                ThorUi.center(c,paint,Math.round(row.optDouble("progress")*100)+"%",r.centerX(),r.top+99,17,ThorUi.TEXT,true);
                ThorUi.progress(c,paint,new RectF(r.left+8,r.bottom-6,r.right-8,r.bottom-2),(float)row.optDouble("progress"),ui.accent);
            } else {
                ui.icon(c,paint,row.optString("icon"),new RectF(r.left+10,r.top+8,r.left+74,r.top+72),row.optBoolean("enabled"));
                ThorUi.text(c,paint,ThorUi.cost(row),r.left+86,r.top+31,19,row.optBoolean("enabled")?ui.accent:ThorUi.MUTED,true);
                String status=ThorUi.badge(row);
                ThorUi.text(c,paint,status,r.left+86,r.top+56,15,ThorUi.MUTED,false);
                ThorUi.wrap(c,paint,ThorUi.name(row),new RectF(r.left+10,r.top+75,r.right-8,r.bottom-4),18,row.optBoolean("enabled")?ThorUi.TEXT:ThorUi.MUTED,false,2,false);
                if(row.has("recharge")) ThorUi.progress(c,paint,new RectF(r.left+8,r.bottom-4,r.right-8,r.bottom-1),(float)row.optDouble("recharge"),ui.accent);
            }
        }
        int count=control.battle?control.commands.size():control.deckRows.size();
        ThorUi.text(c,paint,control.battle?(control.toolsMenu?"TACTICAL TOOLS":control.scienceMenu?"GENERAL'S POWERS":"COMMANDS"):"MENU CONTROLS",24,539,19,ThorUi.MUTED,true);
        drawPages(c,control.deckPage,count,ThorDeckLayout.COMMANDS_PER_PAGE,previousRect,nextRect);
        drawPages(c,control.queuePage,control.queueRows.size(),ThorDeckLayout.QUEUE_PER_PAGE,queuePrevious,queueNext);
        if(count==0) ThorUi.text(c,paint,control.battle?"Select a unit or building to see its commands":"Waiting for the game menu…",48,620,22,ThorUi.MUTED,false);
        ui.panel(c,paint,new RectF(24,844,1216,986),false);
        JSONObject detail=control.highlighted();
        if(control.snapshot.optBoolean("placing")) {
            boolean rotating=control.snapshot.optInt("placementStage")==2;
            ThorUi.text(c,paint,control.snapshot.optString("placementName","BUILDING"),44,878,23,ThorUi.TEXT,true);
            int angle=(Math.round((float)control.snapshot.optDouble("placementAngle")*180/(float)Math.PI)%360+360)%360;
            ThorUi.wrap(c,paint,rotating?"Facing "+angle+"° · Left stick / finger chooses direction. A or Build confirms; B or Cancel discards.":"Move the preview to your site. A or Choose spot locks the position. Construction starts after facing is confirmed.",new RectF(44,900,824,977),19,ThorUi.MUTED,false,3,false);
            ui.panel(c,paint,placementConfirm,true); ui.panel(c,paint,placementCancel,false);
            ThorUi.center(c,paint,rotating?"BUILD":"CHOOSE SPOT",placementConfirm.centerX(),923,19,ui.accent,true);
            ThorUi.center(c,paint,"CANCEL",placementCancel.centerX(),923,19,ThorUi.TEXT,true);
        } else if(detail!=null) {
            ThorUi.text(c,paint,ThorUi.name(detail),44,875,23,ThorUi.TEXT,true);
            ThorUi.text(c,paint,ThorUi.cost(detail)+(ThorUi.cost(detail).isEmpty()?"":"  ·  ")+ThorUi.status(detail,control.snapshot),44,904,18,ui.accent,false);
            ThorUi.wrap(c,paint,detail.optString("description",detail.optBoolean("queue")?"Cancel this item through the original production queue.":""),new RectF(44,918,1192,978),18,ThorUi.MUTED,false,2,false);
        } else {
            ThorUi.text(c,paint,control.lb?"CONTROL GROUPS":control.mapMode?"TACTICAL MAP":"COMMAND AT A GLANCE",44,880,23,ThorUi.TEXT,true);
            ThorUi.wrap(c,paint,control.lb?"Left stick Choose · A Recall · X Assign · LT + X Add · Y Center":control.mapMode?"Left stick moves the map cursor · Y looks there · A orders there · B returns":"Hold RT Commands · Hold LB Groups · RB Radar · View focuses this deck",new RectF(44,906,1192,972),21,ThorUi.MUTED,false,2,false);
        }
        paint.setColor(ui.accent); c.drawRect(24,1005,30,1056,paint);
        String mode=!control.battle?"MENU":control.rt?"COMMAND WHEEL":control.lb?"GROUP WHEEL":control.deckFocus?"DECK":control.mapMode?"MAP":"BATTLE";
        ThorUi.text(c,paint,mode,44,1029,19,ui.accent,true);
        ThorUi.text(c,paint,control.deckFocus?"D-pad Navigate · A Activate · B / View Return · At grid edge: D-pad changes page":control.mapMode?"LS Aim · A Order · Y Look · B Return":!control.battle?"D-pad Navigate · A Confirm · B Back":"RT Commands · LB Groups · RB Map · View Deck",44,1056,18,ThorUi.MUTED,false);
        canvas.restore();
    }
    void drawPages(Canvas c,int page,int count,int size,RectF prev,RectF next) {
        if(count<=size) return;
        control.ui.panel(c,paint,prev,false); control.ui.panel(c,paint,next,false);
        ThorUi.center(c,paint,"‹",prev.centerX(),prev.bottom-5,26,page>0?ThorUi.TEXT:ThorUi.MUTED,true);
        ThorUi.center(c,paint,"›",next.centerX(),next.bottom-5,26,(page+1)*size<count?ThorUi.TEXT:ThorUi.MUTED,true);
        ThorUi.center(c,paint,(page+1)+"/"+((count+size-1)/size),(prev.right+next.left)/2,prev.bottom-7,17,ThorUi.MUTED,false);
    }
    public boolean onTouchEvent(MotionEvent event) {
        // GeneralsX @bugfix Codex 14/10/2026 Capture the minimap finger and pan on down/move/up.
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN) {
            control.touch(); geometry(); radarTouchCaptured=false; radarPointer=-1;
            if(control.battle && radar!=null) {
                float[] point=radarPoint(event.getX(),event.getY(),false);
                if(point!=null) { radarTouchCaptured=true; radarPointer=event.getPointerId(0); lookAtRadar(point); }
            }
            return true;
        }
        if(action==MotionEvent.ACTION_CANCEL) { radarTouchCaptured=false; radarPointer=-1; return true; }
        if(radarTouchCaptured) {
            int index=event.findPointerIndex(radarPointer);
            if(index>=0 && (action==MotionEvent.ACTION_MOVE || action==MotionEvent.ACTION_UP)) {
                geometry(); lookAtRadar(radarPoint(event.getX(index),event.getY(index),true));
            }
            if(action==MotionEvent.ACTION_UP) { radarTouchCaptured=false; radarPointer=-1; }
            else if(action==MotionEvent.ACTION_POINTER_UP && event.getPointerId(event.getActionIndex())==radarPointer) radarPointer=-1;
            return true;
        }
        if(action!=MotionEvent.ACTION_UP) return true;
        geometry(); float x=(event.getX()-offsetX)/scale,y=(event.getY()-offsetY)/scale;
        // GeneralsX @feature Codex 14/10/2026 Portrait touch mirrors L3 camera centring on the current selection.
        if(control.battle && x>=444 && x<=548 && y>=197 && y<=301 && control.snapshot.optInt("selected")>0) { ThorBridge.intent(4,0); return true; }
        if(control.snapshot.optBoolean("placing")) {
            if(placementCancel.contains(x,y)) { ThorBridge.intent(16,0); return true; }
            if(placementConfirm.contains(x,y)) { ThorBridge.intent(17,0); return true; }
        }
        if(previousRect.contains(x,y)) { changePage(false,-1); return true; } if(nextRect.contains(x,y)) { changePage(false,1); return true; }
        if(queuePrevious.contains(x,y)) { changePage(true,-1); return true; } if(queueNext.contains(x,y)) { changePage(true,1); return true; }
        for(int i=0;i<commandRects.size();++i) if(commandRects.get(i).contains(x,y)) { control.focusedId=shown.get(i).optInt("id"); control.activate(shown.get(i)); break; }
        return true;
    }
    float[] radarPoint(float x,float y,boolean clamp) {
        return ThorRadarTouch.point(x,y,scale,offsetX,offsetY,radarRect.left,radarRect.top,radarRect.right,radarRect.bottom,radarBounds,clamp);
    }
    void lookAtRadar(float[] point) {
        if(point==null || !control.ready || !control.battle || radar==null) return;
        control.mapX=point[0]; control.mapY=point[1];
        ThorBridge.nativeIntent(15,0,point[0],point[1]); invalidate();
    }
}
