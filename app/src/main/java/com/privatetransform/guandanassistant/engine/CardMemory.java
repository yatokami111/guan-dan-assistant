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

    public String summarizeRemainingWithTotal(List<Card> myHand) {
        Map<String, Integer> remaining = fullDeckByRank();
        reduce(remaining, playedCards);
        reduce(remaining, myHand);
        int total = 0;
        for (Integer count : remaining.values()) total += count;
        return "剩余" + total + "张  " + summarizeCounts(remaining);
    }

    public String summarizeRemainingDetail(List<Card> myHand) {
        Map<String, Integer> remaining = fullDeckByCard();
        reduceExact(remaining, playedCards);
        reduceExact(remaining, myHand);
        StringBuilder sb = new StringBuilder();
        appendIfAny(sb, remaining, "大王", "BJ");
        appendIfAny(sb, remaining, "小王", "SJ");
        String[] ranks = {"A", "K", "Q", "J", "10", "9", "8", "7", "6", "5", "4", "3", "2"};
        String[] suits = {"S", "H", "C", "D"};
        for (String rank : ranks) {
            StringBuilder line = new StringBuilder(rank).append("：");
            boolean hasAny = false;
            for (String suit : suits) {
                String key = suit + "_" + rank;
                Integer count = remaining.get(key);
                if (count != null && count > 0) {
                    if (hasAny) line.append(" ");
                    line.append(suitLabel(suit)).append(count);
                    hasAny = true;
                }
            }
            if (hasAny) {
                if (sb.length() > 0) sb.append('\n');
                sb.append(line);
            }
        }
        return sb.length() == 0 ? "无剩余牌" : sb.toString();
    }

    public String summarizePlayed() {
        if (playedCards.isEmpty()) return "暂未记录已出牌";
        Map<String, Integer> counts = countByRank(playedCards);
        return summarizeCounts(counts);
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

    private static Map<String, Integer> fullDeckByCard() {
        Map<String, Integer> deck = new LinkedHashMap<>();
        deck.put("BJ", 2);
        deck.put("SJ", 2);
        String[] ranks = {"A", "K", "Q", "J", "10", "9", "8", "7", "6", "5", "4", "3", "2"};
        String[] suits = {"S", "H", "C", "D"};
        for (String rank : ranks) {
            for (String suit : suits) deck.put(suit + "_" + rank, 2);
        }
        return deck;
    }

    private static void reduce(Map<String, Integer> counts, List<Card> cards) {
        if (cards == null) return;
        for (Card card : cards) {
            Integer old = counts.get(card.rank);
            if (old != null && old > 0) counts.put(card.rank, old - 1);
        }
    }

    private static void reduceExact(Map<String, Integer> counts, List<Card> cards) {
        if (cards == null) return;
        for (Card card : cards) {
            String key = exactKey(card);
            Integer old = counts.get(key);
            if (old != null && old > 0) {
                counts.put(key, old - 1);
                continue;
            }
            reduceAnySuit(counts, card.rank);
        }
    }

    private static String exactKey(Card card) {
        if ("BJ".equals(card.rank) || "SJ".equals(card.rank)) return card.rank;
        return card.suit + "_" + card.rank;
    }

    private static void reduceAnySuit(Map<String, Integer> counts, String rank) {
        String[] suits = {"S", "H", "C", "D"};
        for (String suit : suits) {
            String key = suit + "_" + rank;
            Integer old = counts.get(key);
            if (old != null && old > 0) {
                counts.put(key, old - 1);
                return;
            }
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

    private static String summarizeCounts(Map<String, Integer> counts) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Integer> entry : counts.entrySet()) {
            if (entry.getValue() > 0) {
                if (sb.length() > 0) sb.append("  ");
                sb.append(entry.getKey()).append(":").append(entry.getValue());
            }
        }
        return sb.toString();
    }

    private static void appendIfAny(StringBuilder sb, Map<String, Integer> counts, String label, String key) {
        Integer count = counts.get(key);
        if (count != null && count > 0) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(label).append("：").append(count);
        }
    }

    private static String suitLabel(String suit) {
        if ("S".equals(suit)) return "♠";
        if ("H".equals(suit)) return "♥";
        if ("C".equals(suit)) return "♣";
        if ("D".equals(suit)) return "♦";
        return suit;
    }
}
