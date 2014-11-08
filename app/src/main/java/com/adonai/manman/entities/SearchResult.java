package com.adonai.manman.entities;

/**
 * Object representing one of search results in corresponding API call
 *
 * @see com.google.gson.Gson
 * @author Adonai
 */
@SuppressWarnings("UnusedDeclaration") // reflection in Gson
public class SearchResult {
    private String text;
    private String url;

    public String getText() {
        return text;
    }

    public void setText(String text) {
        this.text = text;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
