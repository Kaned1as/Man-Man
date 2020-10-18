package com.adonai.manman.entities

import com.j256.ormlite.field.DatabaseField
import com.j256.ormlite.table.DatabaseTable
import com.adonai.manman.ManChaptersFragment

/**
 * Class representing static alphabet indexes on chapters
 * <br></br>
 * [android.widget.AlphabetIndexer] is very laggy on huge tables with
 * about 15000 items, so we use this class for retrieving indexes once and for all
 *
 * @see ManChaptersFragment
 *
 * @author Kanedias
 */
@DatabaseTable(tableName = "man_chapter_indexer")
class ManSectionIndex {

    constructor(letter: Char, index: Int, parentChapter: String) {
        this.letter = letter
        this.index = index
        this.parentChapter = parentChapter
    }

    @Suppress("unused")
    constructor()

    @DatabaseField(generatedId = true)
    var id = 0

    @DatabaseField(canBeNull = false, uniqueCombo = true)
    var letter = 0.toChar()

    @DatabaseField(canBeNull = false)
    var index = 0

    // this is filled with constant parent chapter from resources
    @DatabaseField(index = true, canBeNull = false, uniqueCombo = true)
    lateinit var parentChapter: String
}