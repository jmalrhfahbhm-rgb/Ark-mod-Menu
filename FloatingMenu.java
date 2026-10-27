package com.fstudio.arkmenu;

import android.app.Service;
import android.content.Intent;
import android.graphics.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.util.*;

/** Overlay service: draggable bubble -> tabbed panel. Needs "Display over other apps". */
public class FloatingMenu extends Service {
    private static final String PKG = "com.studiowildcard.wardrumstudios.ark";
    private WindowManager wm;
    private View bubble, panel;
    private LinearLayout content;
    private EditText lvl, qty, console;

    @Override public IBinder onBind(Intent i) { return null; }

    @Override public void onCreate() {
        super.onCreate();
        if (!Settings.canDrawOverlays(this)) { stopSelf(); return; }
        wm = (WindowManager) getSystemService(WINDOW_SERVICE);
        buildBubble();
        buildPanel();
    }

    private WindowManager.LayoutParams lp(int w, int h, boolean focusable) {
        int flags = WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        if (!focusable) flags |= WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE;
        return new WindowManager.LayoutParams(w, h,
            Build.VERSION.SDK_INT >= 26 ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                                        : WindowManager.LayoutParams.TYPE_PHONE,
            flags, PixelFormat.TRANSLUCENT);
    }

    private void buildBubble() {
        Button b = new Button(this);
        b.setText("M"); b.setAllCaps(false);
        final WindowManager.LayoutParams p = lp(130, 130, false);
        p.gravity = Gravity.TOP | Gravity.START; p.x = 20; p.y = 300;
        b.setOnTouchListener(new View.OnTouchListener() {
            int sx, sy; float tx, ty; boolean moved;
            @Override public boolean onTouch(View v, MotionEvent e) {
                switch (e.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        sx = p.x; sy = p.y; tx = e.getRawX(); ty = e.getRawY(); moved = false; return true;
                    case MotionEvent.ACTION_MOVE:
                        p.x = sx + (int)(e.getRawX() - tx); p.y = sy + (int)(e.getRawY() - ty);
                        if (Math.abs(e.getRawX() - tx) > 10 || Math.abs(e.getRawY() - ty) > 10) moved = true;
                        wm.updateViewLayout(v, p); return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) panel.setVisibility(panel.getVisibility() == View.VISIBLE ? View.GONE : View.VISIBLE);
                        return true;
                }
                return false;
            }
        });
        bubble = b; wm.addView(b, p);
    }

    private void buildPanel() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xEE101418);
        root.setPadding(12, 12, 12, 12);

        HorizontalScrollView tabScroll = new HorizontalScrollView(this);
        LinearLayout tabs = new LinearLayout(this);
        tabScroll.addView(tabs);
        root.addView(tabScroll);

        LinearLayout fields = new LinearLayout(this);
        lvl = field("150", "Level"); qty = field("100", "Qty");
        fields.addView(lvl); fields.addView(qty);
        root.addView(fields);

        ScrollView sv = new ScrollView(this);
        content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        sv.addView(content);
        root.addView(sv, new LinearLayout.LayoutParams(-1, 0, 1f));

        for (final String t : MenuData.TABS) {
            Button tb = new Button(this); tb.setText(t); tb.setAllCaps(false);
            tb.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { showTab(t); }});
            tabs.addView(tb);
        }
        int w = Math.min(getResources().getDisplayMetrics().widthPixels, 900);
        int h = (int)(getResources().getDisplayMetrics().heightPixels * 0.6f);
        WindowManager.LayoutParams p = lp(w, h, true);   // focusable so the text boxes work
        p.gravity = Gravity.CENTER;
        panel = root; panel.setVisibility(View.GONE);
        wm.addView(panel, p);
        showTab("Player");
    }

    private EditText field(String def, String hint) {
        EditText e = new EditText(this);
        e.setText(def); e.setHint(hint); e.setTextColor(Color.WHITE);
        e.setInputType(android.text.InputType.TYPE_CLASS_NUMBER);
        e.setLayoutParams(new LinearLayout.LayoutParams(0, -2, 1f));
        return e;
    }

    private Map<String,String> vars() {
        Map<String,String> m = new HashMap<>();
        m.put("LVL", lvl.getText().toString()); m.put("QTY", qty.getText().toString());
        return m;
    }

    private void addButton(String label, final Runnable r) {
        Button b = new Button(this); b.setText(label); b.setAllCaps(false);
        b.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { r.run(); }});
        content.addView(b);
    }

    private void commandList(String[][] rows) {
        for (final String[] r : rows)
            addButton(MenuData.fill(r[0], vars()), new Runnable() { public void run() {
                send(MenuData.fill(r[1], vars()));
            }});
    }

    private void showTab(String tab) {
        content.removeAllViews();
        switch (tab) {
            case "Player":   commandList(MenuData.PLAYER); break;
            case "Dinos":
                for (final String[] d : MenuData.DINOS)
                    addButton("Spawn tamed: " + d[0], new Runnable() { public void run() {
                        for (String c : MenuData.SPAWN_TAMED)
                            send(MenuData.fill(c.replace("{CLASS}", d[1]), vars()));
                    }});
                commandList(MenuData.DINO_ACTIONS); break;
            case "Items":    commandList(MenuData.ITEMS); commandList(MenuData.ITEM_ACTIONS); break;
            case "World":    commandList(MenuData.WORLD); break;
            case "Teleport": teleportTab(); break;
            case "Console":  consoleTab(); break;
        }
    }

    private void teleportTab() {
        for (final String[] t : MenuData.TELEPORTS)
            addButton(t[0], new Runnable() { public void run() {
                send(MenuData.tpCmd(t[1], t[2], t[3]));
            }});
        final EditText xyz = new EditText(this);
        xyz.setHint("x y z"); xyz.setTextColor(Color.WHITE); content.addView(xyz);
        addButton("Go to x y z", new Runnable() { public void run() {
            send("cheat SetPlayerPos " + xyz.getText().toString().trim());
        }});
        addButton("Backup save (Force Save + zip)", new Runnable() { public void run() { backup(); }});
    }

    private void consoleTab() {
        console = new EditText(this);
        console.setHint("cheat ..."); console.setTextColor(Color.WHITE);
        content.addView(console);
        addButton("Run", new Runnable() { public void run() {
            send(console.getText().toString());
        }});
    }

    /** Backup that works on Android 11+ without root, ASSUMING this menu runs INSIDE the game's own
     *  process (loaded via the patched APK). Then getExternalFilesDir()/getFilesDir() belong to the game
     *  itself, so Scoped Storage does not block reading its save files.
     *  Step 1: copy to <game files>/ArkBackup/<time>  (no permission needed, survives restarts).
     *  Step 2: zip that copy into Downloads/ArkBackup via MediaStore (API 29+), no permission needed.
     *  If the menu runs as a separate app, it cannot read the game's save, and this will report "no access". */
    private void backup() {
        send("cheat SaveWorld");
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() { public void run() {
            new Thread(new Runnable() { public void run() { doBackup(); }}).start();
        }}, 3000);
    }

    private void doBackup() {
        try {
            File ext = getExternalFilesDir(null);
            File root = ext != null ? ext : getFilesDir();
            File dst = new File(root, "ArkBackup/" + System.currentTimeMillis());
            int n = 0;
            for (File src : new File[]{ getFilesDir(), ext }) {
                if (src == null || !src.exists()) continue;
                File[] kids = src.listFiles();
                if (kids == null) continue;
                for (File k : kids) {
                    if (k.getName().equals("ArkBackup")) continue;       // never copy backups into backups
                    n += copy(k, new File(dst, (src == ext ? "ext/" : "int/") + k.getName()));
                }
            }
            if (n == 0) { toast("Backup failed: no files readable (is the menu running inside the game?)"); return; }
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
        try (InputStream in = new FileInputStream(s); OutputStream out = new FileOutputStream(d)) {
            byte[] buf = new byte[65536]; int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        }
        return 1;
    }

    private void zipToDownloads(File dir) throws IOException {
        android.content.ContentValues v = new android.content.ContentValues();
        v.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, "ark_backup_" + dir.getName() + ".zip");
        v.put(android.provider.MediaStore.Downloads.MIME_TYPE, "application/zip");
        v.put(android.provider.MediaStore.Downloads.RELATIVE_PATH, "Download/ArkBackup");
        android.net.Uri u = getContentResolver().insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, v);
        if (u == null) throw new IOException("MediaStore insert failed");
        try (java.util.zip.ZipOutputStream z = new java.util.zip.ZipOutputStream(getContentResolver().openOutputStream(u))) {
            zipDir(dir, dir.getPath().length() + 1, z);
        }
    }

    private void zipDir(File f, int cut, java.util.zip.ZipOutputStream z) throws IOException {
        if (f.isDirectory()) { File[] k = f.listFiles(); if (k != null) for (File c : k) zipDir(c, cut, z); return; }
        z.putNextEntry(new java.util.zip.ZipEntry(f.getPath().substring(cut)));
        try (InputStream in = new FileInputStream(f)) {
            byte[] buf = new byte[65536]; int n;
            while ((n = in.read(buf)) > 0) z.write(buf, 0, n);
        }
        z.closeEntry();
    }

    private void send(String c) { NativeBridge.exec(this, c); }

    private void toast(String m) { Toast.makeText(this, m, Toast.LENGTH_LONG).show(); }

    @Override public void onDestroy() {
        super.onDestroy();
        if (bubble != null) wm.removeView(bubble);
        if (panel != null) wm.removeView(panel);
    }
}
