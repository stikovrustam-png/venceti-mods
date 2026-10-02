package com.venceti.mods;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.provider.Settings;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FloatingBubbleService extends Service {

    private static final String CHANNEL_ID = "venceti_overlay";
    private static final int NOTIF_ID = 4242;
    private static final String FILES_PATH = "/storage/emulated/0/Download/Venceti Mods/Files";
    private static final String LINK_CREATOR = "https://t.me/babycores";
    private static final String LINK_CHANNEL = "https://t.me/funky_sb";

    // palette
    private static final int C_BG       = Color.parseColor("#0E0E10");
    private static final int C_BTN      = Color.parseColor("#16161A");
    private static final int C_BTN_STROKE = Color.parseColor("#26262E");
    private static final int C_ACCENT   = Color.parseColor("#2F80FF");
    private static final int C_TEXT     = Color.parseColor("#DCDCDC");
    private static final int C_MUTED    = Color.parseColor("#6C6C6C");

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private WindowManager wm;
    private FrameLayout bubble;
    private WindowManager.LayoutParams bubbleLp;
    private LinearLayout panel;
    private WindowManager.LayoutParams panelLp;

    private String mode = "apk";
    private int screen = 0; // 0 = main, 1 = files, 2 = info
    private volatile boolean busy = false;
    private boolean holdTriggered = false;

    private final Runnable holdRunnable = new Runnable() {
        @Override public void run() { holdTriggered = true; killAll(); }
    };

    // ---------------------------------------------------------------- lifecycle

    @Override
    public void onCreate() {
        super.onCreate();
        try { wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE); }
        catch (Throwable ignored) { }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForegroundSafe();
        try {
            if (intent != null) {
                String m = intent.getStringExtra("mode");
                if (m != null) mode = m;
            }
            if (!Settings.canDrawOverlays(this)) { killAll(); return START_NOT_STICKY; }
            if (bubble == null) createBubble();
            else if (panel != null) buildScreen();
        } catch (Throwable t) { killAll(); }
        return START_NOT_STICKY;
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    @Override
    public void onDestroy() {
        try { ui.removeCallbacksAndMessages(null); } catch (Throwable ignored) { }
        destroyOverlay();
        try { io.shutdownNow(); } catch (Throwable ignored) { }
        super.onDestroy();
    }

    private void startForegroundSafe() {
        try {
            Notification n = buildNotification();
            if (Build.VERSION.SDK_INT >= 34) startForeground(NOTIF_ID, n, 0x40000000);
            else startForeground(NOTIF_ID, n);
        } catch (Throwable ignored) { }
    }

    private Notification buildNotification() {
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(new NotificationChannel(
                    CHANNEL_ID, "Venceti Mods", NotificationManager.IMPORTANCE_LOW));
            b = new Notification.Builder(this, CHANNEL_ID);
        } else b = new Notification.Builder(this);
        b.setContentTitle("Venceti Mods").setContentText("Overlay active")
         .setSmallIcon(android.R.drawable.ic_menu_manage).setOngoing(true);
        return b.build();
    }

    // ---------------------------------------------------------------- teardown

    private void hidePanel() {
        LinearLayout p = panel;
        panel = null; panelLp = null;
        if (p != null && wm != null) {
            try { wm.removeView(p); } catch (Throwable ignored) { }
        }
    }

    private void destroyOverlay() {
        hidePanel();
        FrameLayout b = bubble;
        bubble = null; bubbleLp = null;
        if (b != null && wm != null) {
            try { wm.removeView(b); } catch (Throwable ignored) { }
        }
    }

    private void killAll() {
        try { ui.removeCallbacksAndMessages(null); } catch (Throwable ignored) { }
        destroyOverlay();
        try { stopForeground(Service.STOP_FOREGROUND_REMOVE); } catch (Throwable ignored) { }
        try { stopSelf(); } catch (Throwable ignored) { }
    }

    // ---------------------------------------------------------------- bubble

    private int overlayType() {
        return Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
    }

    private void createBubble() {
        if (wm == null || bubble != null) return;
        final int size = dp(56);

        TextView circle = new TextView(this);
        circle.setText("apk".equals(mode) ? "a" : "g");
        circle.setTextColor(C_ACCENT);
        circle.setTextSize(22);
        circle.setTypeface(Typeface.DEFAULT_BOLD);
        circle.setGravity(Gravity.CENTER);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.parseColor("#000000"));
        bg.setStroke(dp(2), C_ACCENT);
        circle.setBackground(bg);

        FrameLayout wrap = new FrameLayout(this);
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(size, size);
        circle.setLayoutParams(flp);
        wrap.addView(circle);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                size, size, overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = dm.widthPixels - size - dp(12);
        lp.y = dm.heightPixels / 3;

        wrap.setOnTouchListener(new View.OnTouchListener() {
            private int sx, sy;
            private float tx, ty;
            private boolean moved;
            @Override public boolean onTouch(View v, MotionEvent e) {
                try {
                    WindowManager.LayoutParams p = bubbleLp;
                    if (p == null || wm == null || bubble == null) return false;
                    switch (e.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            sx = p.x; sy = p.y;
                            tx = e.getRawX(); ty = e.getRawY();
                            moved = false;
                            return true;
                        case MotionEvent.ACTION_MOVE: {
                            float dx = e.getRawX() - tx;
                            float dy = e.getRawY() - ty;
                            if (!moved && (Math.abs(dx) > dp(8) || Math.abs(dy) > dp(8))) moved = true;
                            if (moved) {
                                p.x = (int) (sx + dx);
                                p.y = (int) (sy + dy);
                                wm.updateViewLayout(bubble, p);
                            }
                            return true;
                        }
                        case MotionEvent.ACTION_UP:
                            if (!moved) togglePanel();
                            return true;
                    }
                } catch (Throwable ignored) { }
                return true;
            }
        });

        try { wm.addView(wrap, lp); } catch (Throwable t) { return; }
        bubble = wrap;
        bubbleLp = lp;
    }

    // ---------------------------------------------------------------- panel

    private void togglePanel() {
        try { if (panel != null) hidePanel(); else showPanel(); }
        catch (Throwable ignored) { }
    }

    private void showPanel() {
        if (wm == null || panel != null) return;
        screen = 0;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(14), dp(16), dp(16));
        root.setBackground(rounded(C_BG, 16, Color.parseColor("#2A2A2A"), 1));

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                dp(280), ViewGroup.LayoutParams.WRAP_CONTENT, overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.CENTER;

        panel = root;
        panelLp = lp;
        buildScreen();
        try { wm.addView(root, lp); }
        catch (Throwable t) { panel = null; panelLp = null; }
    }

    private void buildScreen() {
        if (panel == null) return;
        try {
            panel.removeAllViews();
            switch (screen) {
                case 1: buildFiles(); break;
                case 2: buildInfo(); break;
                default: buildMain(); break;
            }
        } catch (Throwable ignored) { }
    }

    private void buildMain() {
        addHeader("Mods Panel", false);
        String src = "apk".equals(mode) ? "APK" : "Google Play";
        panel.addView(text("Source: " + src, 11, C_MUTED, false), lp(2, 12));

        TextView dl = iconButton("Download", ICON_DOWNLOAD, C_ACCENT, true);
        dl.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { onDownloadClick(v); }
        });
        panel.addView(dl, lp(0, 10));

        TextView files = iconButton("Files", ICON_FOLDER, C_BTN, false);
        files.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { screen = 1; buildScreen(); }
        });
        panel.addView(files, lp(0, 8));

        TextView tg = iconButton("Telegram Creator", ICON_SEND, C_BTN, false);
        tg.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openUrl(LINK_CREATOR); }
        });
        panel.addView(tg, lp(0, 8));
    }

    private void buildFiles() {
        addHeader("Files", true);
        panel.addView(text("Download/Venceti Mods/Files", 11, C_MUTED, false), lp(2, 12));

        ScrollView sv = new ScrollView(this);
        sv.setBackground(rounded(C_BTN, 10, C_BTN_STROKE, 1));
        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(10), dp(8), dp(10), dp(8));
        sv.addView(list, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams svp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(150));
        panel.addView(sv, svp);
        list.addView(text("Loading…", 12, C_MUTED, false));

        try {
            io.execute(new Runnable() {
                @Override public void run() {
                    final String out = readFiles();
                    ui.post(new Runnable() {
                        @Override public void run() { fillFiles(list, out); }
                    });
                }
            });
        } catch (Throwable t) { fillFiles(list, null); }

        TextView info = iconButton("Information", ICON_INFO, C_BTN, false);
        info.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { screen = 2; buildScreen(); }
        });
        panel.addView(info, lp(0, 12));

        TextView back = iconButton("Back", ICON_BACK, C_BTN, false);
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { screen = 0; buildScreen(); }
        });
        panel.addView(back, lp(0, 8));
    }

    private void buildInfo() {
        addHeader("Information", true);
        panel.addView(text("Venceti Mods · v1.0", 12, C_MUTED, false), lp(2, 12));

        TextView ch = iconButton("Telegram Channel", ICON_SEND, C_BTN, false);
        ch.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openUrl(LINK_CHANNEL); }
        });
        panel.addView(ch, lp(0, 6));

        TextView dm = iconButton("Telegram (DM)", ICON_SEND, C_BTN, false);
        dm.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openUrl(LINK_CREATOR); }
        });
        panel.addView(dm, lp(0, 8));

        TextView back = iconButton("Back to Files", ICON_BACK, C_BTN, false);
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { screen = 1; buildScreen(); }
        });
        panel.addView(back, lp(0, 12));
    }

    private void fillFiles(LinearLayout list, String out) {
        try {
            if (panel == null || screen != 1) return;
            list.removeAllViews();
            if (out == null) {
                list.addView(text("Cannot read folder", 12, Color.parseColor("#D64545"), false));
                list.addView(text("Check Shizuku / storage access", 11, C_MUTED, false));
                return;
            }
            int count = 0;
            for (String line : out.split("\n")) {
                String name = line.trim();
                if (name.isEmpty() || name.startsWith("ERR")) continue;
                list.addView(text("• " + name, 12, C_TEXT, false));
                if (++count >= 100) break;
            }
            if (count == 0) list.addView(text("(empty)", 12, C_MUTED, false));
        } catch (Throwable ignored) { }
    }

    private String readFiles() {
        try {
            java.io.File dir = new java.io.File(FILES_PATH);
            String[] arr = dir.list();
            if (arr != null) {
                java.util.Arrays.sort(arr);
                StringBuilder sb = new StringBuilder();
                for (String s : arr) sb.append(s).append('\n');
                return sb.toString();
            }
        } catch (Throwable ignored) { }
        try {
            java.lang.reflect.Method m = ModInstaller.class.getMethod("listFiles");
            Object o = m.invoke(null);
            return o == null ? null : o.toString();
        } catch (Throwable t) { return null; }
    }

    // ---------------------------------------------------------------- download

    private void onDownloadClick(final View v) {
        if (busy) return;
        busy = true;

        // visual feedback: fade
        try { v.animate().alpha(0.4f).setDuration(120).withEndAction(new Runnable() {
            @Override public void run() { v.animate().alpha(1f).setDuration(180).start(); }
        }).start(); } catch (Throwable ignored) { }

        final String m = mode;
        try {
            io.execute(new Runnable() {
                @Override public void run() {
                    String res;
                    try { res = ModInstaller.install(getApplicationContext(), m); }
                    catch (Throwable t) { res = "ERROR: " + t.getMessage(); }
                    final String r = (res == null) ? "ERROR: empty result" : res;
                    ui.post(new Runnable() {
                        @Override public void run() { finishDownload(r); }
                    });
                }
            });
        } catch (Throwable t) {
            busy = false;
            toast("✗ Не удалось запустить установку");
        }
    }

    private void finishDownload(String r) {
        busy = false;
        boolean ok = "OK".equals(r);
        toast(ok ? "✓ Установлено" : "✗ " + r);
    }

    // ---------------------------------------------------------------- icons

    // simple vector icons drawn on the fly
    private static final int ICON_DOWNLOAD = 1;
    private static final int ICON_FOLDER   = 2;
    private static final int ICON_SEND     = 3;
    private static final int ICON_INFO     = 4;
    private static final int ICON_BACK     = 5;

    private Drawable iconDrawable(int type, int color) {
        return new VectorIcon(type, color, dp(18));
    }

    /** Простой Drawable, рисующий SVG-подобные иконки. */
    private static class VectorIcon extends Drawable {
        private final int type;
        private final int color;
        private final int size;
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        VectorIcon(int type, int color, int size) {
            this.type = type; this.color = color; this.size = size;
            p.setColor(color); p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(size / 8f);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
        }
        @Override public void draw(Canvas c) {
            float s = size;
            float cx = s / 2f, cy = s / 2f;
            switch (type) {
                case ICON_DOWNLOAD: {
                    c.drawLine(cx, s * 0.18f, cx, s * 0.68f, p);
                    Path arrow = new Path();
                    arrow.moveTo(s * 0.30f, s * 0.50f);
                    arrow.lineTo(cx, s * 0.72f);
                    arrow.lineTo(s * 0.70f, s * 0.50f);
                    c.drawPath(arrow, p);
                    c.drawLine(s * 0.22f, s * 0.86f, s * 0.78f, s * 0.86f, p);
                    break;
                }
                case ICON_FOLDER: {
                    Path f = new Path();
                    f.moveTo(s * 0.12f, s * 0.30f);
                    f.lineTo(s * 0.42f, s * 0.30f);
                    f.lineTo(s * 0.50f, s * 0.40f);
                    f.lineTo(s * 0.88f, s * 0.40f);
                    f.lineTo(s * 0.88f, s * 0.82f);
                    f.lineTo(s * 0.12f, s * 0.82f);
                    f.close();
                    c.drawPath(f, p);
                    break;
                }
                case ICON_SEND: {
                    Path t = new Path();
                    t.moveTo(s * 0.10f, s * 0.50f);
                    t.lineTo(s * 0.90f, s * 0.15f);
                    t.lineTo(s * 0.60f, s * 0.85f);
                    t.lineTo(s * 0.50f, s * 0.55f);
                    t.lineTo(s * 0.10f, s * 0.50f);
                    t.close();
                    c.drawPath(t, p);
                    break;
                }
                case ICON_INFO: {
                    p.setStyle(Paint.Style.STROKE);
                    c.drawCircle(cx, cy, s * 0.36f, p);
                    p.setStyle(Paint.Style.FILL);
                    c.drawCircle(cx, s * 0.30f, s * 0.05f, p);
                    p.setStyle(Paint.Style.STROKE);
                    c.drawLine(cx, s * 0.44f, cx, s * 0.72f, p);
                    break;
                }
                case ICON_BACK: {
                    c.drawLine(s * 0.20f, cy, s * 0.80f, cy, p);
                    Path a = new Path();
                    a.moveTo(s * 0.20f, cy);
                    a.lineTo(s * 0.42f, s * 0.28f);
                    a.moveTo(s * 0.20f, cy);
                    a.lineTo(s * 0.42f, s * 0.72f);
                    c.drawPath(a, p);
                    break;
                }
            }
        }
        @Override public void setAlpha(int a) { p.setAlpha(a); }
        @Override public void setColorFilter(android.graphics.ColorFilter cf) { p.setColorFilter(cf); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
        @Override public int getIntrinsicWidth() { return size; }
        @Override public int getIntrinsicHeight() { return size; }
    }

    private Drawable iconWithPadding(int iconType, int iconColor, int padDp) {
        int pad = dp(padDp);
        int total = dp(18) + pad * 2;
        LayerDrawable ld = new LayerDrawable(new Drawable[]{
                iconDrawable(iconType, iconColor)
        });
        ld.setLayerInset(0, pad, pad, pad, pad);
        return ld;
    }

    // ---------------------------------------------------------------- button UI

    /** Кнопка с SVG-иконкой слева и текстом справа, по центру. */
    private TextView iconButton(String label, int iconType, int bgColor, boolean primary) {
        TextView t = new TextView(this);
        t.setText("      " + label); // сдвиг под иконку
        t.setTextColor(C_TEXT);
        t.setTextSize(15);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(12), dp(12), dp(12), dp(12));
        t.setBackground(rounded(bgColor, 10, primary ? bgColor : C_BTN_STROKE, 1));
        // иконка через compound drawable
        Drawable ic = iconDrawable(iconType, primary ? Color.WHITE : C_TEXT);
        ic.setBounds(0, 0, dp(18), dp(18));
        t.setCompoundDrawables(ic, null, null, null);
        t.setCompoundDrawablePadding(dp(8));
        t.setClickable(true);
        return t;
    }

    // ---------------------------------------------------------------- header

    private void addHeader(String title, boolean isSub) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView t = text(title, 16, Color.WHITE, true);
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView close = new TextView(this);
        close.setText("✕");
        close.setTextColor(Color.parseColor("#9A9A9A"));
        close.setTextSize(18);
        close.setPadding(dp(10), dp(4), dp(4), dp(4));
        attachCloseHandler(close);
        row.addView(close, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        panel.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    /** Тап — скрывает панель. Удержание 3 сек — убивает кружок. */
    private void attachCloseHandler(final TextView close) {
        close.setOnTouchListener(new View.OnTouchListener() {
            @Override public boolean onTouch(View v, MotionEvent e) {
                try {
                    switch (e.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            holdTriggered = false;
                            close.setTextColor(Color.parseColor("#FF5C5C"));
                            ui.removeCallbacks(holdRunnable);
                            ui.postDelayed(holdRunnable, 3000);
                            return true;
                        case MotionEvent.ACTION_UP:
                            ui.removeCallbacks(holdRunnable);
                            close.setTextColor(Color.parseColor("#9A9A9A"));
                            if (!holdTriggered && inside(v, e)) hidePanel();
                            return true;
                        case MotionEvent.ACTION_CANCEL:
                            ui.removeCallbacks(holdRunnable);
                            close.setTextColor(Color.parseColor("#9A9A9A"));
                            return true;
                    }
                } catch (Throwable ignored) { }
                return true;
            }
        });
    }

    private boolean inside(View v, MotionEvent e) {
        return e.getX() >= 0 && e.getX() <= v.getWidth()
                && e.getY() >= 0 && e.getY() <= v.getHeight();
    }

    // ---------------------------------------------------------------- helpers

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(sp);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private LinearLayout.LayoutParams lp(int topDp, int bottomDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(topDp);
        p.bottomMargin = dp(bottomDp);
        return p;
    }

    private GradientDrawable rounded(int color, int radiusDp, int strokeColor, int strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(radiusDp));
        if (strokeDp > 0) g.setStroke(dp(strokeDp), strokeColor);
        return g;
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void openUrl(String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Throwable t) { toast("Cannot open link"); }
    }

    private void toast(final String msg) {
        ui.post(new Runnable() {
            @Override public void run() {
                try { Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show(); }
                catch (Throwable ignored) { }
            }
        });
    }
}
