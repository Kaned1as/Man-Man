package com.adonai.manman.entities;

/**
 * Object representing one of search results in corresponding API call
 *
 * @see com.google.gson.Gson
 * @author Oleg Chernovskiy
 */
@SuppressWarnings("UnusedDeclaration") // reflection in Gson
public class SearchResult {
    private String name;
    private String section;
    private String description;
    private String url;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }
}
