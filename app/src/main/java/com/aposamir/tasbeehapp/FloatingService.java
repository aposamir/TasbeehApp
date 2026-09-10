package com.aposamir.tasbeehapp;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.TextView;
import androidx.core.app.NotificationCompat;

public class FloatingService extends Service {

    private static final double DEFAULT_SCALE = 7.0 / 9.0; // احتياطي فقط؛ القيمة الفعلية تأتي من JS عبر NATIVE_BUBBLE_SCALE
    private static final int BASE_BUBBLE_DP = 128; // يطابق layout_width/height الثابت في layout_floating_bubble.xml
    private static final int NOTIFICATION_ID = 1001;
    private static final String CHANNEL_ID = "tasbeeh_bubble_channel";

    private WindowManager windowManager;
    private View floatingView;
    private TextView bubbleCounter;
    private int count = 0;
    private WindowManager.LayoutParams params;

    private BroadcastReceiver webReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            count = intent.getIntExtra("count", count);
            if (bubbleCounter != null) {
                bubbleCounter.setText(String.valueOf(count));
            }
        }
    };

    @Override
    public IBinder onBind(Intent intent) { return null; }

    // خدمة أمامية (Foreground Service) حتى لا يوقفها النظام أثناء تصغير التطبيق —
    // كان هذا هو سبب عودة الفقاعة لموضعها الافتراضي وصفرها عند العودة للتطبيق: كان
    // النظام (خصوصاً MIUI) يوقف الخدمة في الخلفية، فتُنشأ من جديد بقيم افتراضية.
    @Override
    public void onCreate() {
        super.onCreate();
        startForegroundWithNotification();
    }

    private void startForegroundWithNotification() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID, "فقاعة المسبحة", NotificationManager.IMPORTANCE_MIN);
            channel.setShowBadge(false);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) manager.createNotificationChannel(channel);
        }

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("المسبحة تعمل في الخلفية")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setOngoing(true)
                .build();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIFICATION_ID, notification);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && intent.getBooleanExtra("hide", false)) {
            hideView();
            return START_STICKY;
        }

        double scale = DEFAULT_SCALE;
        if (intent != null && intent.hasExtra("scale")) {
            scale = intent.getDoubleExtra("scale", DEFAULT_SCALE);
        }

        if (floatingView == null) {
            createFloatingBubble(scale);
        } else {
            applyScale(scale);
        }

        return START_STICKY;
    }

    // يُخفي الفقاعة عن الشاشة فقط (يزيلها من مدير النوافذ) دون تدمير الخدمة أو حذف
    // موضعها/عدّادها المحفوظين في الذاكرة — بذلك يعودان كما كانا بالضبط عند إظهارها مجدداً
    private void hideView() {
        if (floatingView != null && floatingView.getParent() != null && windowManager != null) {
            try { windowManager.removeView(floatingView); } catch (Exception e) {}
        }
    }

    // يُصغِّر حجم الدائرة الفعلي (TextView) نفسه، بدل تصغير نافذة النظام المحيطة فقط —
    // تصغير النافذة وحدها كان يترك الدائرة بحجمها الأصلي 128dp فتُقصّ بحدود النافذة الأصغر
    // (وهذا هو السبب الحقيقي لظهورها كـ"ربع دائرة" سابقاً).
    private void applyScaleToChild(double scale) {
        if (bubbleCounter == null) return;
        float density = getResources().getDisplayMetrics().density;
        int sizePx = Math.max(1, (int) Math.round(BASE_BUBBLE_DP * scale * density));
        ViewGroup.LayoutParams lp = bubbleCounter.getLayoutParams();
        lp.width = sizePx;
        lp.height = sizePx;
        bubbleCounter.setLayoutParams(lp);
    }

    private void createFloatingBubble(double scale) {
        floatingView = LayoutInflater.from(this).inflate(R.layout.layout_floating_bubble, null);
        bubbleCounter = floatingView.findViewById(R.id.bubble_counter);
        if (bubbleCounter != null) {
            bubbleCounter.setText(String.valueOf(count));
        }
        applyScaleToChild(scale);

        int layoutFlag;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutFlag = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutFlag = WindowManager.LayoutParams.TYPE_PHONE;
        }

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
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
                        int newX = initialX + (int) (event.getRawX() - initialTouchX);
                        int newY = initialY + (int) (event.getRawY() - initialTouchY);
                        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
                        int maxX = Math.max(0, dm.widthPixels - floatingView.getWidth());
                        int maxY = Math.max(0, dm.heightPixels - floatingView.getHeight());
                        params.x = Math.max(0, Math.min(newX, maxX));
                        params.y = Math.max(0, Math.min(newY, maxY));
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

        applyScaleToChild(scale);
        params.width = WindowManager.LayoutParams.WRAP_CONTENT;
        params.height = WindowManager.LayoutParams.WRAP_CONTENT;

        floatingView.measure(
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int newWidth = floatingView.getMeasuredWidth();
        int newHeight = floatingView.getMeasuredHeight();

        android.util.DisplayMetrics dm = getResources().getDisplayMetrics();
        int maxX = Math.max(0, dm.widthPixels - newWidth);
        int maxY = Math.max(0, dm.heightPixels - newHeight);
        params.x = Math.max(0, Math.min(params.x, maxX));
        params.y = Math.max(0, Math.min(params.y, maxY));

        if (floatingView.getParent() == null) {
            windowManager.addView(floatingView, params); // كانت مخفية (hideView) — نعيد إرفاقها بنفس الموضع والعدّاد المحفوظين
        } else {
            windowManager.updateViewLayout(floatingView, params);
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingView != null && floatingView.getParent() != null && windowManager != null) {
            try { windowManager.removeView(floatingView); } catch (Exception e) {}
        }
        floatingView = null;
        try {
            unregisterReceiver(webReceiver);
        } catch (IllegalArgumentException e) {
        }
    }
} 
