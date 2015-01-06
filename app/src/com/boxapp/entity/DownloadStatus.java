package com.boxapp.entity;

/**
 * Created by insearching on 29.12.2014.
 */
public class DownloadStatus {
    boolean isDownloaded;
    int progress;

    public DownloadStatus(boolean isDownloaded){
        this.isDownloaded = isDownloaded;
        progress = 0;
    }

    public DownloadStatus(boolean isDownloaded, int progress){
        this.isDownloaded = isDownloaded;
        this.progress = progress;
    }

    public int getProgress() {
        return progress;
    }

    public boolean isDownloaded() {
        return isDownloaded;
    }
}
