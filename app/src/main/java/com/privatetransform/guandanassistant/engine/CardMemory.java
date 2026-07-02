package com.privatetransform.guandanassistant.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CardMemory {
    private final List<Card> playedCards = new ArrayList<>();

    public void addPlayed(List<Card> cards) {
        if (cards != null) playedCards.addAll(cards);
    }

    public void reset() {
        playedCards.clear();
    }

    public List<Card> playedCards() {
        return new ArrayList<>(playedCards);
    }

    public String summarizeRemaining(List<Card> myHand) {
        Map<String, Integer> remaining = fullDeckByRank();
        reduce(remaining, playedCards);
        reduce(remaining, myHand);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> entry : remaining.entrySet()) {
            if (entry.getValue() > 0) {
                if (sb.length() > 0) sb.append("  ");
                sb.append(entry.getKey()).append(":").append(entry.getValue());
            }
        }
        return sb.length() == 0 ? "外面无剩余牌记录" : sb.toString();
    }

    public String summarizePlayed() {
        if (playedCards.isEmpty()) return "暂未记录已出牌";
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Card card : playedCards) {
            String key = card.rank;
            counts.put(key, counts.containsKey(key) ? counts.get(key) + 1 : 1);
        }
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (sb.length() > 0) sb.append("  ");
            sb.append(entry.getKey()).append(":").append(entry.getValue());
        }
        return sb.toString();
    }

    private static Map<String, Integer> fullDeckByRank() {
        Map<String, Integer> deck = new LinkedHashMap<>();
        String[] ranks = {"BJ", "SJ", "A", "K", "Q", "J", "10", "9", "8", "7", "6", "5", "4", "3", "2"};
        for (String rank : ranks) deck.put(rank, ("BJ".equals(rank) || "SJ".equals(rank)) ? 2 : 8);
        return deck;
    }

    private static void reduce(Map<String, Integer> counts, List<Card> cards) {
        if (cards == null) return;
        for (Card card : cards) {
            Integer old = counts.get(card.rank);
            if (old != null && old > 0) counts.put(card.rank, old - 1);
        }
    }
}
