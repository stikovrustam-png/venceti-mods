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
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
import android.widget.TextView;
import android.widget.Toast;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FloatingBubbleService extends Service {

    private static final String CHANNEL_ID = "venceti_overlay";
    private static final int NOTIF_ID = 4242;

    private static final int C_BG = Color.parseColor("#0E0E10");
    private static final int C_BTN = Color.parseColor("#16161A");
    private static final int C_ACCENT = Color.parseColor("#2F80FF");
    private static final int C_TEXT = Color.parseColor("#DCDCDC");
    private static final int C_MUTED = Color.parseColor("#6C6C6C");
    private static final int C_LINE = Color.parseColor("#26262C");

    private final Handler ui = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newSingleThreadExecutor();

    private WindowManager wm;

    private IconView bubble;
    private WindowManager.LayoutParams bubbleLp;
    private boolean bubbleAttached = false;

    private LinearLayout panel;
    private WindowManager.LayoutParams panelLp;
    private boolean panelAttached = false;
    private View downloadBtn;
    private TextView sourceView;

    private int posX = 0;
    private int posY = 0;

    private String mode = "apk";
    private volatile boolean busy = false;
    private boolean holdTriggered = false;

    private final Runnable holdRunnable = () -> {
        holdTriggered = true;
        killAll();
    };

    // ------------------------------------------------------------ icons

    private static class IconView extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final int type; // 0 = download arrow, 1 = V logo, 2 = close X

        IconView(Context c, int type, int color) {
            super(c);
            this.type = type;
            p.setColor(color);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
        }

        @Override
        protected void onDraw(Canvas c) {
            try {
                float w = getWidth();
                float h = getHeight();
                p.setStrokeWidth(Math.min(w, h) * 0.1f);
                path.reset();
                switch (type) {
                    case 0:
                        path.moveTo(w * 0.5f, h * 0.16f);
                        path.lineTo(w * 0.5f, h * 0.64f);
                        path.moveTo(w * 0.28f, h * 0.44f);
                        path.lineTo(w * 0.5f, h * 0.66f);
                        path.lineTo(w * 0.72f, h * 0.44f);
                        path.moveTo(w * 0.22f, h * 0.84f);
                        path.lineTo(w * 0.78f, h * 0.84f);
                        break;
                    case 1:
                        path.moveTo(w * 0.27f, h * 0.31f);
                        path.lineTo(w * 0.5f, h * 0.71f);
                        path.lineTo(w * 0.73f, h * 0.31f);
                        break;
                    default:
                        path.moveTo(w * 0.28f, h * 0.28f);
                        path.lineTo(w * 0.72f, h * 0.72f);
                        path.moveTo(w * 0.72f, h * 0.28f);
                        path.lineTo(w * 0.28f, h * 0.72f);
                        break;
                }
                c.drawPath(path, p);
            } catch (Throwable ignored) { }
        }
    }

    // ------------------------------------------------------------ lifecycle

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
            if (wm == null) wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
            if (bubble == null) createBubble();
            if (!bubbleAttached && !panelAttached) showBubble();
            if (sourceView != null) sourceView.setText("Source: " + srcName());
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
                startForeground(NOTIF_ID, n, 0x40000000); // SPECIAL_USE
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

    // ------------------------------------------------------------ teardown (idempotent)

    private void hideBubble() {
        IconView b = bubble;
        if (b != null && bubbleAttached && wm != null) {
            try { wm.removeView(b); } catch (Throwable ignored) { }
        }
        bubbleAttached = false;
    }

    private void removePanelView() {
        LinearLayout p = panel;
        if (p != null && panelAttached && wm != null) {
            try { wm.removeView(p); } catch (Throwable ignored) { }
        }
        panelAttached = false;
        panel = null;
        panelLp = null;
        downloadBtn = null;
        sourceView = null;
    }

    private void destroyOverlay() {
        removePanelView();
        hideBubble();
        bubble = null;
        bubbleLp = null;
    }

    private void killAll() {
        try { ui.removeCallbacksAndMessages(null); } catch (Throwable ignored) { }
        destroyOverlay();
        try { stopForeground(Service.STOP_FOREGROUND_REMOVE); } catch (Throwable ignored) { }
        try { stopSelf(); } catch (Throwable ignored) { }
    }

    // ------------------------------------------------------------ window helpers

    private int overlayType() {
        return Build.VERSION.SDK_INT >= 26
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
    }

    private WindowManager.LayoutParams baseLp(int w, int h) {
        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                w, h, overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.TOP | Gravity.START;
        return lp;
    }

    private int clampX(int x, int w) {
        int max = getResources().getDisplayMetrics().widthPixels - w;
        return Math.max(0, Math.min(x, Math.max(0, max)));
    }

    private int clampY(int y, int h) {
        int max = getResources().getDisplayMetrics().heightPixels - h;
        return Math.max(0, Math.min(y, Math.max(0, max)));
    }

    // ------------------------------------------------------------ drag (bubble and panel header)

    private class Drag implements View.OnTouchListener {
        private final boolean isPanel;
        private int sx, sy;
        private float tx, ty;
        private boolean moved;

        Drag(boolean isPanel) {
            this.isPanel = isPanel;
        }

        @Override
        public boolean onTouch(View v, MotionEvent e) {
            try {
                WindowManager.LayoutParams p = isPanel ? panelLp : bubbleLp;
                View target = isPanel ? panel : bubble;
                if (p == null || target == null || wm == null) return true;
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
                        if (!moved && (Math.abs(dx) > dp(6) || Math.abs(dy) > dp(6))) moved = true;
                        if (moved) {
                            int w = isPanel ? p.width : dp(56);
                            int h = isPanel ? Math.max(target.getHeight(), dp(100)) : dp(56);
                            int nx = clampX(Math.round(sx + dx), w);
                            int ny = clampY(Math.round(sy + dy), h);
                            if (nx != p.x || ny != p.y) {
                                p.x = nx;
                                p.y = ny;
                                posX = nx;
                                posY = ny;
                                wm.updateViewLayout(target, p);
                            }
                        }
                        return true;
                    }
                    case MotionEvent.ACTION_UP:
                        if (!moved && !isPanel) openPanel();
                        return true;
                    default:
                        return true;
                }
            } catch (Throwable ignored) { }
            return true;
        }
    }

    // ------------------------------------------------------------ bubble

    private void createBubble() {
        if (bubble != null) return;
        int size = dp(56);
        IconView b = new IconView(this, 1, Color.WHITE);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(C_ACCENT);
        b.setBackground(bg);
        b.setOnTouchListener(new Drag(false));
        bubbleLp = baseLp(size, size);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        posX = dm.widthPixels - size - dp(12);
        posY = dm.heightPixels / 3;
        bubble = b;
    }

    private void showBubble() {
        if (wm == null || bubble == null || bubbleLp == null || bubbleAttached) return;
        try {
            int s = dp(56);
            bubbleLp.x = clampX(posX, s);
            bubbleLp.y = clampY(posY, s);
            posX = bubbleLp.x;
            posY = bubbleLp.y;
            wm.addView(bubble, bubbleLp);
            bubbleAttached = true;
        } catch (Throwable ignored) { }
    }

    private void openPanel() {
        try {
            hideBubble();
            showPanel();
        } catch (Throwable t) {
            showBubble();
        }
    }

    // ------------------------------------------------------------ panel

    private void showPanel() {
        if (wm == null || panelAttached) return;
        final int w = dp(260);

        final LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackground(rounded(C_BG, 14, C_LINE, 1));

        panelLp = baseLp(w, ViewGroup.LayoutParams.WRAP_CONTENT);
        panelLp.x = clampX(posX, w);
        panelLp.y = clampY(posY, dp(150));
        panel = root;
        buildPanel(root);

        try {
            wm.addView(root, panelLp);
            panelAttached = true;
        } catch (Throwable t) {
            panel = null;
            panelLp = null;
            downloadBtn = null;
            sourceView = null;
            showBubble();
            return;
        }

        root.post(() -> {
            try {
                if (panel != root || panelLp == null || wm == null) return;
                int nx = clampX(panelLp.x, panelLp.width);
                int ny = clampY(panelLp.y, root.getHeight());
                if (nx != panelLp.x || ny != panelLp.y) {
                    panelLp.x = nx;
                    panelLp.y = ny;
                    wm.updateViewLayout(root, panelLp);
                }
            } catch (Throwable ignored) { }
        });
    }

    private void buildPanel(LinearLayout root) {
        // header = whole top strip is the drag handle
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setPadding(dp(12), dp(4), dp(4), dp(4));
        GradientDrawable hb = new GradientDrawable();
        hb.setColor(C_BTN);
        float r = dp(14);
        hb.setCornerRadii(new float[]{r, r, r, r, 0, 0, 0, 0});
        header.setBackground(hb);

        header.addView(new IconView(this, 1, C_ACCENT), new LinearLayout.LayoutParams(dp(22), dp(22)));

        TextView title = text("Mods Panel", 15, C_TEXT, true);
        LinearLayout.LayoutParams tlp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        tlp.leftMargin = dp(8);
        header.addView(title, tlp);

        IconView close = new IconView(this, 2, C_TEXT);
        header.addView(close, new LinearLayout.LayoutParams(dp(40), dp(40)));
        attachClose(close);

        header.setOnTouchListener(new Drag(true));
        root.addView(header, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // body
        LinearLayout body = new LinearLayout(this);
        body.setOrientation(LinearLayout.VERTICAL);
        body.setPadding(dp(12), dp(10), dp(12), dp(12));

        sourceView = text("Source: " + srcName(), 13, C_MUTED, false);
        body.addView(sourceView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        LinearLayout btn = new LinearLayout(this);
        btn.setOrientation(LinearLayout.HORIZONTAL);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(12), dp(12), dp(12), dp(12));
        btn.setBackground(rounded(C_ACCENT, 10, 0, 0));
        btn.setClickable(true);
        btn.addView(new IconView(this, 0, Color.WHITE), new LinearLayout.LayoutParams(dp(20), dp(20)));
        TextView bt = text("Download", 15, Color.WHITE, true);
        LinearLayout.LayoutParams blp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        blp.leftMargin = dp(8);
        btn.addView(bt, blp);
        btn.setOnClickListener(v -> onDownload());
        downloadBtn = btn;

        LinearLayout.LayoutParams dlp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dlp.topMargin = dp(10);
        body.addView(btn, dlp);

        root.addView(body, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private void closePanel() {
        try {
            WindowManager.LayoutParams lp = panelLp;
            if (lp != null) {
                posX = lp.x;
                posY = lp.y;
            }
            removePanelView();
            showBubble();
        } catch (Throwable ignored) { }
    }

    // tap = close panel (bubble returns at the panel's spot); hold 3 s = kill everything
    private void attachClose(final View close) {
        close.setOnTouchListener((v, e) -> {
            try {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        holdTriggered = false;
                        v.setAlpha(0.5f);
                        ui.removeCallbacks(holdRunnable);
                        ui.postDelayed(holdRunnable, 3000);
                        return true;
                    case MotionEvent.ACTION_UP:
                        ui.removeCallbacks(holdRunnable);
                        v.setAlpha(1f);
                        if (!holdTriggered && inside(v, e)) closePanel();
                        return true;
                    case MotionEvent.ACTION_CANCEL:
                        ui.removeCallbacks(holdRunnable);
                        v.setAlpha(1f);
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

    // ------------------------------------------------------------ download

    private void onDownload() {
        if (busy) return;
        busy = true;
        try {
            View b = downloadBtn;
            if (b != null) b.animate().alpha(0.4f).setDuration(120).start();
        } catch (Throwable ignored) { }

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
            finishDownload("ERROR: " + t.getMessage());
        }
    }

    private void finishDownload(String r) {
        busy = false;
        try {
            View b = downloadBtn;
            if (b != null) b.animate().alpha(1f).setDuration(220).start();
        } catch (Throwable ignored) { }
        toast("OK".equals(r) ? "Mods installed" : r);
    }

    // ------------------------------------------------------------ helpers

    private String srcName() {
        return "apk".equals(mode) ? "APK" : "Google Play";
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextColor(color);
        t.setTextSize(sp);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
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

    private void toast(final String msg) {
        ui.post(() -> {
            try {
                Toast.makeText(getApplicationContext(), msg, Toast.LENGTH_LONG).show();
            } catch (Throwable ignored) { }
        });
    }
}
