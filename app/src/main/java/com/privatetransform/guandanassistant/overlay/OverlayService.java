package com.privatetransform.guandanassistant.overlay;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.PixelFormat;
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
import com.privatetransform.guandanassistant.MainActivity;
import com.privatetransform.guandanassistant.R;
import com.privatetransform.guandanassistant.engine.StrategyAdvisor;

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
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setPadding(18, 14, 18, 14);
        panel.setBackgroundColor(getColor(R.color.overlay_bg));

        textView = new TextView(this);
        textView.setTextColor(getColor(R.color.overlay_text));
        textView.setTextSize(12);
        textView.setMaxWidth((int) (320 * getResources().getDisplayMetrics().density));
        panel.addView(textView);

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button open = smallButton("编辑");
        open.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) {
                Intent intent = new Intent(OverlayService.this, MainActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            }
        });
        Button close = smallButton("关闭");
        close.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View v) { stopSelf(); }
        });
        row.addView(open);
        row.addView(close);
        panel.addView(row);

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
        params.x = 30;
        params.y = 160;
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
        StrategyAdvisor.Advice advice = AssistantStore.advice();
        String content = "掼蛋辅助  " + AssistantStore.levelSummary()
                + (AssistantStore.isWatching() ? "  实时识别中" : "  未识别")
                + "\n手牌 " + AssistantStore.hand().size() + " 张"
                + "\n建议 " + compact(advice.primary, 42)
                + "\n已出 " + AssistantStore.playedText()
                + "\n余牌 " + compact(AssistantStore.remainingText(), 64)
                + "\n状态 " + compact(AssistantStore.lastScanMessage(), 54);
        textView.setText(content);
    }

    private String compact(String value, int maxLength) {
        if (value == null) return "";
        if (value.length() <= maxLength) return value;
        return value.substring(0, Math.max(0, maxLength - 1)) + "…";
    }

    private Button smallButton(String label) {
        Button button = new Button(this);
        button.setText(label);
        button.setTextSize(12);
        button.setAllCaps(false);
        return button;
    }

    private void registerLocal(BroadcastReceiver broadcastReceiver, IntentFilter filter) {
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(broadcastReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(broadcastReceiver, filter);
        }
    }
}
