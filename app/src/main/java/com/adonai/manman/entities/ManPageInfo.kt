package com.adonai.manman.entities

/**
 * Object representing answer on description API-call
 *
 * @see com.google.gson.Gson
 *
 * @author Kanedias
 */
// reflection in Gson
class ManPageInfo {
    lateinit var name: String
    lateinit var url: String

    var section: String? = null
    var description: String? = null

    var sections: List<InfoSection> = emptyList()
    var anchors: List<InfoAnchor> = emptyList()

    class InfoSection {
        var id: String? = null
        var title: String? = null
        var url: String? = null

        // inner sub-sections
        var sections: List<InfoSection> = emptyList()
    }

    class InfoAnchor {
        var anchor: String? = null
        var description: String? = null
        var url: String? = null
    }
}