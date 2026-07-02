package com.privatetransform.guandanassistant.engine;

import java.util.Locale;
import java.util.Objects;

public final class Card {
    public final String suit;
    public final String rank;

    public Card(String suit, String rank) {
        this.suit = suit == null ? "" : suit.toUpperCase(Locale.ROOT);
        this.rank = rank == null ? "" : rank.toUpperCase(Locale.ROOT);
    }

    public boolean isJoker() {
        return "BJ".equals(rank) || "SJ".equals(rank);
    }

    public boolean isWild(String levelRank) {
        return "H".equals(suit) && rank.equalsIgnoreCase(levelRank);
    }

    public String label() {
        if ("BJ".equals(rank)) return "大王";
        if ("SJ".equals(rank)) return "小王";
        String suitLabel;
        switch (suit) {
            case "S": suitLabel = "♠"; break;
            case "H": suitLabel = "♥"; break;
            case "C": suitLabel = "♣"; break;
            case "D": suitLabel = "♦"; break;
            default: suitLabel = "";
        }
        return suitLabel + rank;
    }

    @Override
    public String toString() {
        return label();
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof Card)) return false;
        Card card = (Card) other;
        return suit.equals(card.suit) && rank.equals(card.rank);
    }

    @Override
    public int hashCode() {
        return Objects.hash(suit, rank);
    }
}
