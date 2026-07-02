package com.privatetransform.guandanassistant.engine;

import java.util.ArrayList;
import java.util.List;

public final class PlayAnalysis {
    public final PlayType type;
    public final int mainValue;
    public final int size;
    public final boolean soft;
    public final List<Card> cards;

    public PlayAnalysis(PlayType type, int mainValue, int size, boolean soft, List<Card> cards) {
        this.type = type;
        this.mainValue = mainValue;
        this.size = size;
        this.soft = soft;
        this.cards = new ArrayList<>(cards);
    }

    public boolean isBombLike() {
        return type == PlayType.BOMB || type == PlayType.STRAIGHT_FLUSH || type == PlayType.JOKER_BOMB;
    }

    public String describe() {
        String softLabel = soft ? "软" : "硬";
        switch (type) {
            case SINGLE: return "单张";
            case PAIR: return "对子";
            case TRIPLE: return "三同张";
            case FULL_HOUSE: return "三带二";
            case STRAIGHT: return "顺子";
            case SERIAL_PAIRS: return "连对";
            case PLANE: return "钢板";
            case BOMB: return softLabel + size + "张炸弹";
            case STRAIGHT_FLUSH: return softLabel + "同花顺";
            case JOKER_BOMB: return "天王炸";
            default: return "无效牌型";
        }
    }
}
