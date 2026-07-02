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
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.privatetransform.guandanassistant.capture.ScreenCaptureService;
import com.privatetransform.guandanassistant.overlay.OverlayService;

public final class MainActivity extends Activity {
    private static final int REQ_CAPTURE = 2001;
    private EditText levelInput;
    private EditText handInput;
    private EditText lastPlayInput;
    private EditText playedInput;
    private TextView resultView;

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            renderAdvice();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildUi();
        registerLocal(receiver, new IntentFilter(AssistantStore.ACTION_STATE_CHANGED));
        registerLocal(receiver, new IntentFilter(AssistantStore.ACTION_SCAN_RESULT));
        requestNotificationsIfNeeded();
        renderAdvice();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(receiver);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_CAPTURE && resultCode == RESULT_OK && data != null) {
            ScreenCaptureService.start(this, resultCode, data);
            Toast.makeText(this, "已启动截图识别服务", Toast.LENGTH_SHORT).show();
        }
    }

    private void buildUi() {
        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 32, 32, 32);
        scroll.addView(root);

        TextView title = text("掼蛋辅助", 24, true);
        root.addView(title);
        root.addView(text("先录入级牌和手牌，打开悬浮窗后可在游戏页面查看建议。截图识别入口已接好，当前需要补充牌面模板后才能自动识别具体牌。", 14, false));

        levelInput = input("当前打几，例如 2、5、A");
        levelInput.setText(AssistantStore.levelRank());
        root.addView(label("级牌"));
        root.addView(levelInput);

        handInput = input("手牌，例如 H5 S5 C5 D5 BJ SJ A K Q");
        handInput.setMinLines(3);
        handInput.setText(AssistantStore.handText());
        root.addView(label("我的手牌"));
        root.addView(handInput);

        lastPlayInput = input("上家/当前要压的牌，可留空表示首出");
        lastPlayInput.setText(AssistantStore.lastPlayText());
        root.addView(label("当前要压的牌"));
        root.addView(lastPlayInput);

        playedInput = input("本轮新增已出牌，点“记入已出”后累计");
        root.addView(label("记牌输入"));
        root.addView(playedInput);

        LinearLayout row1 = row();
        row1.addView(button("分析建议", new View.OnClickListener() {
            @Override public void onClick(View v) { saveInputs(); renderAdvice(); notifyState(); }
        }));
        row1.addView(button("记入已出", new View.OnClickListener() {
            @Override public void onClick(View v) {
                saveInputs();
                AssistantStore.addPlayedFromText(playedInput.getText().toString());
                playedInput.setText("");
                renderAdvice();
                notifyState();
            }
        }));
        root.addView(row1);

        LinearLayout row2 = row();
        row2.addView(button("打开悬浮窗", new View.OnClickListener() {
            @Override public void onClick(View v) { openOverlay(); }
        }));
        row2.addView(button("截图识别", new View.OnClickListener() {
            @Override public void onClick(View v) { requestCapture(); }
        }));
        root.addView(row2);

        LinearLayout row3 = row();
        row3.addView(button("无障碍设置", new View.OnClickListener() {
            @Override public void onClick(View v) { startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)); }
        }));
        row3.addView(button("重置记牌", new View.OnClickListener() {
            @Override public void onClick(View v) {
                AssistantStore.resetMemory();
                renderAdvice();
                notifyState();
            }
        }));
        root.addView(row3);

        resultView = text("", 15, false);
        resultView.setPadding(0, 24, 0, 0);
        root.addView(resultView);
        setContentView(scroll);
    }

    private void saveInputs() {
        AssistantStore.setLevelRank(levelInput.getText().toString());
        AssistantStore.setHandFromText(handInput.getText().toString());
        AssistantStore.setLastPlayFromText(lastPlayInput.getText().toString());
    }

    private void renderAdvice() {
        saveInputs();
        StrategyAdvisorAdviceText text = new StrategyAdvisorAdviceText();
        resultView.setText(text.render());
    }

    private void openOverlay() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(this)) {
            Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:" + getPackageName()));
            startActivity(intent);
            Toast.makeText(this, "请先允许悬浮窗权限", Toast.LENGTH_SHORT).show();
            return;
        }
        saveInputs();
        startService(new Intent(this, OverlayService.class));
        notifyState();
    }

    private void requestCapture() {
        MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
        if (manager != null) startActivityForResult(manager.createScreenCaptureIntent(), REQ_CAPTURE);
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

    private TextView label(String value) {
        TextView view = text(value, 14, true);
        view.setPadding(0, 18, 0, 4);
        return view;
    }

    private EditText input(String hint) {
        EditText view = new EditText(this);
        view.setHint(hint);
        view.setSingleLine(false);
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
        button.setOnClickListener(listener);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        params.setMargins(0, 0, 12, 0);
        button.setLayoutParams(params);
        return button;
    }

    private static final class StrategyAdvisorAdviceText {
        String render() {
            com.privatetransform.guandanassistant.engine.StrategyAdvisor.Advice advice = AssistantStore.advice();
            return "建议\n" + advice.primary
                    + "\n\n要压牌型\n" + advice.targetType
                    + "\n\n已出牌\n" + AssistantStore.playedText()
                    + "\n\n外面余牌估计\n" + advice.remainingSummary
                    + "\n\n识图状态\n" + AssistantStore.lastScanMessage();
        }
    }
}
