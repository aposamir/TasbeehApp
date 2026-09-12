package com.aposamir.tasbeehapp;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

public class FloatingService extends Service {

    private static final double DEFAULT_SCALE = 2.0 / 3.0;
    private static final long POLL_INTERVAL_MS = 1500;

    private WindowManager windowManager;
    private View floatingView;
    private TextView bubbleCounter;
    private int count = 0;
    private WindowManager.LayoutParams params;
    private final Handler pollHandler = new Handler(Looper.getMainLooper());
    private Runnable pollRunnable;

    private BroadcastReceiver webReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            count = intent.getIntExtra("count", count);
            updateCounterText();
        }
    };

    @Override
    public IBinder onBind(Intent intent) { return null; }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        double scale = DEFAULT_SCALE;
        if (intent != null && intent.hasExtra("scale")) {
            scale = intent.getDoubleExtra("scale", DEFAULT_SCALE);
        }

        if (floatingView == null) {
            createFloatingBubble(scale);
            startPolling();
        } else {
            applyScale(scale);
        }

        return START_STICKY;
    }

    private void updateCounterText() {
        if (bubbleCounter != null) {
            bubbleCounter.setText(String.valueOf(count));
        }
    }

    private void startPolling() {
        pollRunnable = new Runnable() {
            @Override
            public void run() {
                SharedPreferences prefs = getSharedPreferences("bubble_prefs", MODE_PRIVATE);
                int savedCount = prefs.getInt("bubble_count", count);
                if (savedCount != count) {
                    count = savedCount;
                    updateCounterText();
                }
                pollHandler.postDelayed(this, POLL_INTERVAL_MS);
            }
        };
        pollHandler.postDelayed(pollRunnable, POLL_INTERVAL_MS);
    }

    private void stopPolling() {
        if (pollRunnable != null) {
            pollHandler.removeCallbacks(pollRunnable);
            pollRunnable = null;
        }
    }

    private void createFloatingBubble(double scale) {
        SharedPreferences prefsInit = getSharedPreferences("bubble_prefs", MODE_PRIVATE);
        count = prefsInit.getInt("bubble_count", 0);

        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_bubble, null);
        bubbleCounter = floatingView.findViewById(R.id.bubble_counter);
        updateCounterText();

        int layoutFlag;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutFlag = WindowManager.LayoutParams.TYPE_PHONE;
        }

        floatingView.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int naturalWidth = floatingView.getMeasuredWidth();
        int naturalHeight = floatingView.getMeasuredHeight();
        int scaledWidth = (int) Math.round(naturalWidth * scale);
        int scaledHeight = (int) Math.round(naturalHeight * scale);

        params = new WindowManager.LayoutParams(
                scaledWidth > 0 ? scaledWidth : WindowManager.LayoutParams.WRAP_CONTENT,
                scaledHeight > 0 ? scaledHeight : WindowManager.LayoutParams.WRAP_CONTENT,
                layoutFlag,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);

        params.gravity = Gravity.TOP | Gravity.LEFT;
        params.x = 0;
        params.y = 100;

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        windowManager.addView(floatingView, params);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(webReceiver, new IntentFilter("WEB_UPDATED"), Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(webReceiver, new IntentFilter("WEB_UPDATED"));
        }

        floatingView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;
            private boolean isClick;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = params.x;
                        initialY = params.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        isClick = true;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        if (Math.abs(event.getRawX() - initialTouchX) > 10 || Math.abs(event.getRawY() - initialTouchY) > 10) {
                            isClick = false;
                        }
                        params.x = initialX + (int) (event.getRawX() - initialTouchX);
                        params.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(floatingView, params);
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (isClick) {
                            sendBroadcast(new Intent("BUBBLE_TAPPED"));
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private void applyScale(double scale) {
        if (floatingView == null || params == null || windowManager == null) return;

        floatingView.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int naturalWidth = floatingView.getMeasuredWidth();
        int naturalHeight = floatingView.getMeasuredHeight();

        if (naturalWidth > 0 && naturalHeight > 0) {
            params.width = (int) Math.round(naturalWidth * scale);
            params.height = (int) Math.round(naturalHeight * scale);
            windowManager.updateViewLayout(floatingView, params);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        stopPolling();
        if (floatingView != null && windowManager != null) {
            windowManager.removeView(floatingView);
        }
        floatingView = null;
        try {
            unregisterReceiver(webReceiver);
        } catch (IllegalArgumentException e) {
        }
    }
} 
