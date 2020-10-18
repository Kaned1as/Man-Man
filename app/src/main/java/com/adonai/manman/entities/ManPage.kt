package com.adonai.manman.entities

import com.j256.ormlite.field.DataType
import com.j256.ormlite.field.DatabaseField
import com.j256.ormlite.table.DatabaseTable
import com.adonai.manman.ManPageDialogFragment
import java.util.*

/**
 * Holder for caching man-page contents to DB
 * Represents man page contents and provides relation to chapter page with description if possible
 *
 * The fields "url" and "name" are not foreign for [ManSectionItem]
 * as they can be retrieved from search page, not contents
 *
 * @see ManPageDialogFragment
 * @see ManCacheFragment
 *
 * @author Kanedias
 */
@DatabaseTable(tableName = "man_pages")
class ManPage {

    constructor(name: String, url: String) {
        this.name = name
        this.url = url
    }

    @Suppress("unused")
    constructor()

    @DatabaseField(id = true, canBeNull = false)
    lateinit var url: String

    @DatabaseField(canBeNull = false)
    lateinit var name: String

    @DatabaseField(dataType = DataType.LONG_STRING, canBeNull = false)
    lateinit var webContent: String

    @DatabaseField(dataType = DataType.SERIALIZABLE, canBeNull = false)
    var links = TreeSet<String>()
}