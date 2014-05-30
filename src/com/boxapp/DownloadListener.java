package com.boxapp;

public interface DownloadListener {
	
	public void onDownloadStarted(String name);
	public void onProgressChanged(Integer progress, String name);
	public void onDownloadCompleted(int position, String name);
	
}
