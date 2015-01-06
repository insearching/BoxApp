package com.boxapp;

import android.app.Application;
import android.content.Context;

/**
 * Created by insearching on 06.01.2015.
 */
public class BoxApplication extends Application {
    private static Context mContext;

    @Override
    public void onCreate() {
        super.onCreate();
        mContext = this;
    }

    public static Context getContext(){
        return mContext;
    }
}
