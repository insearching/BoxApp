package com.boxapp;

public interface UploadListener {
	
	public void onUploadStarted(String name);
	public void onProgressChanged(Integer progress, String name);
	public void onUploadCompleted(String name);
	public void onUploadFailed(int code);

}
