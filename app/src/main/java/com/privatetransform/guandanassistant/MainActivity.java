package com.privatetransform.guandanassistant;

import android.Manifest;
import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.media.projection.MediaProjectionManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.privatetransform.guandanassistant.capture.ScreenCaptureService;
import com.privatetransform.guandanassistant.overlay.OverlayService;

public final class MainActivity extends Activity {
    private static final int REQ_CAPTURE = 2001;

    private TextView resultView;
    private boolean pendingAutoStart;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            renderStatus();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        registerLocal(receiver, new IntentFilter(AssistantStore.ACTION_STATE_CHANGED));
        registerLocal(receiver, new IntentFilter(AssistantStore.ACTION_SCAN_RESULT));
        requestNotificationsIfNeeded();
        renderStatus();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (pendingAutoStart && canDrawOverlay()) {
            pendingAutoStart = false;
            beginRecognitionWithOverlay();
        } else {
            renderStatus();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(receiver);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_CAPTURE) return;
        if (resultCode == RESULT_OK && data != null) {
            ScreenCaptureService.start(this, resultCode, data, true);
            Toast.makeText(this, "已开始自动识别", Toast.LENGTH_SHORT).show();
        } else {
            Toast.makeText(this, "未获得录屏权限，无法自动识别", Toast.LENGTH_SHORT).show();
        }
        renderStatus();
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 36, 32, 32);
        scroll.addView(root);

        root.addView(text("掼蛋辅助", 26, true));
        root.addView(text("点开始后会自动打开悬浮层并请求录屏权限，随后切回微信小程序即可实时识别牌面、记录已出牌和估算余牌。", 15, false));

        Button start = fullButton("开始自动识别", new View.OnClickListener() {
            @Override public void onClick(View v) { startAutoRecognition(); }
        });
        root.addView(start);

        LinearLayout row = row();
        row.addView(button("停止识别", new View.OnClickListener() {
            @Override public void onClick(View v) {
                ScreenCaptureService.stop(MainActivity.this);
                AssistantStore.setWatching(false);
                notifyState();
                renderStatus();
            }
        }));
        row.addView(button("重置记牌", new View.OnClickListener() {
            @Override public void onClick(View v) {
                AssistantStore.resetMemory();
                notifyState();
                renderStatus();
            }
        }));
        root.addView(row);

        resultView = text("", 15, false);
        resultView.setPadding(0, 28, 0, 0);
        root.addView(resultView);
        setContentView(scroll);
    }

    private void startAutoRecognition() {
        if (!canDrawOverlay()) {
            pendingAutoStart = true;
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            Toast.makeText(this, "请先允许悬浮窗权限，返回后会继续启动识别", Toast.LENGTH_LONG).show();
            return;
        }
        beginRecognitionWithOverlay();
    }

    private void beginRecognitionWithOverlay() {
        startService(new Intent(this, OverlayService.class));
        notifyState();
        MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        if (manager == null) {
            Toast.makeText(this, "系统不支持录屏识别", Toast.LENGTH_SHORT).show();
            return;
        }
        startActivityForResult(manager.createScreenCaptureIntent(), REQ_CAPTURE);
    }

    private boolean canDrawOverlay() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(this);
    }

    private void renderStatus() {
        if (resultView == null) return;
        resultView.setText("识别状态\n" + (AssistantStore.isWatching() ? "自动识别中" : "未开始")
                + "\n\n双方级数\n" + AssistantStore.levelSummary()
                + "\n\n全局剩余牌堆\n" + AssistantStore.remainingText()
                + "\n\n已记录出牌\n" + AssistantStore.playedText()
                + "\n\n识图状态\n" + AssistantStore.lastScanMessage());
    }

    private void notifyState() {
        sendBroadcast(new Intent(AssistantStore.ACTION_STATE_CHANGED).setPackage(getPackageName()));
    }

    private void requestNotificationsIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 77);
        }
    }

    private void registerLocal(BroadcastReceiver broadcastReceiver, IntentFilter filter) {
        if (Build.VERSION.SDK_INT >= 33) {
            registerReceiver(broadcastReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(broadcastReceiver, filter);
        }
    }

    private TextView text(String value, int sp, boolean bold) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextSize(sp);
        if (bold) view.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        view.setPadding(0, 8, 0, 8);
        return view;
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, 12, 0, 0);
        return row;
    }

    private Button button(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(16);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(0, 0, 12, 0);
        button.setLayoutParams(params);
        return button;
    }

    private Button fullButton(String label, View.OnClickListener listener) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(18);
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        params.setMargins(0, 24, 0, 0);
        button.setLayoutParams(params);
        return button;
    }
}
