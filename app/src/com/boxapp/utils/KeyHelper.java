package com.boxapp.utils;

import android.os.Environment;

import com.boxapp.BoxApplication;
import com.boxapp.R;

/**
 * Created by insearching on 30.05.2014.
 */
public class KeyHelper {
    public static final String REQUEST_URL = "request_url";
    public static final String ACCESS_TOKEN = "access_token";
    public static final String REFRESH_TOKEN = "refresh_token";
    public static final String AUTH_CODE = "authorization_code";
    public static final String TOKEN = "token";
//    public static final String FILE_IDENT = "file_ident";
//    public static final String FILE_NAME = "file_name";
//    public static final String POSITION = "position";

    public static final String CLIENT_ID = "client_id";
    public static final String CLIENT_SECRET = "client_secret";
    public static final String GRANT_TYPE = "grant_type";
    public static final String CODE = "code";

    //Cache files
    public static final String USER_DETAILS = "userdetails";
    public static final String DOWNLOADED_FILES = "downloaded_files";

    //JSON
    public static final String ITEM_COLLECTION = "item_collection";
    public static final String ENTRIES = "entries";
    public static final String NAME = "name";
    public static final String TYPE = "type";
    public static final String ID = "id";
    public static final String FOLDER = "folder";
    public static final String FILE = "file";
    public static final String FILE_ID = "file_id";
    public static final String ETAG = "etag";
    public static final String PATH = "path";
    public static final String STATUS = "status";
    public static final String PROGRESS = "progress";
    public static final String PARENT_ID = "parent_id";

    public static final String EXT_STORAGE_PATH = Environment.getExternalStorageDirectory().getPath() + "/" + BoxApplication.getContext().getString(R.string.app_name);

    public static class Login {
        public static final String ACCESS_TOKEN = "access_token";
        public static final String EXPIRES_IN = "expires_in";
        public static final String REFRESH_TOKEN = "refresh_token";
    }
}
