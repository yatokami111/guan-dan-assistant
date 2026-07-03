package com.privatetransform.guandanassistant;

import android.accessibilityservice.AccessibilityService;
import android.content.Intent;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class GuanDanAccessibilityService extends AccessibilityService {
    private static final Pattern LEVEL_PATTERN = Pattern.compile("(?:打|级牌|当前级数)\\s*([2-9AJQK]|10)");

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        AccessibilityNodeInfo root = getRootInActiveWindow();
        if (root == null) return;
        String text = collectText(root, 0);
        Matcher matcher = LEVEL_PATTERN.matcher(text);
        if (matcher.find()) {
            AssistantStore.setLevelRank(matcher.group(1));
            sendBroadcast(new Intent(AssistantStore.ACTION_STATE_CHANGED).setPackage(getPackageName()));
        }
    }

    @Override
    public void onInterrupt() {
    }

    private String collectText(AccessibilityNodeInfo node, int depth) {
        if (node == null || depth > 8) return "";
        StringBuilder sb = new StringBuilder();
        CharSequence text = node.getText();
        CharSequence description = node.getContentDescription();
        if (text != null) sb.append(text).append(' ');
        if (description != null) sb.append(description).append(' ');
        for (int i = 0; i < node.getChildCount(); i++) {
            sb.append(collectText(node.getChild(i), depth + 1));
        }
        return sb.toString();
    }
}
