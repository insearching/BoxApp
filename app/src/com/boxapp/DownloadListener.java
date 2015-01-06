package com.boxapp;

public interface DownloadListener {
	
	public void onDownloadStarted(String fileName);
	public void onProgressChanged(int progress, String fileName, String action);
	public void onDownloadCompleted(String fileId, String fileName);
    public void onDownloadFailed(String fileName);
	
}
