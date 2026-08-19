package com.hyper.market.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class TodayFeedPage {
    private final List<TodayFeaturedItem> items;
    private final boolean hasMore;

    public TodayFeedPage(List<TodayFeaturedItem> items, boolean hasMore) {
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
        this.hasMore = hasMore;
    }

    public List<TodayFeaturedItem> getItems() {
        return items;
    }

    public boolean hasMore() {
        return hasMore;
    }
}
