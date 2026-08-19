package com.hyper.market.model;

public final class DetailComment {
    private final String userName;
    private final String content;
    private final double score;

    public DetailComment(String userName, String content, double score) {
        this.userName = userName;
        this.content = content;
        this.score = score;
    }

    public String getUserName() { return userName; }
    public String getContent() { return content; }
    public double getScore() { return score; }
}
