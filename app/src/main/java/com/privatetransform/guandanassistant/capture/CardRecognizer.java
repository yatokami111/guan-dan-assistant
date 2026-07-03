package com.privatetransform.guandanassistant.capture;

import android.content.Context;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;

import com.privatetransform.guandanassistant.engine.Card;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class CardRecognizer {
    public static final int SOURCE_UNKNOWN = 0;
    public static final int SOURCE_HAND = 1;
    public static final int SOURCE_TABLE_PLAY = 2;

    private static final float SCALE = 0.20f;
    private static final int SEARCH_STEP = 8;
    private static final int MAX_FOREGROUND_POINTS = 160;
    private static final float LEVEL_MAX_DIFF = 0.22f;
    private static final float CARD_MAX_DIFF = 0.20f;

    private final List<Template> levelTemplates = new ArrayList<>();
    private final List<Template> cardTemplates = new ArrayList<>();

    public CardRecognizer(Context context) {
        loadTemplates(context.getAssets(), "templates/level", levelTemplates);
        loadTemplates(context.getAssets(), "templates/cards", cardTemplates);
    }

    public RecognitionResult recognize(Bitmap bitmap) {
        long startedAt = System.currentTimeMillis();
        if (bitmap == null) {
            return new RecognitionResult("未拿到屏幕帧", null, null, new ArrayList<Card>(), SOURCE_UNKNOWN, 0f);
        }
        if (levelTemplates.isEmpty() && cardTemplates.isEmpty()) {
            return new RecognitionResult("实时视觉识别中，但模板库为空", null, null, new ArrayList<Card>(), SOURCE_UNKNOWN, 0f);
        }

        Bitmap small = Bitmap.createScaledBitmap(
                bitmap,
                Math.max(1, Math.round(bitmap.getWidth() * SCALE)),
                Math.max(1, Math.round(bitmap.getHeight() * SCALE)),
                false);
        try {
            LevelPair levels = matchLevels(small);
            List<CardMatch> tableMatches = matchCardsInRegions(small, tableRegions(small.getWidth(), small.getHeight()));
            long elapsed = System.currentTimeMillis() - startedAt;

            if (!tableMatches.isEmpty()) {
                List<Card> cards = toCards(tableMatches);
                return new RecognitionResult(
                        "检测耗时" + elapsed + "ms，" + levels.describe() + "，桌面区域识别到 " + labels(cards),
                        levels.own,
                        levels.opponent,
                        cards,
                        SOURCE_TABLE_PLAY,
                        confidence(tableMatches));
            }
            String message = "检测耗时" + elapsed + "ms，" + levels.describe()
                    + "，桌面区域未匹配到新牌。";
            return new RecognitionResult(message, levels.own, levels.opponent, new ArrayList<Card>(), SOURCE_UNKNOWN, 0.15f);
        } finally {
            small.recycle();
        }
    }

    private LevelPair matchLevels(Bitmap small) {
        String own = matchLevelAt(small, rect(small.getWidth(), small.getHeight(), 0.070f, 0.010f, 0.108f, 0.075f));
        String opponent = matchLevelAt(small, rect(small.getWidth(), small.getHeight(), 0.070f, 0.074f, 0.108f, 0.132f));
        return new LevelPair(own, opponent);
    }

    private String matchLevelAt(Bitmap small, Rect levelRect) {
        if (levelTemplates.isEmpty()) return null;
        Match best = null;
        for (Template template : levelTemplates) {
            Bitmap scaled = template.scaled(Math.max(1, levelRect.width()), Math.max(1, levelRect.height()));
            float diff = diff(small, scaled, levelRect.left, levelRect.top);
            if (best == null || diff < best.diff) best = new Match(template.name, diff, levelRect.left, levelRect.top);
        }
        if (best != null && best.diff <= LEVEL_MAX_DIFF) return rankName(best.name);
        return null;
    }

    private List<CardMatch> matchCardsInRegions(Bitmap small, List<Rect> regions) {
        List<CardMatch> all = new ArrayList<>();
        for (Rect region : regions) {
            all.addAll(matchCardsInRegion(small, region));
        }
        return dedupe(all);
    }

    private List<CardMatch> matchCardsInRegion(Bitmap small, Rect region) {
        List<CardMatch> matches = new ArrayList<>();
        for (Template template : cardTemplates) {
            int tw = template.small.getWidth();
            int th = template.small.getHeight();
            if (tw >= region.width() || th >= region.height()) continue;
            Match best = null;
            for (int y = region.top; y <= region.bottom - th; y += SEARCH_STEP) {
                for (int x = region.left; x <= region.right - tw; x += SEARCH_STEP) {
                    float d = diffTemplate(small, template, x, y);
                    if (best == null || d < best.diff) best = new Match(template.name, d, x, y);
                }
            }
            if (best != null && best.diff <= CARD_MAX_DIFF) {
                matches.add(new CardMatch(template.card, best.diff, best.x, best.y, tw, th));
            }
        }
        Collections.sort(matches, new Comparator<CardMatch>() {
            @Override public int compare(CardMatch a, CardMatch b) {
                return Float.compare(a.diff, b.diff);
            }
        });
        return matches;
    }

    private List<CardMatch> dedupe(List<CardMatch> matches) {
        List<CardMatch> result = new ArrayList<>();
        for (CardMatch match : matches) {
            boolean near = false;
            for (CardMatch kept : result) {
                if (Math.abs(match.x - kept.x) < Math.max(match.w, kept.w) / 2
                        && Math.abs(match.y - kept.y) < Math.max(match.h, kept.h) / 2) {
                    near = true;
                    break;
                }
            }
            if (!near) result.add(match);
        }
        Collections.sort(result, new Comparator<CardMatch>() {
            @Override public int compare(CardMatch a, CardMatch b) {
                if (Math.abs(a.y - b.y) > 10) return a.y - b.y;
                return a.x - b.x;
            }
        });
        return result;
    }

    private float diff(Bitmap source, Bitmap template, int left, int top) {
        long sum = 0;
        int count = 0;
        for (int y = 0; y < template.getHeight(); y++) {
            for (int x = 0; x < template.getWidth(); x++) {
                int sp = source.getPixel(left + x, top + y);
                int tp = template.getPixel(x, y);
                int sr = (sp >> 16) & 0xff;
                int sg = (sp >> 8) & 0xff;
                int sb = sp & 0xff;
                int tr = (tp >> 16) & 0xff;
                int tg = (tp >> 8) & 0xff;
                int tb = tp & 0xff;
                if (!isForeground(tr, tg, tb)) continue;
                sum += Math.abs(sr - tr) + Math.abs(sg - tg) + Math.abs(sb - tb);
                count += 3;
            }
        }
        return count < 30 ? 1f : sum / (255f * count);
    }

    private float diffTemplate(Bitmap source, Template template, int left, int top) {
        if (template.fgCount < 10) return 1f;
        long sum = 0;
        for (int i = 0; i < template.fgCount; i++) {
            int sp = source.getPixel(left + template.fgX[i], top + template.fgY[i]);
            int sr = (sp >> 16) & 0xff;
            int sg = (sp >> 8) & 0xff;
            int sb = sp & 0xff;
            sum += Math.abs(sr - template.fgR[i])
                    + Math.abs(sg - template.fgG[i])
                    + Math.abs(sb - template.fgB[i]);
        }
        return sum / (255f * template.fgCount * 3f);
    }

    private boolean isForeground(int r, int g, int b) {
        int avg = (r + g + b) / 3;
        boolean neutralLight = Math.abs(r - g) < 8 && Math.abs(g - b) < 8 && avg > 180;
        return avg < 215 && !neutralLight;
    }

    private List<Rect> tableRegions(int w, int h) {
        List<Rect> regions = new ArrayList<>();
        regions.add(rect(w, h, 0.370f, 0.110f, 0.430f, 0.230f)); // top player out-card
        regions.add(rect(w, h, 0.075f, 0.280f, 0.150f, 0.450f)); // left player out-card
        regions.add(rect(w, h, 0.725f, 0.280f, 0.790f, 0.430f)); // right player out-card
        regions.add(rect(w, h, 0.070f, 0.620f, 0.145f, 0.835f)); // lower-left current out-card
        return regions;
    }

    private List<Rect> handRegions(int w, int h) {
        List<Rect> regions = new ArrayList<>();
        regions.add(rect(w, h, 0.180f, 0.420f, 0.780f, 0.890f));
        return regions;
    }

    private Rect rect(int w, int h, float l, float t, float r, float b) {
        return new Rect(
                clamp(Math.round(w * l), 0, w - 1),
                clamp(Math.round(h * t), 0, h - 1),
                clamp(Math.round(w * r), 1, w),
                clamp(Math.round(h * b), 1, h));
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private String rankName(String templateName) {
        int index = templateName.indexOf('_');
        return index < 0 ? templateName : templateName.substring(0, index);
    }

    private void loadTemplates(AssetManager assets, String path, List<Template> target) {
        try {
            String[] names = assets.list(path);
            if (names == null) return;
            for (String file : names) {
                if (!file.endsWith(".png")) continue;
                String name = file.substring(0, file.length() - 4);
                if (path.endsWith("/cards") && !isTableTemplate(name)) continue;
                try (InputStream input = assets.open(path + "/" + file)) {
                    Bitmap bitmap = BitmapFactory.decodeStream(input);
                    if (bitmap == null) continue;
                    target.add(new Template(name, bitmap));
                }
            }
        } catch (Exception ignored) {
            target.clear();
        }
    }

    private boolean isTableTemplate(String name) {
        return !name.contains("_hand");
    }

    private List<Card> toCards(List<CardMatch> matches) {
        List<Card> cards = new ArrayList<>();
        for (CardMatch match : matches) cards.add(match.card);
        return cards;
    }

    private float confidence(List<CardMatch> matches) {
        if (matches.isEmpty()) return 0f;
        float sum = 0f;
        for (CardMatch match : matches) sum += 1f - match.diff;
        return sum / matches.size();
    }

    private String labels(List<Card> cards) {
        StringBuilder sb = new StringBuilder();
        for (Card card : cards) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(card.label());
        }
        return sb.toString();
    }

    public static final class RecognitionResult {
        public final String message;
        public final String levelRank;
        public final String ownLevelRank;
        public final String opponentLevelRank;
        public final List<Card> cards;
        public final int source;
        public final float confidence;

        public RecognitionResult(String message, String ownLevelRank, String opponentLevelRank, List<Card> cards, int source, float confidence) {
            this.message = message;
            this.levelRank = ownLevelRank;
            this.ownLevelRank = ownLevelRank;
            this.opponentLevelRank = opponentLevelRank;
            this.cards = cards;
            this.source = source;
            this.confidence = confidence;
        }
    }

    private static final class LevelPair {
        final String own;
        final String opponent;

        LevelPair(String own, String opponent) {
            this.own = own;
            this.opponent = opponent;
        }

        String describe() {
            String ownText = own == null ? "?" : own;
            String opponentText = opponent == null ? "?" : opponent;
            return "己方打" + ownText + " / 对方打" + opponentText;
        }
    }

    private static final class Template {
        final String name;
        final Bitmap original;
        final Bitmap small;
        final Card card;
        final int[] fgX;
        final int[] fgY;
        final int[] fgR;
        final int[] fgG;
        final int[] fgB;
        final int fgCount;
        private final Map<String, Bitmap> scaledCache = new HashMap<>();

        Template(String name, Bitmap original) {
            this.name = name;
            this.original = original;
            this.small = Bitmap.createScaledBitmap(
                    original,
                    Math.max(1, Math.round(original.getWidth() * SCALE)),
                    Math.max(1, Math.round(original.getHeight() * SCALE)),
                    false);
            this.card = parseCard(name);
            Foreground foreground = foregroundOf(this.small);
            this.fgX = foreground.x;
            this.fgY = foreground.y;
            this.fgR = foreground.r;
            this.fgG = foreground.g;
            this.fgB = foreground.b;
            this.fgCount = foreground.count;
        }

        Bitmap scaled(int width, int height) {
            String key = width + "x" + height;
            Bitmap bitmap = scaledCache.get(key);
            if (bitmap == null) {
                bitmap = Bitmap.createScaledBitmap(original, width, height, false);
                scaledCache.put(key, bitmap);
            }
            return bitmap;
        }

        private static Card parseCard(String name) {
            if ("JOKER".equals(name)) return new Card("J", "BJ");
            String[] parts = name.split("_");
            if (parts.length < 2) return new Card("", name);
            return new Card(parts[0], parts[1]);
        }

        private static Foreground foregroundOf(Bitmap bitmap) {
            List<Integer> xs = new ArrayList<>();
            List<Integer> ys = new ArrayList<>();
            List<Integer> rs = new ArrayList<>();
            List<Integer> gs = new ArrayList<>();
            List<Integer> bs = new ArrayList<>();
            for (int y = 0; y < bitmap.getHeight(); y++) {
                for (int x = 0; x < bitmap.getWidth(); x++) {
                    int pixel = bitmap.getPixel(x, y);
                    int r = (pixel >> 16) & 0xff;
                    int g = (pixel >> 8) & 0xff;
                    int b = pixel & 0xff;
                    int avg = (r + g + b) / 3;
                    boolean neutralLight = Math.abs(r - g) < 8 && Math.abs(g - b) < 8 && avg > 180;
                    if (avg >= 215 || neutralLight) continue;
                    xs.add(x);
                    ys.add(y);
                    rs.add(r);
                    gs.add(g);
                    bs.add(b);
                }
            }
            return new Foreground(sample(xs), sample(ys), sample(rs), sample(gs), sample(bs));
        }

        private static int[] toArray(List<Integer> values) {
            int[] result = new int[values.size()];
            for (int i = 0; i < values.size(); i++) result[i] = values.get(i);
            return result;
        }

        private static int[] sample(List<Integer> values) {
            if (values.size() <= MAX_FOREGROUND_POINTS) return toArray(values);
            int[] result = new int[MAX_FOREGROUND_POINTS];
            for (int i = 0; i < MAX_FOREGROUND_POINTS; i++) {
                int index = Math.min(values.size() - 1, Math.round(i * (values.size() - 1f) / (MAX_FOREGROUND_POINTS - 1f)));
                result[i] = values.get(index);
            }
            return result;
        }
    }

    private static final class Foreground {
        final int[] x;
        final int[] y;
        final int[] r;
        final int[] g;
        final int[] b;
        final int count;

        Foreground(int[] x, int[] y, int[] r, int[] g, int[] b) {
            this.x = x;
            this.y = y;
            this.r = r;
            this.g = g;
            this.b = b;
            this.count = x.length;
        }
    }

    private static final class Match {
        final String name;
        final float diff;
        final int x;
        final int y;

        Match(String name, float diff, int x, int y) {
            this.name = name;
            this.diff = diff;
            this.x = x;
            this.y = y;
        }
    }

    private static final class CardMatch {
        final Card card;
        final float diff;
        final int x;
        final int y;
        final int w;
        final int h;

        CardMatch(Card card, float diff, int x, int y, int w, int h) {
            this.card = card;
            this.diff = diff;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }
    }

    private static final class Rect {
        final int left;
        final int top;
        final int right;
        final int bottom;

        Rect(int left, int top, int right, int bottom) {
            this.left = left;
            this.top = top;
            this.right = Math.max(right, left + 1);
            this.bottom = Math.max(bottom, top + 1);
        }

        int width() {
            return right - left;
        }

        int height() {
            return bottom - top;
        }
    }
}
