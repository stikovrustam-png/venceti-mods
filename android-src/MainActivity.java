package com.venceti.mods;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.webkit.JavascriptInterface;
import android.webkit.WebView;

import com.getcapacitor.BridgeActivity;

import rikka.shizuku.Shizuku;

public class MainActivity extends BridgeActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        WebView webView = getBridge().getWebView();
        webView.addJavascriptInterface(new VencetiBridge(), "VencetiAndroid");
    }

    public class VencetiBridge {

        @JavascriptInterface
        public boolean isShizukuRunning() {
            try { return Shizuku.pingBinder(); }
            catch (Throwable t) { return false; }
        }

        @JavascriptInterface
        public boolean hasShizukuPermission() {
            try {
                return Shizuku.checkSelfPermission() ==
                    android.content.pm.PackageManager.PERMISSION_GRANTED;
            } catch (Throwable t) { return false; }
        }

        @JavascriptInterface
        public void requestShizukuPermission() {
            try { Shizuku.requestPermission(0); }
            catch (Throwable ignored) {}
        }

        @JavascriptInterface
        public boolean hasOverlayPermission() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                return Settings.canDrawOverlays(MainActivity.this);
            }
            return true;
        }

        @JavascriptInterface
        public void requestOverlayPermission() {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Intent intent = new Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())
                );
                startActivity(intent);
            }
        }

        @JavascriptInterface
        public void startFloatingBubble(String mode) {
            Intent svc = new Intent(MainActivity.this, FloatingBubbleService.class);
            svc.putExtra("mode", mode);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(svc);
            } else {
                startService(svc);
            }
            moveTaskToBack(true);
        }

        @JavascriptInterface
        public void openTelegram(String url) {
            try {
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
            } catch (Throwable ignored) {}
        }
    }
}
