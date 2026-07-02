package com.privatetransform.guandanassistant;

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
    private static List<Card> hand = new ArrayList<>();
    private static List<Card> lastPlay = new ArrayList<>();
    private static final CardMemory memory = new CardMemory();
    private static String lastScanMessage = "尚未截图识别";

    private AssistantStore() {}

    public static synchronized void setLevelRank(String rank) {
        levelRank = rank == null || rank.trim().isEmpty() ? "2" : rank.trim().toUpperCase().replace("T", "10");
    }

    public static synchronized String levelRank() {
        return levelRank;
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
        return memory.summarizePlayed();
    }

    public static synchronized String lastScanMessage() {
        return lastScanMessage;
    }

    public static synchronized void updateScan(String message, List<Card> recognizedHand) {
        lastScanMessage = message;
        if (recognizedHand != null && !recognizedHand.isEmpty()) hand = new ArrayList<>(recognizedHand);
    }

    public static synchronized StrategyAdvisor.Advice advice() {
        return new StrategyAdvisor(levelRank).advise(hand, lastPlay, memory);
    }
}
