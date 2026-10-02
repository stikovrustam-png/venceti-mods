 package com.venceti.mods;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

public class FloatingBubbleService extends Service {

    private WindowManager windowManager;
    private FrameLayout rootView;
    private WindowManager.LayoutParams params;
    private String mode = "apk";
    private final Handler ui = new Handler(Looper.getMainLooper());

    private Runnable holdRunnable;

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getStringExtra("mode") != null) {
            mode = intent.getStringExtra("mode");
        }
        startForegroundNotification();
        if (rootView == null) initOverlay();
        return START_STICKY;
    }

    private void startForegroundNotification() {
        String chId = "venceti_mods";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                chId, "Venceti Mods", NotificationManager.IMPORTANCE_MIN);
            ch.setShowBadge(false);
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
        Notification.Builder b;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) b = new Notification.Builder(this, chId);
        else b = new Notification.Builder(this);
        Notification notif = b
            .setContentTitle("Venceti Mods")
            .setContentText("Active")
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setOngoing(true)
            .build();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, notif,
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(1, notif);
        }
    }

    private void initOverlay() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;

        params = new WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.START;
        params.x = 100;
        params.y = 400;

        rootView = new FrameLayout(this);
        buildBubble();
        windowManager.addView(rootView, params);
    }

    /* ==================== BUBBLE ==================== */
    private void buildBubble() {
        if (rootView == null) return;
        rootView.removeAllViews();

        TextView circle = new TextView(this);
        int size = dp(58);
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(size, size);
        circle.setLayoutParams(lp);
        circle.setText(mode.equals("apk") ? "a" : "g");
        circle.setTextColor(Color.parseColor("#4da3ff"));
        circle.setTextSize(20);
        circle.setGravity(Gravity.CENTER);
        circle.setTypeface(null, android.graphics.Typeface.BOLD);

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.OVAL);
        bg.setColor(Color.parseColor("#000000"));
        bg.setStroke(dp(2), Color.parseColor("#2f80ff"));
        circle.setBackground(bg);
        circle.setElevation(dp(8));

        circle.setOnTouchListener(new View.OnTouchListener() {
            float startX, startY;
            int startParamX, startParamY;
            boolean moved = false;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = e.getRawX();
                        startY = e.getRawY();
                        startParamX = params.x;
                        startParamY = params.y;
                        moved = false;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        float dx = e.getRawX() - startX;
                        float dy = e.getRawY() - startY;
                        if (Math.abs(dx) > 10 || Math.abs(dy) > 10) moved = true;
                        params.x = startParamX + (int) dx;
                        params.y = startParamY + (int) dy;
                        if (rootView != null && rootView.getParent() != null) {
                            try { windowManager.updateViewLayout(rootView, params); } catch (Throwable ignored) {}
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (!moved) openPanel();
                        return true;
                }
                return false;
            }
        });

        rootView.addView(circle);
    }

    /* ==================== PANEL ==================== */
    private void openPanel() {
        if (rootView == null) return;
        rootView.removeAllViews();

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(14), dp(16), dp(16));

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(16));
        bg.setColor(Color.parseColor("#0e0e0e"));
        bg.setStroke(dp(1), Color.parseColor("#2a2a2a"));
        panel.setBackground(bg);
        panel.setElevation(dp(12));

        // Header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText((mode.equals("apk") ? "a" : "g") + "  Mods Panel");
        title.setTextColor(Color.parseColor("#dcdcdc"));
        title.setTextSize(15);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView close = new TextView(this);
        close.setText("✕");
        close.setTextColor(Color.parseColor("#9a9a9a"));
        close.setTextSize(18);
        close.setPadding(dp(8), dp(4), dp(8), dp(4));
        header.addView(close);

        panel.addView(header);

        TextView sub = new TextView(this);
        sub.setText("Source: " + (mode.equals("apk") ? "APK" : "Google Play"));
        sub.setTextColor(Color.parseColor("#6c6c6c"));
        sub.setTextSize(11);
        sub.setPadding(0, dp(2), 0, dp(12));
        panel.addView(sub);

        // Download button
        final TextView download = makeButton("⬇  Download", "#2f80ff", "#ffffff", true);
        panel.addView(download);

        // Files button
        final TextView files = makeButton("📁  Files", "#161616", "#d0d0d0", false);
        LinearLayout.LayoutParams fl = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        fl.topMargin = dp(8);
        panel.addView(files, fl);

        // Telegram button
        TextView tg = makeButton("✈  Telegram Creator", "#161616", "#d0d0d0", false);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        tl.topMargin = dp(8);
        panel.addView(tg, tl);

        /* --- CLOSE: tap = back to bubble, hold 3s = kill --- */
        close.setOnTouchListener(new View.OnTouchListener() {
            long downTime = 0;
            boolean holdTriggered = false;

            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        downTime = System.currentTimeMillis();
                        holdTriggered = false;
                        close.setTextColor(Color.parseColor("#ff5c5c"));

                        holdRunnable = new Runnable() {
                            @Override
                            public void run() {
                                holdTriggered = true;
                                close.setTextColor(Color.parseColor("#9a9a9a"));
                                destroyOverlay();
                            }
                        };
                        ui.postDelayed(holdRunnable, 3000);
                        return true;

                    case MotionEvent.ACTION_UP:
                    case MotionEvent.ACTION_CANCEL:
                        if (holdRunnable != null) {
                            ui.removeCallbacks(holdRunnable);
                            holdRunnable = null;
                        }
                        if (!holdTriggered) {
                            close.setTextColor(Color.parseColor("#9a9a9a"));
                            if (rootView != null) buildBubble();
                        }
                        return true;
                }
                return false;
            }
        });

        /* --- DOWNLOAD: animated + safe --- */
        download.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                download.setText("⏳  Installing...");
                download.setBackground(makeBg("#1a2a4a", "#2f80ff", 10));
                download.setEnabled(false);

                final String m = mode;
                new Thread(new Runnable() {
                    @Override
                    public void run() {
                        String result;
                        try {
                            result = ModInstaller.install(
                                FloatingBubbleService.this, m);
                        } catch (Throwable t) {
                            result = "ERROR: " + t.getMessage();
                        }
                        final String r = result == null ? "ERROR: unknown" : result;

                        ui.post(new Runnable() {
                            @Override
                            public void run() {
                                if (rootView == null) return;
                                boolean ok = r.startsWith("OK");
                                download.setText(ok ? "✓  Done" : "✗  " + short(r));
                                download.setBackground(makeBg(
                                    ok ? "#1a3a24" : "#3a1a1a",
                                    ok ? "#2ecc71" : "#ff5c5c", 10));
                                try {
                                    Toast.makeText(FloatingBubbleService.this,
                                        ok ? "✓ Installed" : r,
                                        Toast.LENGTH_LONG).show();
                                } catch (Throwable ignored) {}

                                ui.postDelayed(new Runnable() {
                                    @Override
                                    public void run() {
                                        if (rootView == null) return;
                                        if (download.getParent() == null) return;
                                        download.setText("⬇  Download");
                                        download.setBackground(makeBg("#2f80ff", "#2f80ff", 10));
                                        download.setEnabled(true);
                                    }
                                }, 2500);
                            }
                        });
                    }
                }).start();
            }
        });

        /* --- FILES: open files screen --- */
        files.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { openFilesPanel(); }
        });

        tg.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) { openUrl("https://t.me/babycores"); }
        });

        // drag panel by empty areas
        panel.setOnTouchListener(new View.OnTouchListener() {
            float startX, startY;
            int startParamX, startParamY;
            @Override
            public boolean onTouch(View v, MotionEvent e) {
                switch (e.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN:
                        startX = e.getRawX();
                        startY = e.getRawY();
                        startParamX = params.x;
                        startParamY = params.y;
                        return false;
                    case MotionEvent.ACTION_MOVE:
                        params.x = startParamX + (int)(e.getRawX() - startX);
                        params.y = startParamY + (int)(e.getRawY() - startY);
                        if (rootView != null && rootView.getParent() != null) {
                            try { windowManager.updateViewLayout(rootView, params); } catch (Throwable ignored) {}
                        }
                        return false;
                }
                return false;
            }
        });

        rootView.addView(panel);
    }

    /* ==================== FILES PANEL ==================== */
    private void openFilesPanel() {
        if (rootView == null) return;
        rootView.removeAllViews();

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(14), dp(16), dp(16));

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(16));
        bg.setColor(Color.parseColor("#0e0e0e"));
        bg.setStroke(dp(1), Color.parseColor("#2a2a2a"));
        panel.setBackground(bg);
        panel.setElevation(dp(12));

        // header
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("📁  Files");
        title.setTextColor(Color.parseColor("#dcdcdc"));
        title.setTextSize(15);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView close = new TextView(this);
        close.setText("✕");
        close.setTextColor(Color.parseColor("#9a9a9a"));
        close.setTextSize(18);
        close.setPadding(dp(8), dp(4), dp(8), dp(4));
        header.addView(close);
        panel.addView(header);

        TextView sub = new TextView(this);
        sub.setText("Download/Venceti Mods/Files");
        sub.setTextColor(Color.parseColor("#6c6c6c"));
        sub.setTextSize(11);
        sub.setPadding(0, dp(2), 0, dp(12));
        panel.addView(sub);

        // list area
        ScrollView scroll = new ScrollView(this);
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list);

        LinearLayout.LayoutParams scrollLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT, dp(180));
        panel.addView(scroll, scrollLp);

        final TextView status = new TextView(this);
        status.setText("Loading…");
        status.setTextColor(Color.parseColor("#9a9a9a"));
        status.setTextSize(12);
        status.setPadding(0, dp(8), 0, 0);
        list.addView(status);

        // Information link at bottom
        TextView infoLink = makeButton("ⓘ  Information", "#161616", "#9a9a9a", false);
        LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        il.topMargin = dp(12);
        panel.addView(infoLink, il);

        // back
        TextView back = makeButton("←  Back", "#161616", "#8a8a8a", false);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        bl.topMargin = dp(8);
        panel.addView(back, bl);

        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { if (rootView != null) buildBubble(); }
        });
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openPanel(); }
        });
        infoLink.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openInformationPanel(); }
        });

        rootView.addView(panel);

        // load file list in background
        new Thread(new Runnable() {
            @Override
            public void run() {
                final String listing;
                try { listing = ModInstaller.listFiles(); }
                catch (Throwable t) { listing = null; }
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (rootView == null) return;
                        list.removeAllViews();
                        if (listing == null || listing.isEmpty()) {
                            TextView tv = new TextView(FloatingBubbleService.this);
                            tv.setText("Folder is empty or not found");
                            tv.setTextColor(Color.parseColor("#6c6c6c"));
                            tv.setTextSize(12);
                            list.addView(tv);
                            return;
                        }
                        String[] lines = listing.split("\n");
                        for (String line : lines) {
                            if (line.trim().isEmpty()) continue;
                            TextView tv = new TextView(FloatingBubbleService.this);
                            tv.setText("•  " + line.trim());
                            tv.setTextColor(Color.parseColor("#c8c8c8"));
                            tv.setTextSize(12);
                            tv.setPadding(0, dp(4), 0, dp(4));
                            list.addView(tv);
                        }
                    }
                });
            }
        }).start();
    }

    /* ==================== INFO PANEL ==================== */
    private void openInformationPanel() {
        if (rootView == null) return;
        rootView.removeAllViews();

        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(dp(16), dp(14), dp(16), dp(16));

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(16));
        bg.setColor(Color.parseColor("#0e0e0e"));
        bg.setStroke(dp(1), Color.parseColor("#2a2a2a"));
        panel.setBackground(bg);
        panel.setElevation(dp(12));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = new TextView(this);
        title.setText("ⓘ  Information");
        title.setTextColor(Color.parseColor("#dcdcdc"));
        title.setTextSize(15);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0,
            LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView close = new TextView(this);
        close.setText("✕");
        close.setTextColor(Color.parseColor("#9a9a9a"));
        close.setTextSize(18);
        close.setPadding(dp(8), dp(4), dp(8), dp(4));
        header.addView(close);
        panel.addView(header);

        TextView sub = new TextView(this);
        sub.setText("Venceti Mods · v1.0");
        sub.setTextColor(Color.parseColor("#6c6c6c"));
        sub.setTextSize(11);
        sub.setPadding(0, dp(2), 0, dp(12));
        panel.addView(sub);

        TextView channel = makeButton("✈  Telegram Channel", "#161616", "#d0d0d0", false);
        panel.addView(channel);

        TextView dm = makeButton("✈  Telegram (DM)", "#161616", "#d0d0d0", false);
        LinearLayout.LayoutParams dl = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        dl.topMargin = dp(8);
        panel.addView(dm, dl);

        TextView back = makeButton("←  Back to Files", "#161616", "#8a8a8a", false);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        bl.topMargin = dp(12);
        panel.addView(back, bl);

        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { if (rootView != null) buildBubble(); }
        });
        channel.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openUrl("https://t.me/funky_sb"); }
        });
        dm.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openUrl("https://t.me/babycores"); }
        });
        back.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { openFilesPanel(); }
        });

        rootView.addView(panel);
    }

    /* ==================== HELPERS ==================== */
    private TextView makeButton(String text, String bgColor, String fgColor, boolean primary) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextColor(Color.parseColor(fgColor));
        b.setTextSize(14);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(14), dp(12), dp(14), dp(12));
        b.setBackground(makeBg(bgColor, primary ? bgColor : "#262626", 10));

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        b.setLayoutParams(lp);
        return b;
    }

    private GradientDrawable makeBg(String fill, String stroke, int radiusDp) {
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(radiusDp));
        bg.setColor(Color.parseColor(fill));
        if (stroke != null) bg.setStroke(dp(1), Color.parseColor(stroke));
        return bg;
    }

    private String short(String s) {
        if (s == null) return "Error";
        if (s.length() > 20) return s.substring(0, 20) + "…";
        return s;
    }

    private void openUrl(String url) {
        try {
            Intent i = new Intent(Intent.ACTION_VIEW, android.net.Uri.parse(url));
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
        } catch (Throwable ignored) {}
    }

    private void destroyOverlay() {
        try {
            if (rootView != null && windowManager != null && rootView.getParent() != null) {
                windowManager.removeView(rootView);
            }
        } catch (Throwable ignored) {}
        rootView = null;
        try { stopForeground(true); } catch (Throwable ignored) {}
        stopSelf();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            if (rootView != null && windowManager != null && rootView.getParent() != null) {
                windowManager.removeView(rootView);
            }
        } catch (Throwable ignored) {}
        rootView = null;
    }

    private int dp(int v) {
        return (int)(v * getResources().getDisplayMetrics().density);
    }
}
