package com.generalsx.zerohour;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.KeyEvent;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

// GeneralsX @feature Codex 14/10/2026 Persist sensitivity and physical button mappings.
public final class ThorSettingsActivity extends Activity {
    SharedPreferences preferences;
    // GeneralsX @feature Codex 14/10/2026 Android preferences are usable from the face buttons too.
    @Override public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_A) {
            if (event.getAction() == KeyEvent.ACTION_UP && getCurrentFocus() != null) getCurrentFocus().performClick();
            return true;
        }
        if (event.getKeyCode() == KeyEvent.KEYCODE_BUTTON_B) {
            if (event.getAction() == KeyEvent.ACTION_UP) finish();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        preferences = getSharedPreferences("thor_controller", MODE_PRIVATE);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL); root.setPadding(40, 20, 40, 20);
        TextView title = new TextView(this); title.setText("Controller settings"); title.setTextSize(26); root.addView(title);
        slider(root, "Stick dead zone", "deadzone", 5, 40, 15);
        slider(root, "Camera speed", "speed", 25, 200, 100);
        CheckBox invert = new CheckBox(this); invert.setText("Invert left-stick vertical camera movement"); invert.setChecked(preferences.getBoolean("invert_pan_y", false)); invert.setOnCheckedChangeListener((v, checked) -> preferences.edit().putBoolean("invert_pan_y", checked).apply()); root.addView(invert);
        CheckBox zoom = new CheckBox(this); zoom.setText("Right-stick up zooms in"); zoom.setChecked(preferences.getBoolean("invert_zoom", true)); zoom.setOnCheckedChangeListener((v, checked) -> preferences.edit().putBoolean("invert_zoom", checked).apply()); root.addView(zoom);
        CheckBox snap = new CheckBox(this); snap.setText("Snap targeting cursor to nearby visible units"); snap.setChecked(preferences.getBoolean("snap", true)); snap.setOnCheckedChangeListener((v, checked) -> preferences.edit().putBoolean("snap", checked).apply()); root.addView(snap);
        String[] labels = {"A — Select / Confirm", "B — Cancel", "X — Attack-move", "Y — Stop", "LB — Groups", "RB — Minimap", "Start — Pause", "View — Command deck"};
        int[] codes = {KeyEvent.KEYCODE_BUTTON_A, KeyEvent.KEYCODE_BUTTON_B, KeyEvent.KEYCODE_BUTTON_X, KeyEvent.KEYCODE_BUTTON_Y, KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1, KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_SELECT};
        for (int i = 0; i < codes.length; i++) {
            final int target = codes[i]; final String label = labels[i];
            Button button = new Button(this); button.setText("Map " + label); button.setOnClickListener(v -> capture(target, label)); root.addView(button);
        }
        Button reset = new Button(this); reset.setText("Restore controller defaults"); reset.setOnClickListener(v -> { preferences.edit().clear().apply(); recreate(); }); root.addView(reset);
        ScrollView scroll = new ScrollView(this); scroll.addView(root); setContentView(scroll);
    }
    void slider(LinearLayout root, String label, String key, int min, int max, int value) {
        TextView caption = new TextView(this); caption.setText(label + ": " + preferences.getInt(key, value) + "%"); root.addView(caption);
        SeekBar slider = new SeekBar(this); slider.setMax(max - min); slider.setProgress(preferences.getInt(key, value) - min);
        slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar bar, int progress, boolean user) { int current = progress + min; caption.setText(label + ": " + current + "%"); if (user) preferences.edit().putInt(key, current).apply(); }
            public void onStartTrackingTouch(SeekBar bar) {}
            public void onStopTrackingTouch(SeekBar bar) {}
        }); root.addView(slider);
    }
    void capture(int target, String label) {
        AlertDialog dialog = new AlertDialog.Builder(this).setTitle(label).setMessage("Press the physical button to use for this action. Existing bindings are swapped so every action stays available.").setNegativeButton("Cancel", null).create();
        dialog.setOnKeyListener((d, code, event) -> {
            if (event.getAction() != KeyEvent.ACTION_DOWN || !KeyEvent.isGamepadButton(code)) return false;
            int previousAction = preferences.getInt("button_" + code, code);
            int previousPhysical = target;
            for (int c = KeyEvent.KEYCODE_BUTTON_A; c <= KeyEvent.KEYCODE_BUTTON_MODE; c++) if (preferences.getInt("button_" + c, c) == target) { previousPhysical = c; break; }
            preferences.edit().putInt("button_" + previousPhysical, previousAction).putInt("button_" + code, target).apply(); d.dismiss(); return true;
        }); dialog.show();
    }
}
