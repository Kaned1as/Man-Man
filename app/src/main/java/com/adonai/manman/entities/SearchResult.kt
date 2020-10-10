package com.adonai.manman.entities

/**
 * Object representing one of search results in corresponding API call
 *
 * @see com.google.gson.Gson
 *
 * @author Kanedias
 */
// reflection in Gson
class SearchResult {
    lateinit var name: String
    lateinit var section: String
    lateinit var description: String
    lateinit var url: String
}