package com.privatetransform.guandanassistant;

import com.privatetransform.guandanassistant.capture.CardRecognizer;
import com.privatetransform.guandanassistant.engine.Card;
import com.privatetransform.guandanassistant.engine.CardMemory;
import com.privatetransform.guandanassistant.engine.CardParser;
import com.privatetransform.guandanassistant.engine.StrategyAdvisor;

import java.util.ArrayList;
import java.util.List;

public final class AssistantStore {
    public static final String ACTION_STATE_CHANGED = "com.privatetransform.guandanassistant.STATE_CHANGED";
    public static final String ACTION_SCAN_RESULT = "com.privatetransform.guandanassistant.SCAN_RESULT";
    public static final String EXTRA_TEXT = "text";

    private static String levelRank = "2";
    private static String opponentLevelRank = "?";
    private static List<Card> hand = new ArrayList<>();
    private static List<Card> lastPlay = new ArrayList<>();
    private static final CardMemory memory = new CardMemory();
    private static String lastScanMessage = "尚未开始实时视觉识别";
    private static boolean watching = false;
    private static String lastTableSignature = "";
    private static int scanCount = 0;
    private static int changeCount = 0;

    private AssistantStore() {}

    public static synchronized void setLevelRank(String rank) {
        levelRank = rank == null || rank.trim().isEmpty() ? "2" : rank.trim().toUpperCase().replace("T", "10");
    }

    public static synchronized void setOpponentLevelRank(String rank) {
        opponentLevelRank = rank == null || rank.trim().isEmpty() ? "?" : rank.trim().toUpperCase().replace("T", "10");
    }

    public static synchronized String levelRank() {
        return levelRank;
    }

    public static synchronized String opponentLevelRank() {
        return opponentLevelRank;
    }

    public static synchronized String levelSummary() {
        return "己方 " + levelRank + " / 对方 " + opponentLevelRank;
    }

    public static synchronized void setHandFromText(String text) {
        hand = CardParser.parseCards(text);
    }

    public static synchronized void setLastPlayFromText(String text) {
        lastPlay = CardParser.parseCards(text);
    }

    public static synchronized void addPlayedFromText(String text) {
        memory.addPlayed(CardParser.parseCards(text));
    }

    public static synchronized void resetMemory() {
        memory.reset();
        lastPlay.clear();
        lastTableSignature = "";
        scanCount = 0;
        changeCount = 0;
    }

    public static synchronized List<Card> hand() {
        return new ArrayList<>(hand);
    }

    public static synchronized String handText() {
        return StrategyAdvisor.labels(hand);
    }

    public static synchronized String lastPlayText() {
        return StrategyAdvisor.labels(lastPlay);
    }

    public static synchronized String playedText() {
        return "已记录" + memory.playedCount() + "张  " + memory.summarizePlayed();
    }

    public static synchronized String remainingText() {
        return memory.summarizeRemaining(hand);
    }

    public static synchronized String lastScanMessage() {
        return lastScanMessage;
    }

    public static synchronized boolean isWatching() {
        return watching;
    }

    public static synchronized void setWatching(boolean enabled) {
        watching = enabled;
        lastScanMessage = enabled ? "正在实时视觉识别" : "实时视觉识别已停止";
    }

    public static synchronized void updateScan(String message, List<Card> recognizedHand) {
        lastScanMessage = message;
        if (recognizedHand != null && !recognizedHand.isEmpty()) hand = new ArrayList<>(recognizedHand);
    }

    public static synchronized void applyRecognition(CardRecognizer.RecognitionResult result) {
        if (result == null) return;
        scanCount++;
        lastScanMessage = "第" + scanCount + "次检测：" + result.message;
        if (result.ownLevelRank != null && !result.ownLevelRank.trim().isEmpty()) {
            setLevelRank(result.ownLevelRank);
        }
        if (result.opponentLevelRank != null && !result.opponentLevelRank.trim().isEmpty()) {
            setOpponentLevelRank(result.opponentLevelRank);
        }
        if (result.cards == null || result.cards.isEmpty()) return;
        if (result.source == CardRecognizer.SOURCE_HAND) {
            hand = new ArrayList<>(result.cards);
            return;
        }
        if (result.source == CardRecognizer.SOURCE_TABLE_PLAY) {
            String signature = signature(result.cards);
            if (!signature.equals(lastTableSignature)) {
                lastPlay = new ArrayList<>(result.cards);
                memory.addPlayed(result.cards);
                lastTableSignature = signature;
                changeCount++;
                lastScanMessage = "第" + scanCount + "次检测：桌面牌变化" + changeCount + "次，已记入 " + StrategyAdvisor.labels(result.cards);
            }
        }
    }

    public static synchronized StrategyAdvisor.Advice advice() {
        return new StrategyAdvisor(levelRank).advise(hand, lastPlay, memory);
    }

    private static String signature(List<Card> cards) {
        StringBuilder sb = new StringBuilder();
        for (Card card : cards) {
            if (sb.length() > 0) sb.append('|');
            sb.append(card.suit).append(card.rank);
        }
        return sb.toString();
    }
}
