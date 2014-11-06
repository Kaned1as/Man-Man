package com.adonai.manman.entities;

import android.support.annotation.NonNull;

import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

/**
 * Object represents an item in browse-chapter page of mankier.com
 *
 * It also serves as DB entity-mirroring class
 */
@DatabaseTable(tableName = "man_chapters")
public class ManSectionItem implements Comparable<ManSectionItem> {

    // these are filled by page
    @DatabaseField(id = true)
    private String url;

    @DatabaseField(canBeNull = false, index = true)
    private String name;

    @DatabaseField
    private String description;

    // this is filled with constant parent chapter from resources
    @DatabaseField(index = true, canBeNull = false)
    private String parentChapter;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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

    public String getParentChapter() {
        return parentChapter;
    }

    public void setParentChapter(String parentChapter) {
        this.parentChapter = parentChapter;
    }

    @Override
    public int compareTo(@NonNull ManSectionItem another) {
        return Character.compare(name.charAt(0), another.name.charAt(0));
    }
}
