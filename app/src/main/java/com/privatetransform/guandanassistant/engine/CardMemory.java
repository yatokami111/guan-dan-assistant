package com.privatetransform.guandanassistant.engine;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class CardMemory {
    private final List<Card> playedCards = new ArrayList<>();

    public void addPlayed(List<Card> cards) {
        if (cards == null) return;
        Map<String, Integer> limits = fullDeckByRank();
        Map<String, Integer> current = countByRank(playedCards);
        for (Card card : cards) {
            int old = current.containsKey(card.rank) ? current.get(card.rank) : 0;
            int limit = limits.containsKey(card.rank) ? limits.get(card.rank) : 8;
            if (old >= limit) continue;
            playedCards.add(card);
            current.put(card.rank, old + 1);
        }
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
        Map<String, Integer> counts = countByRank(playedCards);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (sb.length() > 0) sb.append("  ");
            sb.append(entry.getKey()).append(":").append(entry.getValue());
        }
        return sb.toString();
    }

    public int playedCount() {
        return playedCards.size();
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

    private static Map<String, Integer> countByRank(List<Card> cards) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (Card card : cards) {
            String key = card.rank;
            counts.put(key, counts.containsKey(key) ? counts.get(key) + 1 : 1);
        }
        return counts;
    }
}
