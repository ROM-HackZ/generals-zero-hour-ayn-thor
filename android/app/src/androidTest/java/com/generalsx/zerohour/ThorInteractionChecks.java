package com.generalsx.zerohour;

import android.app.Instrumentation;
import android.content.Intent;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.View;
import org.json.JSONArray;
import org.json.JSONObject;

// GeneralsX @test Codex 14/10/2026 Exercise the actual SDL/engine and deck with a saved skirmish.
// This is a separate instrumentation APK. Save your match before running; it loads the newest save.
public final class ThorInteractionChecks extends Instrumentation {
    GeneralsZHActivity activity;
    long touchDown;
    JSONObject state() throws Exception { return new JSONObject(ThorBridge.nativeSnapshot()); }
    interface Condition { boolean matches(JSONObject s) throws Exception; }
    JSONObject await(Condition condition,String reason) throws Exception {
        long end=SystemClock.uptimeMillis()+45000;
        while(SystemClock.uptimeMillis()<end) {
            JSONObject s=state(); if(condition.matches(s)) return s;
            SystemClock.sleep(50);
        }
        throw new AssertionError(reason+" · state="+state());
    }
    void check(boolean ok,String reason) { if(!ok) throw new AssertionError(reason); }
    void report(String text) { Bundle b=new Bundle(); b.putString("stream",text+"\n"); sendStatus(0,b); }
    JSONObject widget(JSONObject s,String label) {
        JSONArray rows=s.optJSONArray("widgets"); if(rows==null) return null;
        for(int i=0;i<rows.length();i++) {
            JSONObject row=rows.optJSONObject(i);
            if(row.optBoolean("enabled") && row.optString("label").replace("&","").equalsIgnoreCase(label)) return row;
        }
        return null;
    }
    void activate(String label) throws Exception {
        JSONObject s=await(value->widget(value,label)!=null,"Missing "+label);
        JSONObject row=widget(s,label);
        float x=(row.optInt("x")+row.optInt("w")*.5f)/s.optInt("width"),y=(row.optInt("y")+row.optInt("h")*.5f)/s.optInt("height");
        finger(MotionEvent.ACTION_DOWN,x,y); finger(MotionEvent.ACTION_UP,x,y); SystemClock.sleep(2000);
    }
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    @Override public void onStart() {
        Bundle result=new Bundle();
        try {
            activity=(GeneralsZHActivity)startActivitySync(new Intent(getTargetContext(),GeneralsZHActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            // The first snapshot appears after SDL has loaded libmain. Do not call JNI early.
            long end=SystemClock.uptimeMillis()+60000;
            while(SystemClock.uptimeMillis()<end) {
                try { if(state().optBoolean("ready")) break; } catch(UnsatisfiedLinkError ignored) {}
                SystemClock.sleep(200);
            }
            for(int i=0;i<45;i++) {
                if(widget(state(),"LOAD")!=null) break;
                ThorBridge.intent(11,0); SystemClock.sleep(400);
            }
            SystemClock.sleep(2000);
            activate("LOAD"); activate("LOAD GAME");
            await(s->{ JSONArray rows=s.optJSONArray("widgets"); if(rows==null) return false; for(int i=0;i<rows.length();i++) if((rows.optJSONObject(i).optInt("style")&32)!=0) return true; return false; },"Save list missing");
            activate("LOAD GAME");
            await(s->s.optBoolean("battle"),"Saved match did not load");
            report("Saved skirmish loaded.");
            selectionAndCamera();
            minimap();
            placement();
            result.putString("stream","Thor interaction checks passed. Reload your saved match to discard test construction.\n");
            finish(0,result);
        } catch(Throwable error) {
            result.putString("stream","FAIL: "+error+"\n"); finish(1,result);
        }
    }
    void dispatch(View view,int action,float[] coordinates) {
        int count=coordinates.length/2;
        MotionEvent.PointerProperties[] properties=new MotionEvent.PointerProperties[count];
        MotionEvent.PointerCoords[] coords=new MotionEvent.PointerCoords[count];
        for(int i=0;i<count;i++) {
            properties[i]=new MotionEvent.PointerProperties(); properties[i].id=i; properties[i].toolType=MotionEvent.TOOL_TYPE_FINGER;
            coords[i]=new MotionEvent.PointerCoords(); coords[i].x=coordinates[i*2]; coords[i].y=coordinates[i*2+1]; coords[i].pressure=1; coords[i].size=1;
        }
        if(action==MotionEvent.ACTION_DOWN) touchDown=SystemClock.uptimeMillis();
        MotionEvent event=MotionEvent.obtain(touchDown,SystemClock.uptimeMillis(),action,count,properties,coords,0,0,1,1,0,0,InputDevice.SOURCE_TOUCHSCREEN,0);
        runOnMainSync(()->{ if(view!=null) view.onTouchEvent(event); else activity.dispatchTouchEvent(event); });
        event.recycle(); SystemClock.sleep(60);
    }
    void finger(int action,float... normalized) {
        float[] pixels=normalized.clone();
        for(int i=0;i<pixels.length;i++) pixels[i]*=i%2==0?activity.getWindow().getDecorView().getWidth():activity.getWindow().getDecorView().getHeight();
        dispatch(null,action,pixels);
    }
    void twoStart(float x1,float y1,float x2,float y2) {
        finger(MotionEvent.ACTION_DOWN,x1,y1);
        finger(MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),x1,y1,x2,y2);
    }
    void twoEnd(float x1,float y1,float x2,float y2) {
        finger(MotionEvent.ACTION_POINTER_UP|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),x1,y1,x2,y2);
        finger(MotionEvent.ACTION_UP,x1,y1);
    }
    double distance(JSONObject a,JSONObject b) { return Math.hypot(a.optDouble("cameraX")-b.optDouble("cameraX"),a.optDouble("cameraY")-b.optDouble("cameraY")); }
    void selectionAndCamera() throws Exception {
        JSONObject before=state();
        finger(MotionEvent.ACTION_DOWN,.20f,.20f);
        finger(MotionEvent.ACTION_MOVE,.78f,.78f);
        await(s->s.optBoolean("selecting"),"A quick one-finger drag did not draw a selection rectangle");
        check(distance(before,state())<2,"One-finger selection moved the camera");
        finger(MotionEvent.ACTION_UP,.78f,.78f);
        await(s->!s.optBoolean("selecting") && s.optInt("selected")>0,"Selection did not commit on release");
        report("One-finger rectangle selection passed; camera stayed fixed.");
        before=state(); int selected=before.optInt("selected");
        finger(MotionEvent.ACTION_DOWN,.30f,.30f); finger(MotionEvent.ACTION_MOVE,.55f,.50f);
        await(s->s.optBoolean("selecting"),"Second selection did not start");
        finger(MotionEvent.ACTION_POINTER_DOWN|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),.55f,.50f,.75f,.50f);
        await(s->!s.optBoolean("selecting"),"Second finger did not cancel selection");
        check(state().optInt("selected")==selected,"Adding a finger committed a selection");
        for(int i=1;i<=5;i++) finger(MotionEvent.ACTION_MOVE,.55f+i*.025f,.50f+i*.025f,.75f+i*.025f,.50f+i*.025f);
        check(distance(before,state())>10,"Two-finger drag did not pan");
        finger(MotionEvent.ACTION_POINTER_UP|(1<<MotionEvent.ACTION_POINTER_INDEX_SHIFT),.675f,.625f,.875f,.625f);
        SystemClock.sleep(150); JSONObject remainder=state();
        finger(MotionEvent.ACTION_MOVE,.55f,.55f);
        check(!state().optBoolean("selecting") && distance(remainder,state())<2,"Remaining camera finger panned or started a selection");
        finger(MotionEvent.ACTION_UP,.55f,.55f);
        report("Second-finger cancellation, two-finger pan and remaining-finger guard passed.");
        before=state();
        twoStart(.40f,.50f,.60f,.50f);
        for(int i=1;i<=5;i++) finger(MotionEvent.ACTION_MOVE,.40f-i*.02f,.50f,.60f+i*.02f,.50f);
        twoEnd(.30f,.50f,.70f,.50f);
        check(Math.abs(before.optDouble("cameraHeight")-state().optDouble("cameraHeight"))>5,"Pinch did not zoom");
        before=state();
        twoStart(.35f,.50f,.65f,.50f);
        for(int i=1;i<=8;i++) finger(MotionEvent.ACTION_MOVE,.35f,.50f-i*.028f,.65f,.50f+i*.028f);
        twoEnd(.35f,.276f,.65f,.724f);
        check(Math.abs(before.optDouble("cameraAngle")-state().optDouble("cameraAngle"))>.1,"Twist did not rotate");
        report("Pinch zoom and two-finger twist passed.");
    }
    ThorDeck deck() throws Exception {
        java.lang.reflect.Field field=GeneralsZHActivity.class.getDeclaredField("thorController"); field.setAccessible(true);
        ThorController control=(ThorController)field.get(activity);
        check(control.deck!=null,"The second-screen deck is missing"); return control.deck;
    }
    float[] mapPixel(ThorDeck deck,float u,float v) {
        return new float[]{deck.offsetX+deck.scale*(deck.radarRect.left+u*deck.radarRect.width()),
            deck.offsetY+deck.scale*(deck.radarRect.top+v*deck.radarRect.height())};
    }
    void mapTarget(JSONObject s,float ratioX,float ratioY) throws Exception {
        double expectedX=(int)(ratioX*128)*s.optDouble("radarWorldWidth")/128;
        double expectedY=(int)((1-ratioY)*128)*s.optDouble("radarWorldHeight")/128;
        await(value->Math.abs(value.optDouble("cameraX")-expectedX)<25 && Math.abs(value.optDouble("cameraY")-expectedY)<25,"Minimap target did not match the touched world cell");
    }
    void minimap() throws Exception {
        JSONObject s=await(value->value.has("radarBounds"),"No radar in saved match");
        ThorDeck d=deck(); runOnMainSync(d::geometry);
        JSONArray bounds=s.getJSONArray("radarBounds");
        float left=(float)bounds.getDouble(0),top=(float)bounds.getDouble(1),width=(float)(bounds.getDouble(2)-left),height=(float)(bounds.getDouble(3)-top);
        float[] p=mapPixel(d,left+width*.35f,top+height*.35f);
        dispatch(d,MotionEvent.ACTION_DOWN,p); dispatch(d,MotionEvent.ACTION_UP,p); mapTarget(s,.35f,.35f);
        p=mapPixel(d,left+width*.65f,top+height*.65f);
        dispatch(d,MotionEvent.ACTION_DOWN,p); dispatch(d,MotionEvent.ACTION_UP,p); mapTarget(s,.65f,.65f);
        p=mapPixel(d,left+width*.35f,top+height*.35f); dispatch(d,MotionEvent.ACTION_DOWN,p);
        p=mapPixel(d,left+width*.50f,top+height*.50f); dispatch(d,MotionEvent.ACTION_MOVE,p); mapTarget(s,.50f,.50f);
        p=mapPixel(d,left+width*.65f,top+height*.65f); dispatch(d,MotionEvent.ACTION_MOVE,p); mapTarget(s,.65f,.65f);
        dispatch(d,MotionEvent.ACTION_UP,p);
        report("Minimap taps and held-finger drag matched their world coordinates.");
    }
    JSONObject buildButton(JSONObject s) {
        JSONArray rows=s.optJSONArray("widgets"); if(rows==null) return null;
        for(int i=0;i<rows.length();i++) {
            JSONObject row=rows.optJSONObject(i); String label=row.optString("label").toLowerCase(java.util.Locale.ROOT);
            if(row.optBoolean("enabled") && row.optBoolean("command") && (label.contains("reactor") || label.contains("powerplant") || label.contains("power plant"))) return row;
        } return null;
    }
    void chooseBuilding() throws Exception {
        if(buildButton(state())==null) { ThorBridge.intent(5,0); SystemClock.sleep(500); }
        JSONObject s=await(value->buildButton(value)!=null,"No builder/power-plant command");
        JSONObject row=buildButton(s); ThorBridge.nativeActivate(row.optInt("id"),row.optString("action"));
        await(value->value.optInt("placementStage")==1,"Building did not enter position stage");
    }
    void placement() throws Exception {
        ThorBridge.intent(5,1); SystemClock.sleep(200); ThorBridge.intent(4,0); SystemClock.sleep(400);
        chooseBuilding(); int money=state().optInt("money");
        ThorBridge.nativeIntent(0,0,.78f,.60f);
        JSONObject locked=await(s->s.optInt("placementStage")==2,"First A did not enter rotation");
        check(locked.optInt("money")==money,"First A charged money");
        ThorBridge.nativeAxes(0,0,0,0,false,false); SystemClock.sleep(120);
        ThorBridge.nativeAxes(1,0,0,0,false,false); SystemClock.sleep(180);
        ThorBridge.nativeAxes(0,0,0,0,false,false);
        check(Math.abs(locked.optDouble("placementAngle")-state().optDouble("placementAngle"))>.2,"Left stick did not rotate the preview");
        check(distance(locked,state())<2,"Left stick panned during rotation");
        ThorBridge.nativeAxes(0,0,.5f,.5f,false,false); SystemClock.sleep(220); ThorBridge.nativeAxes(0,0,0,0,false,false);
        JSONObject moved=state();
        check(Math.abs(moved.optDouble("placementX")-locked.optDouble("placementX"))<.1 && Math.abs(moved.optDouble("placementY")-locked.optDouble("placementY"))<.1,"Camera movement shifted the locked build site");
        ThorBridge.intent(1,0); await(s->!s.optBoolean("placing"),"B did not cancel rotation");
        check(state().optInt("money")==money,"Cancelled preview spent money");
        report("First-A lock, analog facing, fixed world site and B cancellation passed.");
        // Keep the construction check away from the owner's existing reactor sites below the command center.
        chooseBuilding(); ThorBridge.nativeIntent(0,0,.78f,.35f);
        await(s->s.optInt("placementStage")==2,"Second preview failed");
        ThorBridge.nativeIntent(0,0,.78f,.35f);
        JSONObject after=await(s->s.optInt("money")<money,"Final confirmation did not start construction");
        check(after.optInt("money")<money,"Final A did not start construction");
        report("Final-A confirmation started construction through the original validator.");
        constructionCancellation(money);
    }
    // GeneralsX @test Codex 14/10/2026 A dedicated construction button must reach both command surfaces and refund through retail logic.
    void constructionCancellation(int moneyBefore) throws Exception {
        ThorBridge.intent(3,0); // Stop the selected dozer so construction cannot finish during the check.
        await(s->!s.optBoolean("placing"),"Construction preview did not close");
        ThorBridge.intent(1,0); SystemClock.sleep(150);
        finger(MotionEvent.ACTION_DOWN,.78f,.35f); finger(MotionEvent.ACTION_UP,.78f,.35f);
        JSONObject selected=await(s->{
            JSONArray rows=s.optJSONArray("widgets"); if(rows==null) return false;
            for(int i=0;i<rows.length();i++) if(rows.optJSONObject(i).optString("action").equals("Command_CancelConstruction")) return true;
            return false;
        },"Unfinished building did not expose its cancel button");
        JSONObject cancel=null; JSONArray rows=selected.getJSONArray("widgets");
        for(int i=0;i<rows.length();i++) if(rows.getJSONObject(i).optString("action").equals("Command_CancelConstruction")) cancel=rows.getJSONObject(i);
        check(cancel!=null && cancel.optBoolean("command") && cancel.optBoolean("enabled"),"Cancel construction is missing from the command snapshot");
        final int cancelId=cancel.getInt("id"); ThorDeck d=deck();
        SystemClock.sleep(200);
        runOnMainSync(()->{
            d.geometry();
            boolean inWheel=false; for(JSONObject row:d.control.commands) if(row.optInt("id")==cancelId) inWheel=true;
            check(inWheel,"Cancel construction is missing from the RT command wheel");
        });
        float[] point=new float[2];
        runOnMainSync(()->{
            int index=-1; for(int i=0;i<d.shown.size();i++) if(d.shown.get(i).optInt("id")==cancelId) index=i;
            check(index>=0,"Cancel construction is missing from the bottom-screen grid");
            android.graphics.RectF rect=d.commandRects.get(index);
            point[0]=d.offsetX+d.scale*rect.centerX(); point[1]=d.offsetY+d.scale*rect.centerY();
        });
        dispatch(d,MotionEvent.ACTION_DOWN,point); dispatch(d,MotionEvent.ACTION_UP,point);
        await(s->s.optInt("money")==moneyBefore,"Cancel construction did not refund the original building cost");
        report("Unfinished-building cancel appeared in wheel/grid; bottom-screen touch cancelled it and refunded the full cost.");
    }
}
