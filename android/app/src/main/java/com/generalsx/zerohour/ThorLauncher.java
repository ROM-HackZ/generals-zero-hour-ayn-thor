package com.generalsx.zerohour;
import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.graphics.Color;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

// GeneralsX @feature Codex 14/10/2026 Offline controller-accessible entry point.
public final class ThorLauncher extends Activity {
    TextView status;
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(60, 35, 60, 35); root.setBackgroundColor(Color.rgb(12, 21, 28));
        TextView title = new TextView(this); title.setText("THOR RTS"); title.setTextColor(Color.rgb(140, 227, 199)); title.setTextSize(28); root.addView(title);
        status = new TextView(this); status.setTextColor(Color.WHITE); status.setTextSize(17); root.addView(status);
        Button play = new Button(this); play.setText("Play"); play.setOnClickListener(v -> launch()); root.addView(play);
        Button settings = new Button(this); settings.setText("Engine settings"); settings.setOnClickListener(v -> startActivity(new Intent(this, SetupActivity.class))); root.addView(settings);
        Button controls = new Button(this); controls.setText("Controller settings / button check"); controls.setOnClickListener(v -> startActivity(new Intent(this, ThorSettingsActivity.class))); root.addView(controls);
        TextView help = new TextView(this); help.setText("Left stick: camera · Right stick: zoom\nA: select/order · B: cancel · RT: commands · LB: groups\nRB: minimap · View: command deck · Start: pause"); help.setTextColor(Color.LTGRAY); help.setTextSize(16); root.addView(help);
        setContentView(root); play.requestFocus();
    }
    protected void onResume() { super.onResume(); check(); }
    File data() { return new File(getExternalFilesDir(null), "GameData"); }
    boolean check() {
        File game = data(); File base = new File(game, "ZH_Generals");
        String[] needed = {"INIZH.big", "TexturesZH.big", "W3DZH.big", "MapsZH.big", "WindowZH.big"};
        StringBuilder missing = new StringBuilder();
        for (String name : needed) if (!new File(game, name).isFile()) missing.append(name).append(" ");
        if (!new File(base, "INI.big").isFile()) missing.append("ZH_Generals/INI.big");
        if (missing.length() != 0) { status.setText("Game data needed: " + missing + "\nRun the PC deployment script, then reopen this screen."); return false; }
        status.setText("Zero Hour data ready. Use the D-pad and A to start."); return true;
    }
    void launch() {
        if (!check()) return;
        File game = data();
        getSharedPreferences(SetupActivity.PREFS_NAME, MODE_PRIVATE).edit()
            .putString(SetupActivity.PREF_GAME_PATH, game.getAbsolutePath())
            .putString(SetupActivity.PREF_BASE_GENERALS_PATH, new File(game, "ZH_Generals").getAbsolutePath()).apply();
        try {
            write("gamedata_path.txt", game.getAbsolutePath());
            write("generals_base_path.txt", new File(game, "ZH_Generals").getAbsolutePath());
            File runtime = getExternalFilesDir(null);
            try (FileWriter w = new FileWriter(new File(runtime, "render_backend.cfg"))) { w.write("gles\n"); }
            startActivity(new Intent(this, GeneralsZHActivity.class));
        } catch (IOException e) { status.setText("Could not prepare game paths: " + e.getMessage()); }
    }
    void write(String name, String value) throws IOException { try (FileWriter out = new FileWriter(new File(getFilesDir(), name))) { out.write(value + "\n"); } }
    @Override public boolean dispatchKeyEvent(android.view.KeyEvent event) {
        if (event.getKeyCode() == android.view.KeyEvent.KEYCODE_BUTTON_A) {
            if (event.getAction() == android.view.KeyEvent.ACTION_UP && getCurrentFocus() != null) getCurrentFocus().performClick();
            return true;
        }
        return super.dispatchKeyEvent(event);
    }
}
