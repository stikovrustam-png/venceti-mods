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
import android.widget.TextView;
import android.widget.Toast;

public class FloatingBubbleService extends Service {

    private WindowManager windowManager;
    private FrameLayout rootView;
    private WindowManager.LayoutParams params;
    private String mode = "apk";

    private long closeHoldStart = 0;
    private final Handler holdHandler = new Handler(Looper.getMainLooper());
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            b = new Notification.Builder(this, chId);
        } else {
            b = new Notification.Builder(this);
        }

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

    private void buildBubble() {
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
                        windowManager.updateViewLayout(rootView, params);
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

    private void openPanel() {
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
        title.setText((mode.equals("apk") ? "a" : "g") + "  Mods Panel");
        title.setTextColor(Color.parseColor("#dcdcdc"));
        title.setTextSize(15);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

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

        TextView download = makeButton("⬇  Download", "#2f80ff", "#ffffff", true);
        panel.addView(download);

        TextView tgCreator = makeButton("✈  Telegram Creator", "#161616", "#d0d0d0", false);
        LinearLayout.LayoutParams tgLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        tgLp.topMargin = dp(8);
        panel.addView(tgCreator, tgLp);

        TextView info = makeButton("ⓘ  Information", "#161616", "#d0d0d0", false);
        LinearLayout.LayoutParams infoLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        infoLp.topMargin = dp(8);
        panel.addView(info, infoLp);

        close.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    closeHoldStart = System.currentTimeMillis();
                    close.setTextColor(Color.parseColor("#ff5c5c"));
                    holdRunnable = () -> {
                        if (System.currentTimeMillis() - closeHoldStart >= 2900) {
                            destroyOverlay();
                        }
                    };
                    holdHandler.postDelayed(holdRunnable, 3000);
                    return true;
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    holdHandler.removeCallbacks(holdRunnable);
                    if (System.currentTimeMillis() - closeHoldStart < 2900) {
                        close.setTextColor(Color.parseColor("#9a9a9a"));
                        buildBubble();
                    }
                    return true;
            }
            return false;
        });

        download.setOnClickListener(v -> {
            Toast.makeText(this, "Installing...", Toast.LENGTH_SHORT).show();
            new Thread(() -> {
                String result = ModInstaller.install(this, mode);
                new Handler(Looper.getMainLooper()).post(() ->
                    Toast.makeText(this,
                        result.startsWith("OK") ? "✓ Installed" : result,
                        Toast.LENGTH_LONG).show()
                );
            }).start();
        });

        tgCreator.setOnClickListener(v -> openUrl("https://t.me/babycores"));
        info.setOnClickListener(v -> openInformationPanel());

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
                        windowManager.updateViewLayout(rootView, params);
                        return false;
                }
                return false;
            }
        });

        rootView.addView(panel);
    }

    private void openInformationPanel() {
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
        header.addView(title, new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

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
        LinearLayout.LayoutParams dmLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        dmLp.topMargin = dp(8);
        panel.addView(dm, dmLp);

        TextView back = makeButton("←  Back", "#161616", "#8a8a8a", false);
        LinearLayout.LayoutParams backLp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        backLp.topMargin = dp(12);
        panel.addView(back, backLp);

        close.setOnClickListener(v -> buildBubble());
        channel.setOnClickListener(v -> openUrl("https://t.me/funky_sb"));
        dm.setOnClickListener(v -> openUrl("https://t.me/babycores"));
        back.setOnClickListener(v -> openPanel());

        rootView.addView(panel);
    }

    private TextView makeButton(String text, String bgColor, String fgColor, boolean primary) {
        TextView b = new TextView(this);
        b.setText(text);
        b.setTextColor(Color.parseColor(fgColor));
        b.setTextSize(14);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(14), dp(12), dp(14), dp(12));

        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(dp(10));
        bg.setColor(Color.parseColor(bgColor));
        if (!primary) bg.setStroke(dp(1), Color.parseColor("#262626"));
        b.setBackground(bg);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT);
        b.setLayoutParams(lp);
        return b;
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
            if (rootView != null && windowManager != null) {
                windowManager.removeView(rootView);
            }
        } catch (Throwable ignored) {}
        rootView = null;
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        try {
            if (rootView != null && windowManager != null) {
                windowManager.removeView(rootView);
            }
        } catch (Throwable ignored) {}
    }

    private int dp(int v) {
        return (int)(v * getResources().getDisplayMetrics().density);
    }
}
