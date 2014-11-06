package com.adonai.manman.misc;

import com.adonai.manman.ManChaptersFragment;
import com.adonai.manman.entities.ManSectionItem;

import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.util.List;

/**
 * SAX parser for chapter contents. The reason for creation of this class is
 * the chapter pages being rather huge (around 6-7 Mbytes). Their parsing via Jsoup
 * produces OutOfMemoryError for android phone heap sizes
 * <br/>
 * A nice benefit from that is that CountingInputStream counts page as it's being parsed
 *
 * @see org.ccil.cowan.tagsoup.Parser
 *
 */
public class ManSectionExtractor extends DefaultHandler {
    private final String index;
    private final List<ManSectionItem> msItems;

    private StringBuilder holder;
    private boolean flagCommand;
    private boolean flagLink;
    private boolean flagDesc;

    public ManSectionExtractor(String index, List<ManSectionItem> msItems) {
        this.index = index;
        this.msItems = msItems;
        holder = new StringBuilder(50);
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
        if (flagCommand) {
            if ("a".equals(qName) && !flagDesc) { // first child of div.e -> href, link to a command page
                msItems.get(msItems.size() - 1).setUrl(ManChaptersFragment.CHAPTER_COMMANDS_PREFIX + attributes.getValue("href"));
                flagLink = true;
            } else if ("span".equals(qName)) {
                flagDesc = true;
            }
        } else {
            if ("div".equals(qName) && "e".equals(attributes.getValue("class"))) {
                ManSectionItem msi = new ManSectionItem();
                msi.setParentChapter(index);
                msItems.add(msi);
                flagCommand = true;
            }
        }
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
        if(flagLink || flagDesc) {
            holder.append(ch, start, length);
        }
    }

    @Override
    public void endElement(String uri, String localName, String qName) throws SAXException {
        if(flagCommand) {
            switch (qName) {
                case "div":
                    flagCommand = false;
                    break;
                case "a":
                    if (flagLink && !flagDesc) { // first child of div.e, name of a command
                        ManSectionItem msi = msItems.get(msItems.size() - 1);
                        msi.setName(holder.toString());
                        holder.setLength(0);
                    }
                    flagLink = false;
                    break;
                case "span":
                    if (flagDesc) {  // third child of div.e, description of a command
                        msItems.get(msItems.size() - 1).setDescription(holder.toString());
                        holder.setLength(0);
                    }
                    flagDesc = false;
                    break;
            }
        }
    }
}
