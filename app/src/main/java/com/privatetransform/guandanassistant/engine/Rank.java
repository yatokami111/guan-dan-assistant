package com.privatetransform.guandanassistant.engine;

import java.util.Arrays;
import java.util.List;

public final class Rank {
    private static final List<String> NORMAL = Arrays.asList("2", "3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A");
    private static final List<String> STRAIGHT = Arrays.asList("3", "4", "5", "6", "7", "8", "9", "10", "J", "Q", "K", "A");

    private Rank() {}

    public static boolean isRank(String rank) {
        return NORMAL.contains(rank) || "SJ".equals(rank) || "BJ".equals(rank);
    }

    public static int normalValue(String rank) {
        int index = NORMAL.indexOf(rank);
        return index < 0 ? -1 : index;
    }

    public static int playValue(String rank, String levelRank) {
        if ("BJ".equals(rank)) return 100;
        if ("SJ".equals(rank)) return 99;
        if (rank.equals(levelRank)) return 98;
        return normalValue(rank);
    }

    public static int bombValue(String rank) {
        if ("BJ".equals(rank)) return 100;
        if ("SJ".equals(rank)) return 99;
        if ("2".equals(rank)) return 1;
        return normalValue(rank) + 10;
    }

    public static List<String> straightRanks() {
        return STRAIGHT;
    }
}
