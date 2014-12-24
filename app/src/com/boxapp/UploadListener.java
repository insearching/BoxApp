package com.boxapp;

public interface UploadListener {
	
	public void onUploadStarted(String name);
	public void onProgressChanged(Integer progress, String nam, String action);
	public void onUploadCompleted(String name);
	public void onUploadFailed(int code);

}
