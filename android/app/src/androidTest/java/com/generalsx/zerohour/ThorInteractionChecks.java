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
    boolean fresh;
    boolean extended;
    String faction="";
    boolean inventoryOnly;
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
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); fresh=arguments!=null && "true".equals(arguments.getString("fresh")); extended=arguments!=null && "true".equals(arguments.getString("extended")); inventoryOnly=arguments!=null && "true".equals(arguments.getString("inventory")); faction=arguments==null?"":arguments.getString("faction",""); start(); }
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
            if(fresh) { activate("SOLO PLAY"); activate("SKIRMISH"); if(!faction.isEmpty()) chooseFaction(); activate("PLAY GAME"); }
            else {
                activate("LOAD"); activate("LOAD GAME");
                await(s->{ JSONArray rows=s.optJSONArray("widgets"); if(rows==null) return false; for(int i=0;i<rows.length();i++) if((rows.optJSONObject(i).optInt("style")&32)!=0) return true; return false; },"Save list missing");
                activate("LOAD GAME");
            }
            await(s->s.optBoolean("battle"),"Saved match did not load");
            report(fresh?"Fresh skirmish loaded.":"Saved skirmish loaded.");
            if("China".equals(faction)) {
                chinaToggle(); result.putString("stream","China toggle checks passed. Reload your saved match.\n"); finish(0,result); return;
            }
            if(inventoryOnly) {
                ThorBridge.intent(5,1); SystemClock.sleep(400); ThorBridge.intent(18,7); SystemClock.sleep(800);
                inventory(); result.putString("stream","Inventory checks passed. Reload your saved match.\n"); finish(0,result); return;
            }
            selectionAndCamera();
            minimap();
            placement();
            commandsAndTools();
            if(extended) inventory();
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
        check(selected.has("constructionProgress"),"Unfinished building lost its progress indicator");
        commandCoverage(selected);
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
    // GeneralsX @test Codex 14/10/2026 Every visible retail command survives export; virtual tools and bound queue cancellation use actual deck controls.
    void commandCoverage(JSONObject s) {
        JSONArray rows=s.optJSONArray("widgets"); if(rows==null) return;
        for(int i=0;i<rows.length();++i) {
            JSONObject row=rows.optJSONObject(i);
            if(row.optInt("commandType")!=0 && !row.optBoolean("queueSlot")) check(row.optBoolean("command"),"Missing command: "+row.optString("action"));
        }
    }
    void tapDeckRow(ThorDeck d,int id) {
        float[] point=new float[2];
        runOnMainSync(()->{
            d.geometry(); int index=-1;
            for(int i=0;i<d.shown.size();++i) if(d.shown.get(i).optInt("id")==id) index=i;
            check(index>=0,"Deck row not visible: "+id);
            android.graphics.RectF rect=d.commandRects.get(index);
            point[0]=d.offsetX+d.scale*rect.centerX(); point[1]=d.offsetY+d.scale*rect.centerY();
        });
        dispatch(d,MotionEvent.ACTION_DOWN,point); dispatch(d,MotionEvent.ACTION_UP,point);
    }
    void commandsAndTools() throws Exception {
        ThorBridge.intent(5,0); SystemClock.sleep(500); commandCoverage(state());
        ThorDeck d=deck(); tapDeckRow(d,-1000); SystemClock.sleep(200);
        runOnMainSync(()->check(d.control.toolsMenu && d.control.commands.size()==9,"Tactical tools did not open"));
        tapDeckRow(d,-105); // Select matching: the original shortcut should select this constructor's matching units.
        await(s->s.optInt("selected")>0,"Select matching lost the builder selection");
        tapDeckRow(d,-108); await(value->Math.abs(Math.atan2(Math.sin(value.optDouble("cameraAngle")),Math.cos(value.optDouble("cameraAngle"))))<.02,"Camera reset did not restore the default angle");
        runOnMainSync(()->d.control.key(new android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,android.view.KeyEvent.KEYCODE_BUTTON_B)));
        SystemClock.sleep(200); runOnMainSync(()->check(!d.control.toolsMenu,"B did not close tactical tools"));
        report("Tools opened through the deck; matching selection, camera reset and B back passed.");
        ThorBridge.intent(5,1); SystemClock.sleep(500);
        JSONObject s=state(),build=null; commandCoverage(s);
        JSONArray rows=s.getJSONArray("widgets");
        for(int i=0;i<rows.length();++i) if(rows.getJSONObject(i).optString("action").equals("Command_ConstructAmericaDozer")) build=rows.getJSONObject(i);
        check(build!=null && build.optBoolean("enabled"),"Command center cannot produce a dozer");
        int money=s.optInt("money"); final JSONObject buildRow=build;
        runOnMainSync(()->d.control.activate(buildRow));
        await(value->value.optInt("money")==money-1000,"Dozer production did not spend $1000");
        JSONObject queued=await(value->{JSONArray widgets=value.optJSONArray("widgets"); for(int i=0;i<widgets.length();i++) if(widgets.optJSONObject(i).optBoolean("queue")) return true; return false;},"No production queue entry");
        JSONObject queue=null; rows=queued.getJSONArray("widgets");
        for(int i=0;i<rows.length();i++) if(rows.getJSONObject(i).optBoolean("queue")) queue=rows.getJSONObject(i);
        check(queue!=null && !queue.optBoolean("command"),"Production queue leaked into the command wheel");
        ThorBridge.nativeActivateChecked(queue.getInt("id"),queue.optString("action"),queue.optString("activation")+":stale");
        SystemClock.sleep(200); check(state().optInt("money")==money-1000,"Stale production token cancelled a different item");
        SystemClock.sleep(200); tapDeckRow(d,queue.getInt("id"));
        await(value->value.optInt("money")==money,"Actual queue tap did not refund production");
        report("All visible commands exported; stale queue activation rejected and actual deck cancellation refunded $1000.");
    }
    JSONObject action(JSONObject s,String name) {
        JSONArray rows=s.optJSONArray("widgets"); if(rows==null) return null;
        for(int i=0;i<rows.length();++i) if(rows.optJSONObject(i).optString("action").equals(name)) return rows.optJSONObject(i);
        return null;
    }
    void chooseFaction() throws Exception {
        JSONObject s=state(),combo=null; JSONArray rows=s.getJSONArray("widgets");
        for(int i=0;i<rows.length();++i) { JSONObject row=rows.getJSONObject(i); if((row.optInt("style")&32768)!=0 && (row.optString("label").equals("USA") || row.optString("label").equals("China"))) { combo=row; break; } }
        check(combo!=null,"Local faction dropdown not found"); int id=combo.getInt("id"); ThorBridge.nativeActivate(id,"");
        for(int i=0;i<15;i++) {
            SystemClock.sleep(100); JSONObject current=null; rows=state().getJSONArray("widgets");
            for(int j=0;j<rows.length();++j) if(rows.getJSONObject(j).optInt("id")==id) current=rows.getJSONObject(j);
            if(current!=null && current.optString("label").equals(faction)) { ThorBridge.intent(12,40); SystemClock.sleep(200); return; }
            ThorBridge.intent(12,81);
        }
        throw new AssertionError("Faction dropdown did not reach "+faction);
    }
    void chinaToggle() throws Exception {
        check(state().optString("faction").equals("China"),"China fixture did not start as China");
        ThorBridge.intent(5,1); SystemClock.sleep(300); ThorBridge.intent(4,0); SystemClock.sleep(300);
        construct("Command_ConstructChinaPowerPlant",.78f,.60f);
        JSONObject s=state(),toggle=null; JSONArray rows=s.getJSONArray("widgets");
        for(int i=0;i<rows.length();++i) if(rows.getJSONObject(i).optString("action").toLowerCase(java.util.Locale.ROOT).contains("overcharge")) toggle=rows.getJSONObject(i);
        check(toggle!=null && toggle.optBoolean("command") && toggle.optBoolean("enabled"),"Overcharge command missing");
        final String name=toggle.optString("action"); ThorDeck d=deck(); SystemClock.sleep(200);
        tapDeckRow(d,toggle.getInt("id")); await(value->action(value,name)!=null && action(value,name).optBoolean("active"),"Overcharge did not display its active state");
        check(ThorUi.status(action(state(),name),state()).equals("Active"),"Overcharge details lost active state");
        tapDeckRow(d,toggle.getInt("id")); await(value->action(value,name)!=null && !action(value,name).optBoolean("active"),"Overcharge did not switch off");
        report("China reactor overcharge toggled on/off through deck touch with correct active state.");
    }
    void selectSite(float x,float y) throws Exception {
        ThorBridge.intent(1,0); SystemClock.sleep(150);
        finger(MotionEvent.ACTION_DOWN,x,y); finger(MotionEvent.ACTION_UP,x,y); SystemClock.sleep(300);
    }
    // GeneralsX @test Codex 14/10/2026 Build a real local garrison fixture without retail overrides or engine test hooks.
    void construct(String name,float x,float y) throws Exception {
        for(int attempt=0;attempt<15 && action(state(),name)==null;++attempt) { ThorBridge.intent(18,8); SystemClock.sleep(300); }
        await(value->action(value,name)!=null,"No constructor selected for "+name);
        // Idle-worker selection recentres the camera. Keep all fixture sites relative to the same command center.
        ThorBridge.intent(8,9); SystemClock.sleep(100); ThorBridge.intent(5,1); SystemClock.sleep(300); ThorBridge.intent(7,9); SystemClock.sleep(300);
        JSONObject s=await(value->action(value,name)!=null && action(value,name).optBoolean("enabled"),"Missing builder action: "+name);
        JSONObject button=action(s,name); int money=s.optInt("money"),cost=button.optInt("cost");
        ThorBridge.nativeActivateChecked(button.getInt("id"),name,button.optString("activation"));
        await(value->value.optInt("placementStage")==1,"Missing build preview: "+name);
        ThorBridge.nativeIntent(0,0,x,y); await(value->value.optInt("placementStage")==2,"Missing facing stage");
        ThorBridge.nativeIntent(0,0,x,y); await(value->value.optInt("money")==money-cost,"Construction rejected: "+name);
        selectSite(x,y);
        // A foundation can briefly exist before the dozer reaches it and sets UNDER_CONSTRUCTION.
        // Observe construction starting before accepting the later cleared flag as completion.
        await(value->value.optInt("selected")==1 && value.has("constructionProgress"),"Fixture construction did not start: "+name);
        await(value->value.optInt("selected")==1 && !value.optString("selection").contains("Dozer") && !value.has("constructionProgress"),"Fixture building did not complete: "+name);
        commandCoverage(state());
    }
    void inventory() throws Exception {
        construct("Command_ConstructAmericaPowerPlant",.20f,.35f);
        await(value->value.optJSONArray("radarMarkers")!=null && value.optJSONArray("radarMarkers").length()>0,"Construction radar alert was lost from the deck");
        report("Fixture reactor completed; its original radar alert reached the deck.");
        construct("Command_ConstructAmericaBarracks",.25f,.72f); report("Fixture barracks completed.");
        construct("Command_ConstructAmericaFireBase",.88f,.68f); report("Fixture fire base completed.");
        ThorBridge.intent(8,8); SystemClock.sleep(150); // Remember the host independently of later camera/selection changes.
        selectSite(.25f,.72f);
        JSONObject s=await(value->action(value,"Command_ConstructAmericaInfantryRanger")!=null,"Barracks ranger command missing");
        JSONObject ranger=action(s,"Command_ConstructAmericaInfantryRanger"); int money=s.optInt("money"),cost=ranger.optInt("cost");
        ThorBridge.nativeActivateChecked(ranger.getInt("id"),ranger.optString("action"),ranger.optString("activation"));
        ThorBridge.nativeActivateChecked(ranger.getInt("id"),ranger.optString("action"),ranger.optString("activation"));
        await(value->value.optInt("money")==money-cost*2,"Two rangers did not enter production");
        await(value->{ JSONArray rows=value.getJSONArray("widgets"); for(int i=0;i<rows.length();i++) if(rows.optJSONObject(i).optBoolean("queue")) return true; return false; },"Ranger queue did not appear");
        await(value->{ JSONArray rows=value.getJSONArray("widgets"); for(int i=0;i<rows.length();i++) if(rows.optJSONObject(i).optBoolean("queue")) return false; return true; },"Rangers did not complete production");
        for(int attempt=0;attempt<30 && state().optInt("selected")!=2;++attempt) { ThorBridge.intent(18,1); SystemClock.sleep(200); }
        await(value->value.optInt("selected")==2,"Select army did not select both rangers");
        SystemClock.sleep(500); report("Army members before group assignment: "+state().optJSONArray("selectionMembers"));
        ThorBridge.intent(8,7);
        await(value->value.optJSONArray("groups").optInt(7)==2,"Both rangers were not committed to the saved control group");
        ThorBridge.intent(7,8);
        await(value->value.optString("selection").equals("Fire Base"),"Host group did not restore fire base selection");
        ThorBridge.intent(4,0); SystemClock.sleep(300);
        JSONObject host=state(); float targetX=(float)host.optDouble("selectionScreenX"),targetY=(float)host.optDouble("selectionScreenY");
        ThorBridge.intent(7,7); await(value->value.optInt("selected")==2,"Ranger group did not restore");
        SystemClock.sleep(300);
        check(distance(host,state())<2,"Recalling rangers moved the fixture camera");
        ThorBridge.nativeIntent(0,0,targetX,targetY); SystemClock.sleep(500);
        ThorBridge.intent(7,8); await(value->value.optString("selection").equals("Fire Base"),"Host selection lost after order");
        s=await(value->{
            if(value.optInt("passengers")!=2) return false;
            int count=0; JSONArray buttons=value.getJSONArray("widgets");
            for(int i=0;i<buttons.length();++i) if(buttons.optJSONObject(i).has("occupant")) count++;
            return count==2;
        },"Both ranger exits did not reach the fire base's original command panel"); commandCoverage(s);
        java.util.ArrayList<JSONObject> occupants=new java.util.ArrayList<>(); JSONArray rows=s.getJSONArray("widgets");
        for(int i=0;i<rows.length();i++) if(rows.optJSONObject(i).has("occupant")) occupants.add(rows.optJSONObject(i));
        check(occupants.size()==2,"Native snapshot lost an individual passenger");
        ThorDeck d=deck(); SystemClock.sleep(200);
        runOnMainSync(()->{
            int count=0; java.util.HashSet<String> keys=new java.util.HashSet<>();
            for(JSONObject row:d.control.commands) if(row.has("occupant")) { count++; keys.add(row.optString("identity")); check(row.optString("label").contains("Ranger"),"Passenger label is not the occupant"); check(!row.optString("icon").isEmpty(),"Passenger portrait missing"); }
            check(count==2 && keys.size()==2,"The wheel collapsed individual passenger slots");
        });
        JSONObject first=occupants.get(0); tapDeckRow(d,first.getInt("id"));
        await(value->value.optInt("passengers")==1,"Individual exit did not remove exactly one ranger");
        ThorBridge.nativeActivateChecked(first.getInt("id"),first.optString("action"),first.optString("activation"));
        SystemClock.sleep(200); check(state().optInt("passengers")==1,"Stale passenger slot ejected another occupant");
        report("Two independent Ranger slots reached the wheel/grid; individual exit and stale-occupant protection passed.");
    }
}
