package com.privatetransform.guandanassistant.overlay;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.privatetransform.guandanassistant.AssistantStore;
import com.privatetransform.guandanassistant.R;

public final class OverlayService extends Service {
    private WindowManager windowManager;
    private View root;
    private TextView textView;
    private WindowManager.LayoutParams params;
    private float downX;
    private float downY;
    private int startX;
    private int startY;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            render();
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        registerLocal(receiver, new IntentFilter(AssistantStore.ACTION_STATE_CHANGED));
        registerLocal(receiver, new IntentFilter(AssistantStore.ACTION_SCAN_RESULT));
        show();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        render();
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        unregisterReceiver(receiver);
        if (windowManager != null && root != null) windowManager.removeView(root);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void show() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            stopSelf();
            return;
        }
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.HORIZONTAL);
        panel.setGravity(Gravity.CENTER_VERTICAL);
        panel.setPadding(dp(10), dp(5), dp(8), dp(5));
        panel.setBackgroundResource(R.drawable.overlay_bar_bg);

        textView = new TextView(this);
        textView.setTextColor(getColor(R.color.overlay_text));
        textView.setTextSize(10);
        textView.setTypeface(Typeface.DEFAULT_BOLD);
        textView.setIncludeFontPadding(false);
        textView.setMaxWidth((int) (getResources().getDisplayMetrics().widthPixels * 0.78f));
        panel.addView(textView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT));

        Button close = smallButton("×");
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { stopSelf(); }
        });
        panel.addView(close);

        int type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;
        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT);
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = dp(10);
        params.y = dp(10);
        root = panel;
        root.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        downX = event.getRawX();
                        downY = event.getRawY();
                        startX = params.x;
                        startY = params.y;
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        params.x = startX + (int) (event.getRawX() - downX);
                        params.y = startY + (int) (event.getRawY() - downY);
                        windowManager.updateViewLayout(root, params);
                        return true;
                    default:
                        return false;
                }
            }
        });
        windowManager.addView(root, params);
        render();
    }

    private void render() {
        if (textView == null) return;
        String content = "记牌器  " + AssistantStore.levelSummary()
                + (AssistantStore.isWatching() ? "  · 识别中" : "  · 未识别")
                + "\n" + compact(AssistantStore.remainingText(), 96)
                + "\n" + compact(AssistantStore.lastScanMessage(), 110);
        textView.setText(content);
    }

    private String compact(String value, int maxLength) {
        if (value == null) return "";
        if (value.length() <= maxLength) return value;
        return value.substring(0, Math.max(0, maxLength - 1)) + "...";
    }

    private Button smallButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextColor(getColor(R.color.overlay_muted));
        button.setTextSize(13);
        button.setAllCaps(false);
        button.setBackgroundResource(R.drawable.overlay_button_bg);
        button.setMinWidth(dp(30));
        button.setMinHeight(dp(26));
        button.setMinimumWidth(dp(30));
        button.setMinimumHeight(dp(26));
        button.setPadding(0, 0, 0, dp(1));
        return button;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private void registerLocal(BroadcastReceiver broadcastReceiver, IntentFilter filter) {
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(broadcastReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(broadcastReceiver, filter);
        }
    }
}
