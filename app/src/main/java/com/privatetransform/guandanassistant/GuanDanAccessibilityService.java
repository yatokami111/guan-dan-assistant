package com.privatetransform.guandanassistant;

import android.accessibilityservice.AccessibilityService;
import android.view.accessibility.AccessibilityEvent;

public final class GuanDanAccessibilityService extends AccessibilityService {
    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Reserved for future page detection. Screen cards are captured through MediaProjection.
    }

    @Override
    public void onInterrupt() {
    }
}
