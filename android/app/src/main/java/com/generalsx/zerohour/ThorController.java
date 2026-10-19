package com.generalsx.zerohour;

import android.app.Presentation;
import android.content.Context;
import android.hardware.display.DisplayManager;
import android.os.Handler;
import android.os.Looper;
import android.view.Display;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;

// GeneralsX @feature Codex 14/10/2026 Xbox RTS contexts, controller menus and display lifecycle.
final class ThorController implements DisplayManager.DisplayListener, android.hardware.input.InputManager.InputDeviceListener {
    final GeneralsZHActivity activity;
    final Handler handler = new Handler(Looper.getMainLooper());
    final DisplayManager displays;
    final android.content.SharedPreferences preferences;
    final android.hardware.input.InputManager inputs;
    final ThorOverlay overlay;
    Presentation presentation;
    ThorDeck deck;
    ThorDeck fallbackDeck;
    JSONObject snapshot = new JSONObject();
    final ArrayList<JSONObject> widgets = new ArrayList<>();
    final ArrayList<JSONObject> commands = new ArrayList<>();
    final ArrayList<JSONObject> deckRows = new ArrayList<>();
    final ArrayList<JSONObject> queueRows = new ArrayList<>();
    final ThorUi ui = new ThorUi();
    JSONObject powers;
    boolean scienceMenu;
    int queuePage;
    long lastRadarRead;
    boolean ready, running, battle, lt, rt, lb, r3, deckFocus, mapMode, typing;
    int focusedId, page, wedge = -1, groupPage, keyboardIndex, deckPage;
    int menuColumns=2, hatX, hatY, dpadKeys, editingId;
    final ThorControls.Navigation dpadNavigation=new ThorControls.Navigation(),stickNavigation=new ThorControls.Navigation();
    float lx, ly, rx, ry, mapX = .5f, mapY = .5f;
    long aDown, lastTap;
    long lastDiagnostic;
    boolean aHeld;
    boolean description;
    int descriptionScroll;
    JSONObject confirmation;
    // GeneralsX @feature Codex 14/10/2026 Tactical tools expose keyboard-only actions through existing wheel/deck navigation.
    boolean toolsMenu;
    JSONObject toolsEntry;
    boolean editingWidget;
    boolean controllerActive;
    boolean ltButton, rtButton, ltAxis, rtAxis;
    long lastMotionDiagnostic;
    int lastKeyCode, lastKeySource, lastMotionSource;
    int lastControllerDevice = -1;
    String entered = "";
    final String keys = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789 _-.";

