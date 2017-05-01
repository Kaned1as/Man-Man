package com.adonai.manman.entities;

import java.util.List;

/**
 * Object representing answer on description API-call
 *
 * @see com.google.gson.Gson
 * @author Oleg Chernovskiy
 */
@SuppressWarnings("UnusedDeclaration") // reflection in Gson
public class ManPageInfo {
    private String name;
    private String section;
    private String description;
    private String url;
    private List<InfoSection> sections;
    private List<InfoAnchor> anchors;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
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

    public List<InfoSection> getSections() {
        return sections;
    }

    public void setSections(List<InfoSection> sections) {
        this.sections = sections;
    }

    public List<InfoAnchor> getAnchors() {
        return anchors;
    }

    public void setAnchors(List<InfoAnchor> anchors) {
        this.anchors = anchors;
    }

    public static class InfoSection {
        private String id;
        private String title;
        private String url;

        // inner sub-sections
        private List<InfoSection> sections;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public List<InfoSection> getSections() {
            return sections;
        }

        public void setSections(List<InfoSection> sections) {
            this.sections = sections;
        }
    }

    public static class InfoAnchor {
        private String anchor;
        private String description;
        private String url;

        public String getAnchor() {
            return anchor;
        }

        public void setAnchor(String anchor) {
            this.anchor = anchor;
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
    }
}
