package com.boxapp.entity;

/**
 * Created by insearching on 25.06.2014.
 */
public class Folder {
    private String id;

    private String label;
    private String parentId;

    public Folder(String id, String label) {
        this.id = id;
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    public String getId() {
        return id;
    }
}
