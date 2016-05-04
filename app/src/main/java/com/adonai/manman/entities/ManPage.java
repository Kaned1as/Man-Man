package com.adonai.manman.entities;

import com.j256.ormlite.field.DataType;
import com.j256.ormlite.field.DatabaseField;
import com.j256.ormlite.table.DatabaseTable;

import java.util.TreeSet;

/**
 * Holder for caching man-page contents to DB
 * Represents man page contents and provides relation to chapter page with description if possible
 *
 * The fields "url" and "name" are not foreign for {@link com.adonai.manman.entities.ManSectionItem}
 * as they can be retrieved from search page, not contents
 *
 * @see com.adonai.manman.ManPageDialogFragment
 * @see com.adonai.manman.ManPageCacheFragment
 * @author Oleg Chernovskiy
 */
@DatabaseTable(tableName = "man_pages")
@SuppressWarnings("UnusedDeclaration") // reflection in Gson
public class ManPage {

    public ManPage(String name, String url) {
        this.name = name;
        this.url = url;
    }

    // for OrmLite reflection restriction
    public ManPage() {
    }

    @DatabaseField(id = true)
    private String url;

    @DatabaseField(canBeNull = false)
    private String name;

    @DatabaseField(dataType = DataType.LONG_STRING)
    private String webContent;

    @DatabaseField(dataType = DataType.SERIALIZABLE)
    private TreeSet<String> links;


    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getWebContent() {
        return webContent;
    }

    public void setWebContent(String webContent) {
        this.webContent = webContent;
    }

    public TreeSet<String> getLinks() {
        return links;
    }

    public void setLinks(TreeSet<String> links) {
        this.links = links;
    }
}
