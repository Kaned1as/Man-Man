package com.adonai.manman.entities;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

/**
 * Class representing static alphabet indexes on chapters
 * <br/>
 * {@link android.widget.AlphabetIndexer} is very laggy on huge tables with
 * about 15000 items, so we use this class for retrieving indexes once and for all
 *
 * @see com.adonai.manman.ManChaptersFragment
 * @author Adonai
 */
@DatabaseTable(tableName = "man_chapter_indexer")
@SuppressWarnings("UnusedDeclaration") // reflection in Gson
public class ManSectionIndex {

    public ManSectionIndex(char letter, int index, String parentChapter) {
        this.letter = letter;
        this.index = index;
        this.parentChapter = parentChapter;
    }

    // for OrmLite reflection restriction
    public ManSectionIndex() {
    }

    @DatabaseField(generatedId = true)
    private int id; // actually it's useless...

    @DatabaseField(canBeNull = false, uniqueCombo = true)
    private char letter;

    @DatabaseField(canBeNull = false)
    private int index;

    // this is filled with constant parent chapter from resources
    @DatabaseField(index = true, canBeNull = false, uniqueCombo = true)
    private String parentChapter;

    public char getLetter() {
        return letter;
    }

    public void setLetter(char letter) {
        this.letter = letter;
    }

    public int getIndex() {
        return index;
    }

    public void setIndex(int index) {
        this.index = index;
    }

    public String getParentChapter() {
        return parentChapter;
    }

    public void setParentChapter(String parentChapter) {
        this.parentChapter = parentChapter;
    }
}
