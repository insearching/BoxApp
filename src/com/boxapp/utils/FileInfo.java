package com.boxapp.utils;

public class FileInfo {
	String id = null;
	String name = null;
	String type = null;
	String etag = null;
	public FileInfo(String name, String type){
		this.name = name;
		this.type = type;
	}
	
	public FileInfo(String name, String type, String id){
		this.name = name;
		this.type = type;
		this.id = id;
	}

	public FileInfo(String name, String type, String id, String etag){
		this.name = name;
		this.type = type;
		this.id = id;
		this.etag = etag;
	}
	public String getName(){
		return name;
	}
	public String getType(){
		return type;
	}
	public String getId(){
		return id;
	}
	public String getEtag(){
		return etag;
	}
}