    ThorController(GeneralsZHActivity activity) {
        this.activity = activity;
        preferences = activity.getSharedPreferences("thor_controller", Context.MODE_PRIVATE);
        displays = (DisplayManager)activity.getSystemService(Context.DISPLAY_SERVICE);
        inputs = (android.hardware.input.InputManager)activity.getSystemService(Context.INPUT_SERVICE);
        inputs.registerInputDeviceListener(this, handler);
        overlay = new ThorOverlay(activity, this);
        activity.addContentView(overlay, new FrameLayout.LayoutParams(-1, -1));
        fallbackDeck = new ThorDeck(activity, this);
        FrameLayout.LayoutParams fallbackLayout = new FrameLayout.LayoutParams(activity.getResources().getDisplayMetrics().widthPixels * 2 / 3, activity.getResources().getDisplayMetrics().heightPixels * 9 / 10, android.view.Gravity.CENTER);
        activity.addContentView(fallbackDeck, fallbackLayout);
        fallbackDeck.setVisibility(View.GONE);
        displays.registerDisplayListener(this, handler);
        attachDisplay();
        resume();
    }
    void attachDisplay() {
        Display target = null;
        for (Display d : displays.getDisplays())
            if (d.getDisplayId() != activity.getDisplay().getDisplayId()) { target = d; break; }
        if (presentation != null && (target == null || presentation.getDisplay().getDisplayId() != target.getDisplayId())) {
            presentation.dismiss(); presentation = null; deck = null;
        }
        if (target != null && presentation == null) {
            presentation = new Presentation(activity, target);
            deck = new ThorDeck(presentation.getContext(), this);
            presentation.setContentView(deck);
            presentation.getWindow().addFlags(WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE);
            presentation.getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
            try { presentation.show(); }
            catch (WindowManager.InvalidDisplayException e) { presentation = null; deck = null; }
            android.util.Log.i("Thor", "Command deck display " + target.getDisplayId());
        }
    }
    final Runnable tick = new Runnable() {
        public void run() {
            if (!running) return;
            try {
                snapshot = new JSONObject(ThorBridge.nativeSnapshot());
                ready = snapshot.optBoolean("ready");
                if (ready) ThorBridge.nativeOptions(preferences.getBoolean("snap", true), presentation != null);
                boolean wasBattle = battle;
                battle = snapshot.optBoolean("battle");
                if (wasBattle != battle) {
                    toolsMenu = false;
                    focusedId = 0; deckFocus = mapMode = rt = lb = false; aHeld = false;
                    if (!battle && ready) ThorBridge.intent(97, 0);
                }
                JSONObject oldFocus = find(focusedId);
                String focusAction = oldFocus == null ? "" : oldFocus.optString("identity", oldFocus.optString("action"));
                widgets.clear(); commands.clear(); deckRows.clear(); queueRows.clear(); powers = null;
                JSONArray rows = snapshot.optJSONArray("widgets");
                if (rows != null) for (int i = 0; i < rows.length(); i++) {
                    JSONObject row = rows.optJSONObject(i);
                    if (row == null) continue;
                    widgets.add(row);
                    if (row.optBoolean("command")) commands.add(row);
                    if (row.optBoolean("queue")) queueRows.add(row);
                    if (row.optString("action").equals("NonCommand_GeneralsExperience")) powers = row;
                }
                // GeneralsX @feature Codex 14/10/2026 Stable native slot ordering and contextual/shortcut deduplication.
                scienceMenu = false;
                for (JSONObject row : commands) if (row.optBoolean("science")) scienceMenu = true;
                if (scienceMenu) {
                    for (int i = commands.size() - 1; i >= 0; --i) if (!commands.get(i).optBoolean("science")) commands.remove(i);
                    java.util.Collections.sort(commands, (a, b) -> a.optInt("y") == b.optInt("y") ? Integer.compare(a.optInt("x"), b.optInt("x")) : Integer.compare(a.optInt("y"), b.optInt("y")));
                } else java.util.Collections.sort(commands, (a, b) -> Integer.compare(a.optInt("slot", -1) < 0 ? 1000 : a.optInt("slot"), b.optInt("slot", -1) < 0 ? 1000 : b.optInt("slot")));
                // GeneralsX @bugfix Codex 14/10/2026 Only duplicate power shortcuts merge; repeated occupant/action slots remain independent.
                java.util.HashMap<String, Integer> identities = new java.util.HashMap<>();
                for (int i = 0; i < commands.size();) {
                    JSONObject row = commands.get(i);
                    String identity = row.optString("identity", row.optString("action", "id:" + row.optInt("id")));
                    Integer existing = identity.startsWith("power:") ? identities.get(identity) : null;
                    if (existing == null) { identities.put(identity, i); ++i; }
                    else {
                        JSONObject previous = commands.get(existing);
                        if (ThorCommands.preferReplacement(previous.optBoolean("enabled"), (float)previous.optDouble("recharge", 1), row.optBoolean("enabled"), (float)row.optDouble("recharge", 1))) commands.set(existing, row);
                        commands.remove(i);
                    }
                }
                if (battle) {
                    toolsEntry = new JSONObject().put("id", -1000).put("label", toolsMenu ? "Back to commands" : "Tactical tools").put("enabled", true).put("identity", "tools-menu");
                    widgets.add(toolsEntry);
                    if (toolsMenu) {
                        commands.clear();
                        JSONArray tools=snapshot.optJSONArray("tools");
                        if(tools!=null) for(int i=0;i<tools.length();++i) { JSONObject row=tools.optJSONObject(i); if(row!=null) { commands.add(row); widgets.add(row); } }
                    } else commands.add(toolsEntry);
                } else toolsEntry = null;
                if (battle) {
                    deckRows.addAll(commands);
                    deckRows.addAll(queueRows);
                    if (powers != null) deckRows.add(powers);
                } else {
                    // GeneralsX @bugfix Codex 18/10/2026 Keep menu order tied to its native reading order while windows animate in.
                    deckRows.addAll(widgets);
                    java.util.Collections.sort(deckRows,(a,b)->a.optInt("y")==b.optInt("y")?Integer.compare(a.optInt("x"),b.optInt("x")):Integer.compare(a.optInt("y"),b.optInt("y")));
                    float[][] nativeRects=new float[deckRows.size()][];
                    for(int i=0;i<deckRows.size();++i) {
                        JSONObject row=deckRows.get(i);
                        nativeRects[i]=new float[]{row.optInt("x"),row.optInt("y"),row.optInt("x")+row.optInt("w"),row.optInt("y")+row.optInt("h")};
                    }
                    menuColumns=ThorDeckLayout.menuColumns(nativeRects);
                }
                if (battle) {
                    JSONObject retained = null;
                    for (JSONObject row : deckRows) if (row.optInt("id") == focusedId && (focusAction.isEmpty() || focusAction.equals(row.optString("identity", row.optString("action"))))) retained = row;
                    if (retained == null && !focusAction.isEmpty()) for (JSONObject row : deckRows) if (focusAction.equals(row.optString("identity", row.optString("action")))) { retained = row; break; }
                    focusedId = retained != null ? retained.optInt("id") : deckRows.isEmpty() ? 0 : deckRows.get(0).optInt("id");
                } else {
                    JSONObject focus=find(focusedId);
                    if(focus==null || !focus.optBoolean("enabled")) focusedId=firstEnabled();
                }
                // GeneralsX @bugfix Codex 19/10/2026 Leaving a native editor cannot trap D-pad input in an obsolete field.
                if(editingWidget && (find(editingId)==null || !find(editingId).optBoolean("enabled"))) { editingWidget=false; editingId=0; }
                page = Math.min(page, Math.max(0, (commands.size() - 1) / 8));
                deckPage = Math.min(deckPage, Math.max(0, ((battle ? commands.size() : deckRows.size()) - 1) / deckPageSize()));
                queuePage = Math.min(queuePage, Math.max(0, (queueRows.size() - 1) / ThorDeckLayout.QUEUE_PER_PAGE));
                if (battle && deckFocus) {
                    for (int i = 0; i < commands.size(); ++i) if (commands.get(i).optInt("id") == focusedId) deckPage = i / ThorDeckLayout.COMMANDS_PER_PAGE;
                    for (int i = 0; i < queueRows.size(); ++i) if (queueRows.get(i).optInt("id") == focusedId) queuePage = i / ThorDeckLayout.QUEUE_PER_PAGE;
                }
                if(!battle) for(int i=0;i<deckRows.size();++i) if(deckRows.get(i).optInt("id")==focusedId) deckPage=i/deckPageSize();
                refreshArtwork();
                long diagnosticNow = android.os.SystemClock.uptimeMillis();
                if ((activity.getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0 && diagnosticNow - lastDiagnostic > 1000) {
                    lastDiagnostic = diagnosticNow;
                    try {
                        snapshot.put("focusedId", focusedId);
                        snapshot.put("controllerActive", controllerActive);
                        snapshot.put("lastKeyCode", lastKeyCode);
                        snapshot.put("lastKeySource", lastKeySource);
                        snapshot.put("lastMotionSource", lastMotionSource);
                        snapshot.put("lx", lx); snapshot.put("ly", ly);
                        snapshot.put("rx", rx); snapshot.put("ry", ry);
                        snapshot.put("rt", rt); snapshot.put("lt", lt);
                        snapshot.put("deckFocus", deckFocus); snapshot.put("mapMode", mapMode);
                        snapshot.put("page", page); snapshot.put("wedge", wedge);
                        snapshot.put("deckPage", deckPage); snapshot.put("queuePage", queuePage); snapshot.put("artworkCached", ui.art.snapshot().size());
                        snapshot.put("physicalWidth", overlay.getWidth()); snapshot.put("physicalHeight", overlay.getHeight());
                        try (java.io.FileWriter out = new java.io.FileWriter(new java.io.File(activity.getExternalFilesDir(null), "thor-state.json"))) { out.write(snapshot.toString()); }
                    } catch (java.io.IOException e) { android.util.Log.w("Thor", "State capture unavailable", e); }
                }
                if (mapMode) {
                    JSONArray bounds=snapshot.optJSONArray("radarBounds");
                    mapX = Math.max(bounds==null?0:(float)bounds.optDouble(0), Math.min(bounds==null?.999f:(float)bounds.optDouble(2), mapX + lx * .025f));
                    mapY = Math.max(bounds==null?0:(float)bounds.optDouble(1), Math.min(bounds==null?.999f:(float)bounds.optDouble(3), mapY + ly * .025f));
                }
                sendAxes();
                pumpDpad();
                boolean stickMenu=!battle && !typing && !rt && !lb;
                int sx=stickMenu && Math.abs(lx)>.6f && Math.abs(lx)>Math.abs(ly)?(lx>0?1:-1):0;
                int sy=stickMenu && Math.abs(ly)>.6f && Math.abs(ly)>=Math.abs(lx)?(ly>0?1:-1):0;
                if(stickNavigation.step(sx,sy,android.os.SystemClock.uptimeMillis(),true)) direction(stickNavigation.dx,stickNavigation.dy);
                overlay.invalidate();
                if (deck != null) deck.invalidate();
                fallbackDeck.setVisibility(presentation == null && (deckFocus || mapMode) ? View.VISIBLE : View.GONE);
                if (fallbackDeck.getVisibility() == View.VISIBLE) fallbackDeck.invalidate();
            } catch (UnsatisfiedLinkError e) {
                ready = false; // SDL has not loaded the game library yet.
            } catch (org.json.JSONException e) {
                android.util.Log.e("Thor", "Invalid engine snapshot", e);
            }
            handler.postDelayed(this, 33);
        }
    };
    void resume() { if (running) return; running = true; if (ready) ThorBridge.intent(98, 0); handler.post(tick); }
    void pause() {
        running = false; handler.removeCallbacks(tick);
        aHeld = false; lt = rt = lb = r3 = false; lx = ly = rx = ry = 0;
        ltButton = rtButton = ltAxis = rtAxis = false;
        resetNavigation();
        typing = deckFocus = mapMode = toolsMenu = editingWidget = false; editingId=0; confirmation = null;
        if (ready) { ThorBridge.nativeAxes(0, 0, 0, 0, false, false); ThorBridge.intent(99, 0); }
    }
    void destroy() {
        pause(); displays.unregisterDisplayListener(this); inputs.unregisterInputDeviceListener(this);
        if (presentation != null) presentation.dismiss();
    }
    void sendAxes() {
        if (!ready || !controllerActive) return;
        boolean blocked = rt || lb || deckFocus || mapMode || typing || !battle;
        float speed = preferences.getInt("speed", 100) / 100f;
        boolean rotating = snapshot.optInt("placementStage") == 2;
        float vertical = !rotating && preferences.getBoolean("invert_pan_y", false) ? -ly : ly;
        // GeneralsX @feature Codex 14/10/2026 Owner preference: up zooms in, while precision aim stays natural.
        float zoom = (!r3 || rotating) && preferences.getBoolean("invert_zoom", true) ? -ry : ry;
        ThorBridge.nativeAxes(blocked ? 0 : lx * speed, blocked ? 0 : vertical * speed, blocked ? 0 : rx, blocked ? 0 : zoom, r3, lt);
        if ((rt || lb) && Math.max(Math.abs(lx), Math.abs(ly)) > .35f) {
            wedge = ThorControls.wedge(lx, ly);
        }
    }
    float dead(float value) { return ThorControls.deadZone(value, preferences.getInt("deadzone", 15) / 100f); }
    boolean motion(MotionEvent e) {
        lastMotionSource = e.getSource();
        if ((e.getSource() & InputDevice.SOURCE_JOYSTICK) != InputDevice.SOURCE_JOYSTICK || !ready) return false;
        controllerActive = true;
        if (e.getDeviceId() >= 0) lastControllerDevice = e.getDeviceId();
        lx = dead(e.getAxisValue(MotionEvent.AXIS_X)); ly = dead(e.getAxisValue(MotionEvent.AXIS_Y));
        rx = dead(e.getAxisValue(MotionEvent.AXIS_Z)); ry = dead(e.getAxisValue(MotionEvent.AXIS_RZ));
        long diagnosticNow = android.os.SystemClock.uptimeMillis();
        if (diagnosticNow - lastMotionDiagnostic > 500) {
            lastMotionDiagnostic = diagnosticNow;
            android.util.Log.d("ThorInput", "Joystick device=" + e.getDeviceId() + " axes=" + lx + "," + ly + "," + rx + "," + ry);
        }
        ltAxis = Math.max(e.getAxisValue(MotionEvent.AXIS_LTRIGGER), e.getAxisValue(MotionEvent.AXIS_BRAKE)) > .5f;
        rtAxis = Math.max(e.getAxisValue(MotionEvent.AXIS_RTRIGGER), e.getAxisValue(MotionEvent.AXIS_GAS)) > .5f;
        boolean newLt = ltButton || ltAxis, newRt = battle && (rtButton || rtAxis);
        if (newLt != lt) lt = newLt;
        if (newRt != rt) { rt = newRt; page = 0; wedge = -1; description = false; descriptionScroll = 0; }
        hatX=Math.round(e.getAxisValue(MotionEvent.AXIS_HAT_X)); hatY=Math.round(e.getAxisValue(MotionEvent.AXIS_HAT_Y));
        pumpDpad();
        sendAxes(); return true;
    }
    boolean key(KeyEvent e) {
        lastKeyCode = e.getKeyCode(); lastKeySource = e.getSource();
        android.util.Log.d("ThorInput", "Key device=" + e.getDeviceId() + " source=" + e.getSource() + " code=" + e.getKeyCode() + " action=" + e.getAction());
        int code = preferences.getInt("button_" + e.getKeyCode(), e.getKeyCode());
        // GeneralsX @bugfix Codex 19/10/2026 Movies and scripted scenes never fall through to menu/gameplay actions.
        if (!ready || snapshot.optBoolean("movie") || snapshot.optBoolean("cinematic")) {
            boolean gamepad = KeyEvent.isGamepadButton(code) || code >= KeyEvent.KEYCODE_DPAD_UP && code <= KeyEvent.KEYCODE_DPAD_RIGHT;
            if (!gamepad) return false;
            aHeld = false;
            try {
                if (code == KeyEvent.KEYCODE_BUTTON_A && e.getAction() == KeyEvent.ACTION_UP)
                    ThorBridge.intent(20, snapshot.optInt("skipMovie"));
                if (ready && snapshot.optBoolean("cinematic") && code == KeyEvent.KEYCODE_BUTTON_START
                        && e.getAction() == KeyEvent.ACTION_DOWN && e.getRepeatCount() == 0)
                    ThorBridge.intent(21, 0);
                return true;
            } catch (UnsatisfiedLinkError error) { return false; }
        }
        if (!KeyEvent.isGamepadButton(code) && code != KeyEvent.KEYCODE_DPAD_UP && code != KeyEvent.KEYCODE_DPAD_DOWN && code != KeyEvent.KEYCODE_DPAD_LEFT && code != KeyEvent.KEYCODE_DPAD_RIGHT) return false;
        controllerActive = true;
        if (e.getDeviceId() >= 0) lastControllerDevice = e.getDeviceId();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        // Hat axes and D-pad keys can describe the same held direction. Route them through one edge/repeat state.
        if(code>=KeyEvent.KEYCODE_DPAD_UP && code<=KeyEvent.KEYCODE_DPAD_RIGHT) {
            int bit=1<<(code-KeyEvent.KEYCODE_DPAD_UP);
            if(down) dpadKeys|=bit; else dpadKeys&=~bit;
            pumpDpad(); return true;
        }
        if (down && e.getRepeatCount() != 0) return true;
        switch (code) {
        case KeyEvent.KEYCODE_BUTTON_L2: ltButton = down; lt = ltButton || ltAxis; sendAxes(); return true;
        case KeyEvent.KEYCODE_BUTTON_R2: rtButton = down; rt = battle && (rtButton || rtAxis); page = 0; wedge = -1; description = false; descriptionScroll = 0; sendAxes(); return true;
        case KeyEvent.KEYCODE_BUTTON_L1: lb = battle && down; wedge = -1; sendAxes(); return true;
        case KeyEvent.KEYCODE_BUTTON_THUMBR: r3 = down; sendAxes(); return true;
        case KeyEvent.KEYCODE_BUTTON_A:
            if (down) { aDown = e.getEventTime(); aHeld = true; if (battle && !rt && !lb && !deckFocus && !mapMode && !typing) ThorBridge.intent(13, 0); }
            else if (aHeld) { aHeld = false; confirm(e.getEventTime()); }
            return true;
        case KeyEvent.KEYCODE_BUTTON_Y:
            if (rt) { description = down; if (down) descriptionScroll = 0; return true; }
            if (!down) { description = false; return true; }
            break;
        default: if (!down) return true;
        }
        switch (code) {
        case KeyEvent.KEYCODE_BUTTON_B:
            if (confirmation != null) confirmation = null;
            else if (typing) { typing = false; entered = ""; }
            else if (editingWidget) { editingWidget = false; editingId=0; }
            else if (toolsMenu) { toolsMenu = false; focusedId = 0; page = deckPage = 0; }
            else if (rt || lb) { rt = lb = false; }
            else if (scienceMenu && powers != null) activate(powers);
            else if (deckFocus || mapMode) { deckFocus = mapMode = false; }
            else if (!battle) {
                JSONObject back = null;
                for (JSONObject w : widgets) {
                    String label = w.optString("label").replace("&", "").trim();
                    if (w.optBoolean("enabled") && (w.optBoolean("back") || label.equalsIgnoreCase("BACK") || label.equalsIgnoreCase("CANCEL"))) { back = w; break; }
                }
                if (back != null) activate(back); else ThorBridge.intent(11, 0);
            } else ThorBridge.intent(1, 0);
            break;
        case KeyEvent.KEYCODE_BUTTON_X:
            if (typing) { if (!entered.isEmpty()) entered = entered.substring(0, entered.length() - 1); }
            else if (lb && wedge >= 0 && groupPage * 8 + wedge < 10) ThorBridge.intent(lt ? 9 : 8, groupPage * 8 + wedge);
            else if (battle) ThorBridge.intent(2, 0);
            break;
        case KeyEvent.KEYCODE_BUTTON_Y:
            if (typing) commitText();
            else if (lb && wedge >= 0 && groupPage * 8 + wedge < 10) ThorBridge.intent(10, groupPage * 8 + wedge);
            else if (mapMode) ThorBridge.nativeIntent(15, 0, mapX, mapY);
            else if (battle) ThorBridge.intent(3, 0);
            break;
        case KeyEvent.KEYCODE_BUTTON_R1: if (battle) mapMode = !mapMode; break;
        case KeyEvent.KEYCODE_BUTTON_SELECT:
            if (!battle) break;
            deckFocus = !deckFocus;
            if (deckFocus && !deckRows.isEmpty()) { focusedId = deckRows.get(0).optInt("id"); deckPage = 0; }
            break;
        case KeyEvent.KEYCODE_BUTTON_START:
            rt = lb = deckFocus = mapMode = false; aHeld = false;
            ThorBridge.intent(97, 0); ThorBridge.intent(11, 0); break;
        case KeyEvent.KEYCODE_BUTTON_THUMBL:
            // GeneralsX @feature Codex 14/10/2026 Select matching on-screen units without aiming through cursor snap twice.
            if (lt) {
                if (battle && !rt && !lb && !deckFocus && !mapMode && !typing && !editingWidget && confirmation == null) ThorBridge.intent(19, 0);
            } else ThorBridge.intent(4, 0);
            break;
        }
        sendAxes(); overlay.invalidate(); return true;
    }
    void confirm(long now) {
        // GeneralsX @bugfix Codex 18/10/2026 A can skip a full-screen movie without selecting a menu behind it.
        if (snapshot.optBoolean("movie")) { ThorBridge.intent(20, snapshot.optInt("skipMovie")); return; }
        if (confirmation != null) { activateChecked(confirmation); confirmation = null; return; }
        if (typing) { if (keyboardIndex < keys.length()) entered += keys.charAt(keyboardIndex); return; }
        if (editingWidget) { ThorBridge.intent(12, 40); editingWidget = false; editingId=0; return; }
        if (lb) { if (wedge >= 0 && groupPage * 8 + wedge < 10) ThorBridge.intent(7, groupPage * 8 + wedge); return; }
        if (rt) {
            int index = page * 8 + wedge;
            if (wedge >= 0 && index < commands.size()) activate(commands.get(index));
            return;
        }
        if (!battle || deckFocus) { JSONObject w = find(focusedId); if (w != null) activate(w); return; }
        if (mapMode) { ThorBridge.nativeIntent(15, 1, mapX, mapY); return; }
        int tap = now - lastTap < 250 ? 2 : 1; lastTap = now;
        ThorBridge.intent(14, tap);
    }
    void activate(JSONObject w) {
        if (!w.optBoolean("enabled")) return;
        if(w.optInt("id")==-1000) { toolsMenu=!toolsMenu; focusedId=0; page=deckPage=0; return; }
        if(w.optString("action").equals("NonCommand_GeneralsExperience")) { toolsMenu=false; page=deckPage=0; }
        if (w.optBoolean("confirm")) { confirmation = w; return; }
        activateChecked(w);
        if ((w.optInt("style") & 64) != 0) {
            typing = true; entered = w.optString("value", w.optString("label")); keyboardIndex = 0;
        }
        else if ((w.optInt("style") & (8 | 16 | 32 | 32768)) != 0) { editingWidget = true; editingId=w.optInt("id"); }
    }
    // GeneralsX @bugfix Codex 14/10/2026 Reused queue/passenger slots cannot activate a different target from an old snapshot.
    void activateChecked(JSONObject w) {
        if(w.has("intent")) { ThorBridge.intent(w.optInt("intent"),w.optInt("value")); return; }
        ThorBridge.nativeActivateChecked(w.optInt("id"), w.optString("action"), w.optString("activation"));
    }
    void commitText() {
        if (!typing) return;
        ThorBridge.nativeText(entered); typing = false;
    }
    void touch() {
        controllerActive = false;
        aHeld = false; rt = lb = r3 = false; description = false;
        resetNavigation(); editingWidget=false; editingId=0;
        if (ready) { ThorBridge.nativeAxes(0, 0, 0, 0, false, false); ThorBridge.intent(97, 0); ThorBridge.intent(96, 0); }
    }
    void direction(int dx, int dy) {
        if (typing) { keyboardIndex = (keyboardIndex + dx + dy * 10 + keys.length()) % keys.length(); return; }
        if (editingWidget) { ThorBridge.intent(12, dx < 0 ? 80 : dx > 0 ? 79 : dy < 0 ? 82 : 81); return; }
        if (rt) {
            if (description && dy != 0) descriptionScroll = Math.max(0, descriptionScroll + dy * 3);
            else { page = Math.max(0, Math.min(Math.max(0, (commands.size() - 1) / 8), page + dx)); descriptionScroll = 0; }
            return;
        }
        if (lb) { groupPage = dx > 0 ? 1 : 0; return; }
        if (!battle || deckFocus) { navigate(dx, dy); return; }
        if (mapMode) return;
        ThorBridge.intent(5, dy < 0 ? 0 : dy > 0 ? 1 : dx < 0 ? 2 : 3);
    }
    int deckPageSize() { return battle?ThorDeckLayout.COMMANDS_PER_PAGE:ThorDeckLayout.menuPageSize(menuColumns); }
    void resetNavigation() { hatX=hatY=dpadKeys=0; dpadNavigation.reset(); stickNavigation.reset(); }
    void pumpDpad() {
        int x=hatX,y=hatY;
        if(x==0 && y==0) { x=((dpadKeys&8)!=0?1:0)-((dpadKeys&4)!=0?1:0); y=((dpadKeys&2)!=0?1:0)-((dpadKeys&1)!=0?1:0); }
        if(dpadNavigation.step(x,y,android.os.SystemClock.uptimeMillis(),!battle || deckFocus || editingWidget || typing)) direction(dpadNavigation.dx,dpadNavigation.dy);
    }
    JSONObject find(int id) { for (JSONObject w : widgets) if (w.optInt("id") == id) return w; return null; }
    JSONObject highlighted() {
        if (rt) { int index = page * 8 + wedge; return wedge >= 0 && index < commands.size() ? commands.get(index) : null; }
        return deckFocus || !battle ? find(focusedId) : null;
    }
    void refreshArtwork() {
        ui.begin(snapshot);
        JSONObject selected = highlighted();
        if (selected != null) ui.fetch(selected.optString("icon"));
        ui.fetch(snapshot.optString("portrait")); ui.fetch(snapshot.optString("factionIcon"));
        for (JSONObject row : commands) ui.fetch(row.optString("icon"));
        for (JSONObject row : queueRows) ui.fetch(row.optString("icon"));
        JSONArray markers=snapshot.optJSONArray("radarMarkers");
        if(markers!=null) for(int i=0;i<markers.length();++i) if(markers.optJSONObject(i).optBoolean("hero")) { ui.fetch("HeroReticle"); break; }
        long now = android.os.SystemClock.uptimeMillis();
        if (now - lastRadarRead >= 100) {
            int[] pixels = battle ? ThorBridge.nativeRadar() : null;
            if (deck != null) deck.updateRadar(pixels); fallbackDeck.updateRadar(pixels); lastRadarRead = now;
        }
    }
    int firstEnabled() {
        JSONObject first = null;
        for (JSONObject w : widgets) {
            if (!w.optBoolean("enabled")) continue;
            String label = w.optString("label");
            if (label.equalsIgnoreCase("RETURN TO GAME") || label.equalsIgnoreCase("NO")) return w.optInt("id");
            if (first == null || w.optInt("y") < first.optInt("y") || w.optInt("y") == first.optInt("y") && w.optInt("x") < first.optInt("x")) first = w;
        }
        return first == null ? 0 : first.optInt("id");
    }
    // GeneralsX @bugfix Codex 18/10/2026 Focus follows the displayed menu or battlefield deck grid.
    void navigate(int dx, int dy) {
        (deck != null ? deck : fallbackDeck).navigate(dx, dy);
    }
    public void onDisplayAdded(int id) { attachDisplay(); }
    public void onDisplayRemoved(int id) { attachDisplay(); }
    public void onDisplayChanged(int id) { attachDisplay(); }
    public void onInputDeviceAdded(int id) { }
    public void onInputDeviceChanged(int id) { }
    public void onInputDeviceRemoved(int id) {
        if (id != lastControllerDevice) return;
        // GeneralsX @bugfix Codex 14/10/2026 Disconnects release every held controller state.
        aHeld = false; lt = rt = lb = r3 = false; lx = ly = rx = ry = 0;
        ltButton = rtButton = ltAxis = rtAxis = false;
        resetNavigation();
        if (ready) { ThorBridge.nativeAxes(0, 0, 0, 0, false, false); ThorBridge.intent(97, 0); }
    }
}
