package com.boxapp.entity;

/**
 * Created by insearching on 19.06.2014.
 */
public class ResponseEntity {

    private int statusCode;
    private FileInfo info;

    public ResponseEntity(int statusCode) {
        this.statusCode = statusCode;
        info = null;
    }

    public FileInfo getInfo() {
        return info;
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setInfo(FileInfo info) {
        this.info = info;
    }
}
