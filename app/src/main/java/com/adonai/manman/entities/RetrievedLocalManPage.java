package com.adonai.manman.entities;

import android.support.annotation.NonNull;

import java.io.Serializable;

/**
 * Holds data about man page retrieved by file walker
 */
public final class RetrievedLocalManPage implements Comparable<RetrievedLocalManPage>, Serializable {
    
    private String commandName;
    private String parentEntry;
    private byte[] content;

    public String getCommandName() {
        return commandName;
    }

    public void setCommandName(String commandName) {
        this.commandName = commandName;
    }

    public String getParentEntry() {
        return parentEntry;
    }

    public void setParentEntry(String parentEntry) {
        this.parentEntry = parentEntry;
    }

    public byte[] getContent() {
        return content;
    }

    public void setContent(byte[] content) {
        this.content = content;
    }

    @Override
    public int compareTo(@NonNull RetrievedLocalManPage another) {
        return commandName.compareTo(another.commandName);
    }
}
