package com.privatetransform.guandanassistant.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class HandAnalyzer {
    private final String levelRank;

    public HandAnalyzer(String levelRank) {
        this.levelRank = normalizeLevel(levelRank);
    }

    public PlayAnalysis analyze(List<Card> cards) {
        if (cards == null || cards.isEmpty()) return invalid(cards);
        List<Card> safe = new ArrayList<>(cards);
        if (isJokerBomb(safe)) return new PlayAnalysis(PlayType.JOKER_BOMB, 1000, safe.size(), false, safe);
        PlayAnalysis flush = analyzeStraightFlush(safe);
        PlayAnalysis grouped = analyzeGrouped(safe);
        if (flush.type == PlayType.STRAIGHT_FLUSH && grouped.type != PlayType.BOMB) return flush;
        if (flush.type == PlayType.STRAIGHT_FLUSH && compareBomb(flush, grouped) > 0) return flush;
        return grouped;
    }

    public boolean canBeat(PlayAnalysis mine, PlayAnalysis target) {
        if (mine == null || target == null || mine.type == PlayType.INVALID) return false;
        if (target.type == PlayType.INVALID) return true;
        if (mine.isBombLike() || target.isBombLike()) return compareBomb(mine, target) > 0;
        return mine.type == target.type && mine.size == target.size && mine.mainValue > target.mainValue;
    }

    public int compareBomb(PlayAnalysis left, PlayAnalysis right) {
        if (left.type == PlayType.JOKER_BOMB) return right.type == PlayType.JOKER_BOMB ? 0 : 1;
        if (right.type == PlayType.JOKER_BOMB) return -1;
        if (!left.isBombLike() && right.isBombLike()) return -1;
        if (left.isBombLike() && !right.isBombLike()) return 1;
        if (!left.isBombLike()) return 0;
        int leftTier = bombTier(left);
        int rightTier = bombTier(right);
        if (leftTier != rightTier) return leftTier - rightTier;
        if (left.soft != right.soft) return left.soft ? -1 : 1;
        return left.mainValue - right.mainValue;
    }

    private PlayAnalysis analyzeGrouped(List<Card> cards) {
        int wild = countWild(cards);
        Map<String, List<Card>> byRank = byRankNoWild(cards);
        int size = cards.size();
        if (size == 1) return new PlayAnalysis(PlayType.SINGLE, Rank.playValue(cards.get(0).rank, levelRank), size, false, cards);
        if (size == 2 && canMakeSame(byRank, wild, 2)) return same(cards, byRank, wild, PlayType.PAIR);
        if (size == 3 && canMakeSame(byRank, wild, 3)) return same(cards, byRank, wild, PlayType.TRIPLE);
        if (size >= 4 && canMakeSame(byRank, wild, size)) return same(cards, byRank, wild, PlayType.BOMB);
        if (size == 5) {
            PlayAnalysis fullHouse = analyzeFullHouse(cards, byRank, wild);
            if (fullHouse.type != PlayType.INVALID) return fullHouse;
            PlayAnalysis straight = analyzeStraight(cards, wild);
            if (straight.type != PlayType.INVALID) return straight;
        }
        if (size >= 6 && size % 2 == 0) {
            PlayAnalysis pairs = analyzeSerialPairs(cards, wild);
            if (pairs.type != PlayType.INVALID) return pairs;
        }
        if (size >= 6) {
            PlayAnalysis plane = analyzePlane(cards, wild);
            if (plane.type != PlayType.INVALID) return plane;
        }
        return invalid(cards);
    }

    private PlayAnalysis analyzeFullHouse(List<Card> cards, Map<String, List<Card>> byRank, int wild) {
        for (String tripleRank : Rank.straightRanks()) {
            int needTriple = Math.max(0, 3 - count(byRank, tripleRank));
            if (needTriple > wild) continue;
            int restWild = wild - needTriple;
            for (String pairRank : Rank.straightRanks()) {
                if (pairRank.equals(tripleRank)) continue;
                int needPair = Math.max(0, 2 - count(byRank, pairRank));
                if (needPair <= restWild) {
                    return new PlayAnalysis(PlayType.FULL_HOUSE, Rank.playValue(tripleRank, levelRank), 5, wild > 0, cards);
                }
            }
        }
        return invalid(cards);
    }

    private PlayAnalysis analyzeStraight(List<Card> cards, int wild) {
        if (cards.size() != 5 || hasRank(cards, "2") || hasJoker(cards)) return invalid(cards);
        for (int start = 0; start <= Rank.straightRanks().size() - 5; start++) {
            int missing = missingRanks(cards, Rank.straightRanks().subList(start, start + 5));
            if (missing <= wild) {
                String high = Rank.straightRanks().get(start + 4);
                return new PlayAnalysis(PlayType.STRAIGHT, Rank.normalValue(high), cards.size(), wild > 0, cards);
            }
        }
        return invalid(cards);
    }

    private PlayAnalysis analyzeSerialPairs(List<Card> cards, int wild) {
        if (hasRank(cards, "2") || hasJoker(cards)) return invalid(cards);
        int pairCount = cards.size() / 2;
        if (pairCount < 3) return invalid(cards);
        Map<String, List<Card>> byRank = byRankNoWild(cards);
        for (int start = 0; start <= Rank.straightRanks().size() - pairCount; start++) {
            int missing = 0;
            for (String rank : Rank.straightRanks().subList(start, start + pairCount)) {
                missing += Math.max(0, 2 - count(byRank, rank));
            }
            if (missing <= wild) {
                String high = Rank.straightRanks().get(start + pairCount - 1);
                return new PlayAnalysis(PlayType.SERIAL_PAIRS, Rank.normalValue(high), cards.size(), wild > 0, cards);
            }
        }
        return invalid(cards);
    }

    private PlayAnalysis analyzePlane(List<Card> cards, int wild) {
        if (hasRank(cards, "2") || hasJoker(cards)) return invalid(cards);
        Map<String, List<Card>> byRank = byRankNoWild(cards);
        for (int len = 2; len <= 4; len++) {
            int bodySize = len * 3;
            if (cards.size() != bodySize && cards.size() != bodySize + len && cards.size() != bodySize + len * 2) continue;
            for (int start = 0; start <= Rank.straightRanks().size() - len; start++) {
                int missing = 0;
                for (String rank : Rank.straightRanks().subList(start, start + len)) {
                    missing += Math.max(0, 3 - count(byRank, rank));
                }
                if (missing <= wild) {
                    String high = Rank.straightRanks().get(start + len - 1);
                    return new PlayAnalysis(PlayType.PLANE, Rank.normalValue(high), cards.size(), wild > 0, cards);
                }
            }
        }
        return invalid(cards);
    }

    private PlayAnalysis analyzeStraightFlush(List<Card> cards) {
        if (cards.size() != 5 || hasRank(cards, "2") || hasJoker(cards)) return invalid(cards);
        int wild = countWild(cards);
        Set<String> suits = new HashSet<>();
        for (Card card : cards) if (!card.isWild(levelRank)) suits.add(card.suit);
        if (suits.size() > 1) return invalid(cards);
        PlayAnalysis straight = analyzeStraight(cards, wild);
        if (straight.type == PlayType.STRAIGHT) {
            return new PlayAnalysis(PlayType.STRAIGHT_FLUSH, straight.mainValue, 5, wild > 0, cards);
        }
        return invalid(cards);
    }

    private PlayAnalysis same(List<Card> cards, Map<String, List<Card>> byRank, int wild, PlayType type) {
        String bestRank = "2";
        for (String rank : byRank.keySet()) {
            if (Rank.playValue(rank, levelRank) > Rank.playValue(bestRank, levelRank)) bestRank = rank;
        }
        return new PlayAnalysis(type, Rank.playValue(bestRank, levelRank), cards.size(), wild > 0, cards);
    }

    private boolean canMakeSame(Map<String, List<Card>> byRank, int wild, int size) {
        for (String rank : Rank.straightRanks()) {
            if (count(byRank, rank) + wild >= size) return true;
        }
        if (count(byRank, "2") + wild >= size) return true;
        return false;
    }

    private boolean isJokerBomb(List<Card> cards) {
        if (cards.size() != 4) return false;
        int big = 0;
        int small = 0;
        for (Card card : cards) {
            if ("BJ".equals(card.rank)) big++;
            if ("SJ".equals(card.rank)) small++;
        }
        return big == 2 && small == 2;
    }

    private int bombTier(PlayAnalysis analysis) {
        if (analysis.type == PlayType.STRAIGHT_FLUSH) return 5;
        if (analysis.type == PlayType.BOMB) {
            if (analysis.size >= 6) return analysis.size + 10;
            return analysis.size;
        }
        return 0;
    }

    private int missingRanks(List<Card> cards, List<String> ranks) {
        Set<String> have = new HashSet<>();
        for (Card card : cards) if (!card.isWild(levelRank)) have.add(card.rank);
        int missing = 0;
        for (String rank : ranks) if (!have.contains(rank)) missing++;
        return missing;
    }

    private Map<String, List<Card>> byRankNoWild(List<Card> cards) {
        Map<String, List<Card>> result = new HashMap<>();
        for (Card card : cards) {
            if (card.isWild(levelRank)) continue;
            List<Card> list = result.get(card.rank);
            if (list == null) {
                list = new ArrayList<>();
                result.put(card.rank, list);
            }
            list.add(card);
        }
        return result;
    }

    private int count(Map<String, List<Card>> byRank, String rank) {
        List<Card> list = byRank.get(rank);
        return list == null ? 0 : list.size();
    }

    private int countWild(List<Card> cards) {
        int count = 0;
        for (Card card : cards) if (card.isWild(levelRank)) count++;
        return count;
    }

    private boolean hasRank(List<Card> cards, String rank) {
        for (Card card : cards) if (rank.equals(card.rank)) return true;
        return false;
    }

    private boolean hasJoker(List<Card> cards) {
        for (Card card : cards) if (card.isJoker()) return true;
        return false;
    }

    private PlayAnalysis invalid(List<Card> cards) {
        return new PlayAnalysis(PlayType.INVALID, -1, cards == null ? 0 : cards.size(), false, cards == null ? Collections.<Card>emptyList() : cards);
    }

    private static String normalizeLevel(String levelRank) {
        if (levelRank == null || levelRank.trim().isEmpty()) return "2";
        String level = levelRank.trim().toUpperCase().replace("T", "10");
        return Rank.isRank(level) && !"BJ".equals(level) && !"SJ".equals(level) ? level : "2";
    }
}
