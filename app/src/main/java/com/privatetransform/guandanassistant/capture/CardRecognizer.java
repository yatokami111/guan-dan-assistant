package com.privatetransform.guandanassistant.capture;

import android.graphics.Bitmap;

import com.privatetransform.guandanassistant.engine.Card;

import java.util.ArrayList;
import java.util.List;

public final class CardRecognizer {
    public RecognitionResult recognize(Bitmap bitmap) {
        if (bitmap == null) return new RecognitionResult("未拿到屏幕帧", new ArrayList<Card>());
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        int brightSamples = 0;
        int samples = 0;
        int stepX = Math.max(1, width / 48);
        int startY = (int) (height * 0.62f);
        for (int y = startY; y < height; y += Math.max(1, height / 80)) {
            for (int x = 0; x < width; x += stepX) {
                int pixel = bitmap.getPixel(x, y);
                int r = (pixel >> 16) & 0xff;
                int g = (pixel >> 8) & 0xff;
                int b = pixel & 0xff;
                if ((r + g + b) / 3 > 180) brightSamples++;
                samples++;
            }
        }
        String message = "已获取屏幕帧 " + width + "x" + height
                + "，底部亮色采样 " + brightSamples + "/" + samples
                + "。当前版本已打通截图入口，正式识牌需补充目标游戏牌面模板或模型。";
        return new RecognitionResult(message, new ArrayList<Card>());
    }

    public static final class RecognitionResult {
        public final String message;
        public final List<Card> cards;

        RecognitionResult(String message, List<Card> cards) {
            this.message = message;
            this.cards = cards;
        }
    }
}
