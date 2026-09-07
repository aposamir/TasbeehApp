package com.aposamir.tasbeehapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.KeyEvent;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private static final int OVERLAY_PERMISSION_REQ = 1000;
    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webview);
        WebSettings webSettings = webView.getSettings();
        webSettings.setJavaScriptEnabled(true);
        webSettings.setDomStorageEnabled(true);
        webView.setWebViewClient(new WebViewClient());

        webView.loadUrl("file:///android_asset/index.html");

        webView.addJavascriptInterface(new WebAppInterface(), "AndroidBridge");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(bubbleReceiver, new IntentFilter("BUBBLE_TAPPED"), Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(bubbleReceiver, new IntentFilter("BUBBLE_TAPPED"));
        }

        checkOverlayPermission();
    }

    private void checkOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            startActivityForResult(intent, OVERLAY_PERMISSION_REQ);
        } else {
            startService(new Intent(this, FloatingService.class));
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == OVERLAY_PERMISSION_REQ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && Settings.canDrawOverlays(this)) {
                startService(new Intent(this, FloatingService.class));
            }
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP && event.getAction() == KeyEvent.ACTION_DOWN) {
            webView.evaluateJavascript("javascript:androidTap();", null);
            return true;
        }
        return super.dispatchKeyEvent(event);
    }

    private BroadcastReceiver bubbleReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            webView.evaluateJavascript("javascript:androidTap();", null);
        }
    };

    public class WebAppInterface {
        @JavascriptInterface
        public void updateCount(int count) {
            Intent intent = new Intent("WEB_UPDATED");
            intent.putExtra("count", count);
            sendBroadcast(intent);
        }

        @JavascriptInterface
        public void toggleBubble() {
            SharedPreferences prefs = getSharedPreferences("bubble_prefs", MODE_PRIVATE);
            boolean isRunning = prefs.getBoolean("bubble_running", true);
            if (isRunning) {
                stopService(new Intent(MainActivity.this, FloatingService.class));
                prefs.edit().putBoolean("bubble_running", false).apply();
            } else {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(MainActivity.this)) {
                    checkOverlayPermission();
                } else {
                    startService(new Intent(MainActivity.this, FloatingService.class));
                }
                prefs.edit().putBoolean("bubble_running", true).apply();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(bubbleReceiver);
    }
}
