package com.boxapp.utils;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import com.boxapp.UploadListener;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;

/**
 * Created by insearching on 16.06.2014.
 */
public class UploadService extends Service {

    private static final String TAG = "UPLOAD SERVICE";
    private UploadListener uploadListener;
    private IBinder mBinder = new FileUploaddBinder();
    private Integer mProgress = 0;


    public class FileUploaddBinder extends Binder {
        public UploadService getService() {
            return UploadService.this;
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        if (android.os.Debug.isDebuggerConnected())
            android.os.Debug.waitForDebugger();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String requestUrl = intent.getStringExtra(KeyMap.REQUEST_URL);
        String accessToken = intent.getStringExtra(KeyMap.ACCESS_TOKEN);
        String folderId = intent.getStringExtra(KeyMap.FOLDER);
        String path = intent.getStringExtra(KeyMap.PATH);

        new UploadFileTask(startId).execute(requestUrl, accessToken, folderId, path);
        return super.onStartCommand(intent, flags, startId);
    }

    /**
     * Upload file on service using HTTP-post method
     *
     * @param[0] - request URL
     * @param[1] - access token
     * @param[2] - the ID of folder where this file should be uploaded
     * @param[3] - the path to file which has to be uploaded
     */
    class UploadFileTask extends AsyncTask<String, Void, FileUploadResponse> {

        private int startId;

        public UploadFileTask(int startId) {
            this.startId = startId;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
        }

        @Override
        protected FileUploadResponse doInBackground(String... param) {
            HttpResponse response = null;
            String path = param[3];
            FileUploadResponse uploadResponse = null;

            File file = new File(path);
            HttpClient client = new DefaultHttpClient();
            HttpPost post = new HttpPost(param[0]);
            try {
                post.setHeader("Authorization", "Bearer " + param[1]);
                MultipartEntity multiEntity = new MultipartEntity();
                multiEntity.addPart(KeyMap.PARENT_ID, new StringBody(param[2]));
                multiEntity.addPart(KeyMap.FILE, new FileBody(file));
                post.setEntity(multiEntity);

                response = client.execute(post);
                HttpEntity responseEntity = response.getEntity();

                int result = response.getStatusLine().getStatusCode();
                uploadResponse = new FileUploadResponse(result);
                if (responseEntity != null && result == 201) {
                    try {
                        uploadResponse.setInfo(findObject(EntityUtils.toString(responseEntity), 0));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }

            } catch (ClientProtocolException e) {
                Log.e(TAG, e.getMessage());
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
            }
            return uploadResponse;
        }

        @Override
        protected void onPostExecute(FileUploadResponse response) {
            super.onPostExecute(response);
            int result = response.getStatusCode();
            if (response.getInfo() != null) {
                FileInfo info = response.getInfo();
                String fileName = info.getName();
                uploadListener.onUploadCompleted(fileName);
            } else {
                uploadListener.onUploadFailed(result);
            }
            stopSelf(startId);
        }
    }


    class FileUploadResponse {

        private int statusCode;
        private FileInfo info;

        FileUploadResponse(int statusCode) {
            this.statusCode = statusCode;
            info = null;
        }

        public FileInfo getInfo() {
            return info;
        }

        public int getStatusCode() {
            return statusCode;
        }

        public void setInfo(FileInfo info) {
            this.info = info;
        }

    }

    /**
     * Gets info about file or folder, without array
     * in JSON query.
     *
     * @param json   JSON data got from response
     * @param number number of file or folder in JSON
     * @return FileInfo object with info about file
     */
    private static FileInfo findObject(String json, int number) {
        try {
            JSONArray files = new JSONObject(json).getJSONArray("entries");
            JSONObject text = files.getJSONObject(number);
            String name = text.getString(KeyMap.NAME);
            String type = text.getString(KeyMap.TYPE);
            String id = text.getString(KeyMap.ID);
            String etag = text.getString(KeyMap.ETAG);
            return new FileInfo(name, type, id, etag);
        } catch (JSONException e) {
            Log.e(TAG, e.getMessage());
            return null;
        }
    }

    public void attachListener(Context context) {
        uploadListener = (UploadListener) context;
    }
}
