package com.boxapp.entity;

import android.os.Parcel;
import android.os.Parcelable;

import com.boxapp.utils.KeyHelper;
import com.google.gson.annotations.SerializedName;

public class Item implements Parcelable {

    private String type;
    private long id;
    @SerializedName("sequence_id")
    private int sequenceId;
    private String name;
    private int etag;
    private DownloadStatus status;
    public enum Type {
        FILE, FOLDER
    }

    public Item(long id){
        this.id = id;
    }

    public String getName(){
        return name;
    }
    public Type getType(){
        return type.equals(KeyHelper.FILE) ? Type.FILE : Type.FOLDER;
    }
    public long getId(){
        return id;
    }
    public int getEtag(){
        return etag;
    }
    public int getSequenceId(){
        return sequenceId;
    }

    public DownloadStatus getStatus() {
        return status;
    }

    public void setDownloadStatus(DownloadStatus status){
        this.status = status;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(type.toString());
        dest.writeLong(id);
        dest.writeInt(sequenceId);
        dest.writeString(name);
        dest.writeInt(etag);
    }
}