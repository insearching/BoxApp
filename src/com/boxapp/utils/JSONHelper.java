package com.boxapp.utils;

import android.util.Log;

import com.boxapp.entity.FileInfo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * Created by insearching on 18.06.2014.
 */
public class JSONHelper {

    /**
     * Gets info about file or folder, without array
     * in JSON query.
     *
     * @param json   JSON data got from response
     * @param number number of file or folder in JSON
     * @return FileInfo object with info about file
     */
    public static FileInfo findObject(String json, int number) {
        try {
            JSONArray files = new JSONObject(json).getJSONArray(KeyMap.ENTRIES);
            JSONObject text = files.getJSONObject(number);
            String name = text.getString(KeyMap.NAME);
            String type = text.getString(KeyMap.TYPE);
            String id = text.getString(KeyMap.ID);
            String etag = text.getString(KeyMap.ETAG);
            return new FileInfo(name, type, id, etag);
        } catch (JSONException e) {
            Log.e("JSON", e.getMessage());
            return null;
        }
    }
}