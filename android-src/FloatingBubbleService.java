package com.venceti.mods;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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

    private static final int C_BG = Color.parseColor("#F0161622");
    private static final int C_BTN = Color.parseColor("#2A2A3A");
    private static final int C_ACCENT = Color.parseColor("#6C5CE7");
    private static final int C_WARN = Color.parseColor("#E1A100");
    private static final int C_OK = Color.parseColor("#2E9E5B");
    private static final int C_ERR = Color.parseColor("#D64545");
    private static final int C_MUTED = Color.parseColor("#9A9AB0");

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private WindowManager wm;
    private TextView bubble;
    private WindowManager.LayoutParams bubbleLp;
    private LinearLayout panel;
    private WindowManager.LayoutParams panelLp;
    private TextView downloadBtn;

    private String mode = "apk";
    private int screen = 0; // 0 = main, 1 = files, 2 = info
    private volatile boolean busy = false;
    private boolean installing = false;
    private int dots = 0;
    private boolean holdTriggered = false;

    private final Runnable holdRunnable = () -> {
        holdTriggered = true;
        killAll();
    };

    private final Runnable dotsRunnable = new Runnable() {
        @Override
        public void run() {
            if (!installing || downloadBtn == null) return;
            dots = (dots + 1) % 4;
            try {
                downloadBtn.setText("⏳ Installing" + "...".substring(0, dots));
            } catch (Throwable ignored) { }
            ui.postDelayed(this, 350);
        }
    };

    // ---------------------------------------------------------------- lifecycle

    @Override
    public void onCreate() {
        super.onCreate();
        try {
            wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        } catch (Throwable ignored) { }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForegroundSafe();
        try {
            if (intent != null) {
                String m = intent.getStringExtra("mode");
                if (m != null) mode = m;
            }
            if (!Settings.canDrawOverlays(this)) {
                killAll();
                return START_NOT_STICKY;
            }
            if (bubble == null) {
                createBubble();
            } else if (panel != null) {
                buildScreen();
            }
        } catch (Throwable t) {
            killAll();
        }
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

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
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIF_ID, n, 0x40000000); // FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            } else {
                startForeground(NOTIF_ID, n);
            }
        } catch (Throwable ignored) { }
    }

    private Notification buildNotification() {
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) {
                nm.createNotificationChannel(new NotificationChannel(
                        CHANNEL_ID, "Venceti Mods", NotificationManager.IMPORTANCE_LOW));
            }
            b = new Notification.Builder(this, CHANNEL_ID);
        } else {
            b = new Notification.Builder(this);
        }
        b.setContentTitle("Venceti Mods")
                .setContentText("Overlay active")
                .setSmallIcon(android.R.drawable.ic_menu_manage)
                .setOngoing(true);
        return b.build();
    }

    // ---------------------------------------------------------------- teardown

    private void hidePanel() {
        try { ui.removeCallbacks(dotsRunnable); } catch (Throwable ignored) { }
        LinearLayout p = panel;
        panel = null;
        panelLp = null;
        downloadBtn = null;
        if (p != null && wm != null) {
            try { wm.removeView(p); } catch (Throwable ignored) { }
        }
    }

    // idempotent
    private void destroyOverlay() {
        hidePanel();
        TextView b = bubble;
        bubble = null;
        bubbleLp = null;
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

        TextView b = new TextView(this);
        b.setText("V");
        b.setTextColor(Color.WHITE);
        b.setTextSize(22);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setGravity(Gravity.CENTER);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(C_ACCENT);
        bg.setStroke(dp(2), Color.WHITE);
        b.setBackground(bg);

        DisplayMetrics dm = getResources().getDisplayMetrics();
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                size, size, overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        lp.x = dm.widthPixels - size - dp(12);
        lp.y = dm.heightPixels / 3;

        b.setOnTouchListener(new View.OnTouchListener() {
            private int sx, sy;
            private float tx, ty;
            private boolean moved;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                try {
                    WindowManager.LayoutParams p = bubbleLp;
                    if (p == null || wm == null || bubble == null) return false;
                    switch (e.getActionMasked()) {
                        case MotionEvent.ACTION_DOWN:
                            sx = p.x;
                            sy = p.y;
                            tx = e.getRawX();
                            ty = e.getRawY();
                            moved = false;
                            return true;
                        case MotionEvent.ACTION_MOVE: {
                            float dx = e.getRawX() - tx;
                            float dy = e.getRawY() - ty;
                            if (!moved && (Math.abs(dx) > dp(8) || Math.abs(dy) > dp(8))) {
                                moved = true;
                            }
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
                        default:
                            break;
                    }
                } catch (Throwable ignored) { }
                return true;
            }
        });

        wm.addView(b, lp);
        bubble = b;
        bubbleLp = lp;
    }

    // ---------------------------------------------------------------- panel

    private void togglePanel() {
        try {
            if (panel != null) hidePanel();
            else showPanel();
        } catch (Throwable ignored) { }
    }

    private void showPanel() {
        if (wm == null || panel != null) return;
        screen = 0;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(10), dp(14), dp(14));
        root.setBackground(rounded(C_BG, 16, C_ACCENT, 1));

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                dp(280), ViewGroup.LayoutParams.WRAP_CONTENT, overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.CENTER;

        panel = root;
        panelLp = lp;
        buildScreen();
        try {
            wm.addView(root, lp);
        } catch (Throwable t) {
            panel = null;
            panelLp = null;
            downloadBtn = null;
        }
    }

    private void buildScreen() {
        if (panel == null) return;
        try {
            panel.removeAllViews();
            downloadBtn = null;
            switch (screen) {
                case 1: buildFiles(); break;
                case 2: buildInfo(); break;
                default: buildMain(); break;
            }
        } catch (Throwable ignored) { }
    }

    private void buildMain() {
        addHeader("ⓐ Mods Panel");
        String src = "apk".equals(mode) ? "APK" : "Google Play";
        panel.addView(text("Source: " + src, 13, C_MUTED, false), lp(8));

        TextView dl = button(installing ? "⏳ Installing..." : "⬇ Download", C_ACCENT);
        dl.setOnClickListener(v -> onDownloadClick());
        panel.addView(dl, lp(10));
        downloadBtn = dl;
        if (installing) {
            ui.removeCallbacks(dotsRunnable);
            ui.post(dotsRunnable);
        }

        TextView files = button("📁 Files", C_BTN);
        files.setOnClickListener(v -> {
            screen = 1;
            buildScreen();
        });
        panel.addView(files, lp(8));

        TextView tg = button("✈ Telegram Creator", C_BTN);
        tg.setOnClickListener(v -> openUrl(LINK_CREATOR));
        panel.addView(tg, lp(8));
    }

    private void buildFiles() {
        addHeader("📁 Files");
        panel.addView(text("Download/Venceti Mods/Files", 12, C_MUTED, false), lp(6));

        ScrollView sv = new ScrollView(this);
        sv.setBackground(rounded(C_BTN, 10, 0, 0));
        final LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        list.setPadding(dp(10), dp(8), dp(10), dp(8));
        sv.addView(list, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams svp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(150));
        svp.topMargin = dp(8);
        panel.addView(sv, svp);
        list.addView(text("Loading...", 13, C_MUTED, false));

        try {
            io.execute(() -> {
                final String out = readFiles();
                ui.post(() -> fillFiles(list, out));
            });
        } catch (Throwable t) {
            fillFiles(list, null);
        }

        TextView info = button("ⓘ Information", C_BTN);
        info.setOnClickListener(v -> {
            screen = 2;
            buildScreen();
        });
        panel.addView(info, lp(8));

        TextView back = button("← Back", C_BTN);
        back.setOnClickListener(v -> {
            screen = 0;
            buildScreen();
        });
        panel.addView(back, lp(8));
    }

    private void buildInfo() {
        addHeader("ⓘ Information");
        panel.addView(text("Venceti Mods · v1.0", 14, Color.WHITE, false), lp(8));

        TextView ch = button("✈ Telegram Channel", C_BTN);
        ch.setOnClickListener(v -> openUrl(LINK_CHANNEL));
        panel.addView(ch, lp(10));

        TextView dm = button("✈ Telegram (DM)", C_BTN);
        dm.setOnClickListener(v -> openUrl(LINK_CREATOR));
        panel.addView(dm, lp(8));

        TextView back = button("← Back to Files", C_BTN);
        back.setOnClickListener(v -> {
            screen = 1;
            buildScreen();
        });
        panel.addView(back, lp(8));
    }

    private void fillFiles(LinearLayout list, String out) {
        try {
            if (panel == null || screen != 1) return;
            list.removeAllViews();
            if (out == null) {
                list.addView(text("Cannot read folder", 13, C_ERR, false));
                list.addView(text("Check Shizuku / storage access", 12, C_MUTED, false));
                return;
            }
            int count = 0;
            for (String line : out.split("\n")) {
                String name = line.trim();
                if (name.isEmpty() || name.startsWith("ERR")) continue;
                list.addView(text("• " + name, 13, Color.WHITE, false));
                if (++count >= 100) break;
            }
            if (count == 0) {
                list.addView(text("(empty)", 13, C_MUTED, false));
            }
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
        } catch (Throwable t) {
            return null;
        }
    }

    // ---------------------------------------------------------------- download

    private void onDownloadClick() {
        if (busy) return;
        busy = true;
        installing = true;
        dots = 0;
        if (downloadBtn != null) setBtn(downloadBtn, "⏳ Installing...", C_WARN);
        ui.removeCallbacks(dotsRunnable);
        ui.postDelayed(dotsRunnable, 350);

        final String m = mode;
        try {
            io.execute(() -> {
                String res;
                try {
                    res = ModInstaller.install(getApplicationContext(), m);
                } catch (Throwable t) {
                    res = "ERROR: " + t.getMessage();
                }
                final String r = (res == null) ? "ERROR: empty result" : res;
                ui.post(() -> finishDownload(r));
            });
        } catch (Throwable t) {
            installing = false;
            busy = false;
            ui.removeCallbacks(dotsRunnable);
            if (downloadBtn != null) setBtn(downloadBtn, "⬇ Download", C_ACCENT);
        }
    }

    private void finishDownload(String r) {
        installing = false;
        ui.removeCallbacks(dotsRunnable);
        boolean ok = "OK".equals(r);
        if (downloadBtn != null) {
            setBtn(downloadBtn, ok ? "✓ Done" : "✗ Error", ok ? C_OK : C_ERR);
        }
        if (!ok) toast(r);
        ui.postDelayed(() -> {
            busy = false;
            if (downloadBtn != null) setBtn(downloadBtn, "⬇ Download", C_ACCENT);
        }, 2000);
    }

    // ---------------------------------------------------------------- helpers

    private void addHeader(String title) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);

        TextView t = text(title, 16, Color.WHITE, true);
        row.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView close = new TextView(this);
        close.setText("✕");
        close.setTextColor(Color.WHITE);
        close.setTextSize(20);
        close.setPadding(dp(12), dp(6), dp(6), dp(6));
        attachCloseHandler(close);
        row.addView(close, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        panel.addView(row, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    // tap = close panel only; hold 3 s = kill bubble completely
    private void attachCloseHandler(final TextView close) {
        close.setOnTouchListener((v, e) -> {
            try {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        holdTriggered = false;
                        close.setTextColor(Color.parseColor("#FF5252"));
                        ui.removeCallbacks(holdRunnable);
                        ui.postDelayed(holdRunnable, 3000);
                        return true;
                    case MotionEvent.ACTION_UP:
                        ui.removeCallbacks(holdRunnable);
                        close.setTextColor(Color.WHITE);
                        if (!holdTriggered && inside(v, e)) hidePanel();
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        ui.removeCallbacks(holdRunnable);
                        close.setTextColor(Color.WHITE);
                        return true;
                    default:
                        break;
                }
            } catch (Throwable ignored) { }
            return true;
        });
    }

    private boolean inside(View v, MotionEvent e) {
        return e.getX() >= 0 && e.getX() <= v.getWidth()
                && e.getY() >= 0 && e.getY() <= v.getHeight();
    }

    private void setBtn(TextView t, String s, int color) {
        try {
            t.setText(s);
            t.setBackground(rounded(color, 10, 0, 0));
        } catch (Throwable ignored) { }
    }

    private TextView button(String label, int color) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextColor(Color.WHITE);
        t.setTextSize(15);
        t.setGravity(Gravity.CENTER);
        t.setPadding(dp(12), dp(12), dp(12), dp(12));
        t.setBackground(rounded(color, 10, 0, 0));
        t.setClickable(true);
        return t;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(sp);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private LinearLayout.LayoutParams lp(int topDp) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(topDp);
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
        } catch (Throwable t) {
            toast("Cannot open link");
        }
    }

    private void toast(final String msg) {
        ui.post(() -> {
            try {
                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
            } catch (Throwable ignored) { }
        });
    }
}
