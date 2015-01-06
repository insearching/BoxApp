package com.boxapp.entity;

import android.os.Parcel;
import android.os.Parcelable;

import com.boxapp.utils.KeyHelper;
import com.google.gson.annotations.SerializedName;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by insearching on 29.12.2014.
 */

public class LoginDetails implements Parcelable {

    @SerializedName("access_token")
    private String accessToken;
    @SerializedName("expires_in")
    private int expiresIn;
    @SerializedName("refresh_token")
    private String refreshToken;

    public LoginDetails(String accessToken, String refreshToken, int expiresIn){
        this.accessToken = accessToken;
        this.refreshToken = refreshToken;
        this.expiresIn = expiresIn;
    }

    public LoginDetails(JSONObject jsonObject){
        try {
            accessToken = jsonObject.getString(KeyHelper.Login.ACCESS_TOKEN);
            expiresIn = jsonObject.getInt(KeyHelper.Login.EXPIRES_IN);
            refreshToken = jsonObject.getString(KeyHelper.Login.REFRESH_TOKEN);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public String getAccessToken() {
        return accessToken;
    }

    public int getExpiresIn() {
        return expiresIn;
    }

    public String getRefreshToken() {
        return refreshToken;
    }

    protected LoginDetails(Parcel in) {
        accessToken = in.readString();
        expiresIn = in.readInt();
        refreshToken = in.readString();
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(accessToken);
        dest.writeInt(expiresIn);
        dest.writeString(refreshToken);
    }
}
