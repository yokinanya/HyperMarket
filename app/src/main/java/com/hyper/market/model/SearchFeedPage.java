package com.hyper.market.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SearchFeedPage {
    private final List<MarketAppInfo> apps;
    private final boolean hasMore;

    public SearchFeedPage(List<MarketAppInfo> apps, boolean hasMore) {
        this.apps = Collections.unmodifiableList(new ArrayList<>(apps));
        this.hasMore = hasMore;
    }

    public List<MarketAppInfo> getApps() {
        return apps;
    }

    public boolean hasMore() {
        return hasMore;
    }
}
