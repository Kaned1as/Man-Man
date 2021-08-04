package com.adonai.manman.entities

import com.google.gson.Gson

/**
 * Object representing one of search results in corresponding API call
 *
 * @see Gson
 *
 * @author Kanedias
 */
// reflection in Gson
class SearchResult {
    lateinit var name: String
    lateinit var section: String
    lateinit var url: String
    var description: String? = null
}