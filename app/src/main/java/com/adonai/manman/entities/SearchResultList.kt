package com.adonai.manman.entities

import com.google.gson.annotations.SerializedName

/**
 * Object representing search result list in API call
 * Contains several search results or null if no results found
 *
 * @see com.google.gson.Gson
 *
 * @author Kanedias
 */
// reflection in Gson
class SearchResultList {
    @SerializedName("q")
    lateinit var query: String

    var results: List<SearchResult> = emptyList()

    var truncated = false
}