package com.adonai.manman.entities

import com.j256.ormlite.field.DatabaseField
import com.j256.ormlite.table.DatabaseTable

/**
 * Object represents an item in browse-chapter page of mankier.com
 * It also serves as DB entity-mirroring class
 *
 * @author Kanedias
 */
@DatabaseTable(tableName = "man_chapters")
class ManSectionItem : Comparable<ManSectionItem> {

    // these are filled by page
    @DatabaseField(id = true)
    lateinit var url: String

    @DatabaseField(canBeNull = false, index = true)
    lateinit var name: String

    @DatabaseField(canBeNull = false)
    lateinit var description: String

    // this is filled with constant parent chapter from resources
    @DatabaseField(index = true, canBeNull = false)
    lateinit var parentChapter: String

    override fun compareTo(other: ManSectionItem): Int {
        return name.compareTo(other.name)
    }
}