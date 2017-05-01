package com.adonai.manman.entities;

import com.google.gson.annotations.JsonAdapter;
import com.google.gson.annotations.SerializedName;

import java.util.List;

/**
 * Object representing search result list in API call
 * Contains several search results or null if no results found
 *
 * @see com.google.gson.Gson
 * @author Oleg Chernovskiy
 */
@SuppressWarnings("UnusedDeclaration") // reflection in Gson
public class SearchResultList {

    @SerializedName("q")
    private String query;

    private boolean truncated;

    private List<SearchResult> results;

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public boolean getTruncated() {
        return truncated;
    }

    public void setTruncated(boolean truncated) {
        this.truncated = truncated;
    }

    public List<SearchResult> getResults() {
        return results;
    }

    public void setResults(List<SearchResult> results) {
        this.results = results;
    }
}
