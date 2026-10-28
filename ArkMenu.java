package com.fstudio.arkmenu;

import android.app.Activity;
import android.graphics.Color;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;

/**
 * In-game menu drawn INSIDE the game's own Activity (no overlay permission, no Service,
 * no manifest edits). Start it with one call from GameActivity.onCreate:
 *     ArkMenu.start(this);
 */
public final class ArkMenu {
    private static boolean started;

    private final Activity act;
    private FrameLayout root;
    private Button bubble;
    private LinearLayout panel, content;
    private EditText lvl, qty, console;

    private ArkMenu(Activity a) { act = a; }

    /** Safe to call more than once. Waits a few seconds so the engine finishes setting up its own views. */
    public static void start(final Activity a) {
        if (started) return;
        started = true;
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            public void run() {
                try { new ArkMenu(a).build(); }
                catch (Throwable t) { started = false; }   // never crash the game
            }
        }, 6000);
    }

    // ------------------------------------------------------------------ UI
    private void build() {
        root = new FrameLayout(act);

        bubble = new Button(act);
        bubble.setText("M"); bubble.setAllCaps(false);
        FrameLayout.LayoutParams bp = new FrameLayout.LayoutParams(130, 130);
        bubble.setLayoutParams(bp);
        bubble.setX(20); bubble.setY(300);
        bubble.setOnTouchListener(new View.OnTouchListener() {
            float dx, dy; boolean moved;
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        dx = v.getX() - e.getRawX(); dy = v.getY() - e.getRawY(); moved = false; return true;
                    case MotionEvent.ACTION_MOVE:
                        if (Math.abs(v.getX() - (e.getRawX() + dx)) > 10 || Math.abs(v.getY() - (e.getRawY() + dy)) > 10) moved = true;
                        v.setX(e.getRawX() + dx); v.setY(e.getRawY() + dy); return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) panel.setVisibility(panel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
                        return true;
                }
                return false;
            }
        });

        panel = new LinearLayout(act);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(0xEE101418);
        panel.setPadding(12, 12, 12, 12);

        HorizontalScrollView tabScroll = new HorizontalScrollView(act);
        LinearLayout tabs = new LinearLayout(act);
        tabScroll.addView(tabs);
        panel.addView(tabScroll);

        LinearLayout fields = new LinearLayout(act);
        lvl = field("150", "Level"); qty = field("100", "Qty");
        fields.addView(lvl); fields.addView(qty);
        panel.addView(fields);

        ScrollView sv = new ScrollView(act);
        content = new LinearLayout(act);
        content.setOrientation(LinearLayout.VERTICAL);
        sv.addView(content);
        panel.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

        for (final String t : MenuData.TABS) {
            Button tb = new Button(act); tb.setText(t); tb.setAllCaps(false);
            tb.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showTab(t); }});
            tabs.addView(tb);
        }

        int sw = act.getResources().getDisplayMetrics().widthPixels;
        int sh = act.getResources().getDisplayMetrics().heightPixels;
        FrameLayout.LayoutParams pp = new FrameLayout.LayoutParams(Math.min(sw, 900), (int) (sh * 0.6f), Gravity.CENTER);
        panel.setLayoutParams(pp);
        panel.setVisibility(View.GONE);

        root.addView(panel);
        root.addView(bubble);
        act.addContentView(root, new ViewGroup.LayoutParams(-1, -1));
        showTab("Player");
    }

    private EditText field(String def, String hint) {
        EditText e = new EditText(act);
        e.setText(def); e.setHint(hint); e.setTextColor(Color.WHITE);
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        return e;
    }

    private Map<String, String> vars() {
        Map<String, String> m = new HashMap<String, String>();
        m.put("LVL", lvl.getText().toString());
        m.put("QTY", qty.getText().toString());
        return m;
    }

    private void addButton(String label, final Runnable r) {
        Button b = new Button(act); b.setText(label); b.setAllCaps(false);
        b.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { r.run(); }});
        content.addView(b);
    }

    private void send(String c) { NativeBridge.exec(act, c); }

    private void commandList(String[][] rows) {
        for (final String[] r : rows)
            addButton(MenuData.fill(r[0], vars()), new Runnable() { public void run() {
                send(MenuData.fill(r[1], vars()));
            }});
    }

    private void showTab(String tab) {
        content.removeAllViews();
        if (tab.equals("Player")) commandList(MenuData.PLAYER);
        else if (tab.equals("Dinos")) {
            for (final String[] d : MenuData.DINOS)
                addButton("Spawn tamed: " + d[0], new Runnable() { public void run() {
                    for (String c : MenuData.SPAWN_TAMED)
                        send(MenuData.fill(c.replace("{CLASS}", d[1]), vars()));
                }});
            commandList(MenuData.DINO_ACTIONS);
        }
        else if (tab.equals("Items")) { commandList(MenuData.ITEMS); commandList(MenuData.ITEM_ACTIONS); }
        else if (tab.equals("World")) commandList(MenuData.WORLD);
        else if (tab.equals("Teleport")) teleportTab();
        else if (tab.equals("Console")) consoleTab();
    }

    private void teleportTab() {
        for (final String[] t : MenuData.TELEPORTS)
            addButton(t[0], new Runnable() { public void run() { send(MenuData.tpCmd(t[1], t[2], t[3])); }});
        final EditText xyz = new EditText(act);
        xyz.setHint("x y z"); xyz.setTextColor(Color.WHITE);
        content.addView(xyz);
        addButton("Go to x y z", new Runnable() { public void run() {
            send("cheat SetPlayerPos " + xyz.getText().toString().trim());
        }});
        addButton("Backup save (Force Save + zip)", new Runnable() { public void run() { backup(); }});
    }

    private void consoleTab() {
        console = new EditText(act);
        console.setHint("cheat ..."); console.setTextColor(Color.WHITE);
        content.addView(console);
        addButton("Run", new Runnable() { public void run() { send(console.getText().toString()); }});
    }

    // ------------------------------------------------------------------ Backup
    /** Runs inside the game process, so the game's own folders are readable (no Scoped Storage problem).
     *  1) copy to <game files>/ArkBackup/<time>   2) zip into Downloads/ArkBackup via MediaStore (Android 10+). */
    private void backup() {
        send("cheat SaveWorld");
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() { public void run() {
            new Thread(new Runnable() { public void run() { doBackup(); }}).start();
        }}, 3000);
    }

    private void doBackup() {
        try {
            File ext = act.getExternalFilesDir(null);
            File root = ext != null ? ext : act.getFilesDir();
            File dst = new File(root, "ArkBackup/" + System.currentTimeMillis());
            int n = 0;
            File[] srcs = new File[]{ act.getFilesDir(), ext };
            for (File src : srcs) {
                if (src == null || !src.exists()) continue;
                File[] kids = src.listFiles();
                if (kids == null) continue;
                for (File k : kids) {
                    if (k.getName().equals("ArkBackup")) continue;
                    n += copy(k, new File(dst, (src == ext ? "ext/" : "int/") + k.getName()));
                }
            }
            if (n == 0) { toast("Backup failed: no readable files"); return; }
            String where = "Saved copy: " + dst;
            if (Build.VERSION.SDK_INT >= 29) { zipToDownloads(dst); where += "\nZip: Downloads/ArkBackup"; }
            toast(where);
        } catch (Exception e) { toast("Backup failed: " + e.getMessage()); }
    }

    private int copy(File s, File d) throws IOException {
        if (s.isDirectory()) {
            File[] fs = s.listFiles(); int c = 0;
            if (fs == null) return 0;
            d.mkdirs();
            for (File f : fs) c += copy(f, new File(d, f.getName()));
            return c;
        }
        d.getParentFile().mkdirs();
        InputStream in = new FileInputStream(s);
        OutputStream out = new FileOutputStream(d);
        try {
            byte[] buf = new byte[65536]; int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } finally { in.close(); out.close(); }
        return 1;
    }

    private void zipToDownloads(File dir) throws IOException {
        android.content.ContentValues v = new android.content.ContentValues();
        v.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, "ark_backup_" + dir.getName() + ".zip");
        v.put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/zip");
        v.put(android.provider.MediaStore.Downloads.RELATIVE_PATH, "Download/ArkBackup");
        android.net.Uri u = act.getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
        if (u == null) throw new IOException("MediaStore insert failed");
        java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(act.getContentResolver().openOutputStream(u));
        try { zipDir(dir, dir.getPath().length() + 1, z); } finally { z.close(); }
    }

    private void zipDir(File f, int cut, java.util.zip.ZipOutputStream z) throws IOException {
        if (f.isDirectory()) { File[] k = f.listFiles(); if (k != null) for (File c : k) zipDir(c, cut, z); return; }
        z.putNextEntry(new java.util.zip.ZipEntry(f.getPath().substring(cut)));
        InputStream in = new FileInputStream(f);
        try {
            byte[] buf = new byte[65536]; int n;
            while ((n = in.read(buf)) > 0) z.write(buf, 0, n);
        } finally { in.close(); }
        z.closeEntry();
    }

    private void toast(final String m) {
        act.runOnUiThread(new Runnable() { public void run() { Toast.makeText(act, m, Toast.LENGTH_LONG).show(); }});
    }
}
