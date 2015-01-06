package com.boxapp;

import com.boxapp.entity.LoginDetails;

import retrofit.client.Response;

/**
 * Created by insearching on 05.01.2015.
 */
public interface LoginListener {
    public void onAccessTokenReceived(LoginDetails loginDetails, Response response);
}
