package com.privatetransform.guandanassistant.engine;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class CardParser {
    private CardParser() {}

    public static List<Card> parseCards(String input) {
        List<Card> cards = new ArrayList<>();
        if (input == null || input.trim().isEmpty()) return cards;
        String normalized = input
                .replace("红桃", " H").replace("黑桃", " S")
                .replace("梅花", " C").replace("草花", " C")
                .replace("方块", " D").replace("方片", " D")
                .replace("♥", " H").replace("♠", " S")
                .replace("♣", " C").replace("♦", " D")
                .replace("大王", " BJ ").replace("小王", " SJ ")
                .replace(",", " ").replace("，", " ")
                .replace("、", " ").replace(";", " ")
                .replace("；", " ").replace("\n", " ");
        for (String raw : normalized.trim().split("\\s+")) {
            Card card = parseOne(raw);
            if (card != null) cards.add(card);
        }
        return cards;
    }

    public static Card parseOne(String raw) {
        if (raw == null) return null;
        String token = raw.trim().toUpperCase(Locale.ROOT);
        if (token.isEmpty()) return null;
        if ("BJ".equals(token) || "大王".equals(token)) return new Card("J", "BJ");
        if ("SJ".equals(token) || "小王".equals(token)) return new Card("J", "SJ");
        token = token.replace("10", "T");
        String suit = "";
        char first = token.charAt(0);
        if (first == 'S' || first == 'H' || first == 'C' || first == 'D') {
            suit = String.valueOf(first);
            token = token.substring(1);
        }
        if (token.length() == 0) return null;
        String rank = token.replace("T", "10");
        if (!Rank.isRank(rank)) return null;
        return new Card(suit, rank);
    }
}
