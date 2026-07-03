package com.privatetransform.guandanassistant.engine;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class StrategyAdvisor {
    private final HandAnalyzer analyzer;
    private final String levelRank;

    public StrategyAdvisor(String levelRank) {
        this.levelRank = levelRank == null ? "2" : levelRank.trim().toUpperCase().replace("T", "10");
        this.analyzer = new HandAnalyzer(this.levelRank);
    }

    public Advice advise(List<Card> hand, List<Card> target, CardMemory memory) {
        List<Card> safeHand = hand == null ? Collections.<Card>emptyList() : new ArrayList<>(hand);
        List<Card> safeTarget = target == null ? Collections.<Card>emptyList() : new ArrayList<>(target);
        PlayAnalysis targetAnalysis = analyzer.analyze(safeTarget);
        List<PlayAnalysis> candidates = findCandidates(safeHand, targetAnalysis);
        String primary;
        if (safeHand.isEmpty()) {
            primary = "还没有手牌数据。先启动实时识牌，或在手牌输入框手动录入。";
        } else if (safeTarget.isEmpty()) {
            primary = leadAdvice(safeHand, candidates);
        } else if (candidates.isEmpty()) {
            primary = "建议过牌。当前未找到能压住「" + targetAnalysis.describe() + "」的低成本牌型。";
        } else {
            PlayAnalysis best = candidates.get(0);
            primary = "建议出 " + labels(best.cards) + "（" + best.describe() + "）。";
            if (best.isBombLike() && !targetAnalysis.isBombLike()) {
                primary += " 这是炸弹压制，除非要抢出牌权，否则可以谨慎保留。";
            }
        }
        String memoryText = memory == null ? "" : memory.summarizeRemaining(safeHand);
        return new Advice(primary, targetAnalysis.describe(), memoryText, candidates);
    }

    private String leadAdvice(List<Card> hand, List<PlayAnalysis> candidates) {
        if (hand.size() <= 5) {
            PlayAnalysis all = analyzer.analyze(hand);
            if (all.type != PlayType.INVALID) return "可尝试一手走完：" + labels(hand) + "（" + all.describe() + "）。";
        }
        if (!candidates.isEmpty()) {
            PlayAnalysis first = candidates.get(0);
            return "建议首出低成本牌型：" + labels(first.cards) + "（" + first.describe() + "），尽量先整理散牌。";
        }
        List<Card> sorted = sortByPlayValue(hand);
        return "建议先出最小散牌：" + sorted.get(0).label() + "，保留炸弹、级牌和连牌结构。";
    }

    private List<PlayAnalysis> findCandidates(List<Card> hand, PlayAnalysis target) {
        List<PlayAnalysis> candidates = new ArrayList<>();
        addSingles(hand, target, candidates);
        addSameRank(hand, target, candidates, 2);
        addSameRank(hand, target, candidates, 3);
        addSameRank(hand, target, candidates, 4);
        addSameRank(hand, target, candidates, 5);
        addStraights(hand, target, candidates);
        addFullHouses(hand, target, candidates);
        Collections.sort(candidates, new Comparator<PlayAnalysis>() {
            @Override
            public int compare(PlayAnalysis a, PlayAnalysis b) {
                if (a.isBombLike() != b.isBombLike()) return a.isBombLike() ? 1 : -1;
                if (a.size != b.size) return a.size - b.size;
                return a.mainValue - b.mainValue;
            }
        });
        return candidates;
    }

    private void addSingles(List<Card> hand, PlayAnalysis target, List<PlayAnalysis> out) {
        for (Card card : hand) {
            PlayAnalysis analysis = analyzer.analyze(Collections.singletonList(card));
            if (target.type == PlayType.INVALID || analyzer.canBeat(analysis, target)) out.add(analysis);
        }
    }

    private void addSameRank(List<Card> hand, PlayAnalysis target, List<PlayAnalysis> out, int count) {
        Map<String, List<Card>> byRank = byRank(hand);
        for (List<Card> group : byRank.values()) {
            if (group.size() < count) continue;
            List<Card> cards = new ArrayList<>(group.subList(0, count));
            PlayAnalysis analysis = analyzer.analyze(cards);
            if (analysis.type != PlayType.INVALID && (target.type == PlayType.INVALID || analyzer.canBeat(analysis, target))) out.add(analysis);
        }
    }

    private void addStraights(List<Card> hand, PlayAnalysis target, List<PlayAnalysis> out) {
        List<Card> sorted = sortByPlayValue(hand);
        for (int i = 0; i + 5 <= sorted.size(); i++) {
            List<Card> slice = new ArrayList<>(sorted.subList(i, i + 5));
            PlayAnalysis analysis = analyzer.analyze(slice);
            if ((analysis.type == PlayType.STRAIGHT || analysis.type == PlayType.STRAIGHT_FLUSH)
                    && (target.type == PlayType.INVALID || analyzer.canBeat(analysis, target))) {
                out.add(analysis);
            }
        }
    }

    private void addFullHouses(List<Card> hand, PlayAnalysis target, List<PlayAnalysis> out) {
        Map<String, List<Card>> byRank = byRank(hand);
        for (List<Card> triple : byRank.values()) {
            if (triple.size() < 3) continue;
            for (List<Card> pair : byRank.values()) {
                if (pair == triple || pair.size() < 2) continue;
                List<Card> cards = new ArrayList<>();
                cards.addAll(triple.subList(0, 3));
                cards.addAll(pair.subList(0, 2));
                PlayAnalysis analysis = analyzer.analyze(cards);
                if (analysis.type == PlayType.FULL_HOUSE && (target.type == PlayType.INVALID || analyzer.canBeat(analysis, target))) {
                    out.add(analysis);
                }
            }
        }
    }

    private Map<String, List<Card>> byRank(List<Card> cards) {
        Map<String, List<Card>> byRank = new HashMap<>();
        for (Card card : cards) {
            List<Card> list = byRank.get(card.rank);
            if (list == null) {
                list = new ArrayList<>();
                byRank.put(card.rank, list);
            }
            list.add(card);
        }
        return byRank;
    }

    private List<Card> sortByPlayValue(List<Card> cards) {
        List<Card> sorted = new ArrayList<>(cards);
        Collections.sort(sorted, new Comparator<Card>() {
            @Override
            public int compare(Card a, Card b) {
                return Rank.playValue(a.rank, levelRank) - Rank.playValue(b.rank, levelRank);
            }
        });
        return sorted;
    }

    public static String labels(List<Card> cards) {
        StringBuilder sb = new StringBuilder();
        for (Card card : cards) {
            if (sb.length() > 0) sb.append(" ");
            sb.append(card.label());
        }
        return sb.toString();
    }

    public static final class Advice {
        public final String primary;
        public final String targetType;
        public final String remainingSummary;
        public final List<PlayAnalysis> candidates;

        Advice(String primary, String targetType, String remainingSummary, List<PlayAnalysis> candidates) {
            this.primary = primary;
            this.targetType = targetType;
            this.remainingSummary = remainingSummary;
            this.candidates = candidates;
        }
    }
}
