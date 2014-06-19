package com.boxapp.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import com.boxapp.UploadListener;
import com.boxapp.entity.ResponseEntity;
import com.boxapp.utils.FileHelper;
import com.boxapp.utils.KeyMap;
import com.boxapp.utils.MultipartUtility;

import org.apache.http.protocol.HTTP;

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
    private int startId;


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
//        String requestUrl = intent.getStringExtra(KeyMap.REQUEST_URL);
//        String accessToken = intent.getStringExtra(KeyMap.ACCESS_TOKEN);
//        String folderId = intent.getStringExtra(KeyMap.FOLDER);
//        String path = intent.getStringExtra(KeyMap.PATH);
//
//        new UploadFileTask(startId, path).execute(requestUrl, accessToken, folderId);
        this.startId = startId;
        return START_NOT_STICKY;
    }

    public void uploadFile(String requestUrl, String accessToken, String folderId, String path) {
        new UploadFileTask(startId, path).execute(requestUrl, accessToken, folderId);
    }

    /**
     * Upload file on service using HTTP-post method
     *
     * @param[0] - request URL
     * @param[1] - access token
     * @param[2] - the ID of folder where this file should be uploaded
     * @param[3] - the path to file which has to be uploaded
     */
    class UploadFileTask extends AsyncTask<String, Void, ResponseEntity> {
        private int startId;
        private String path;
        private String name;

        public UploadFileTask(int startId, String path) {
            this.startId = startId;
            this.path = path;
            name = new File(path).getName();
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            if (uploadListener != null)
                uploadListener.onUploadStarted(name);
        }

        @Override
        protected ResponseEntity doInBackground(String... param) {
            ResponseEntity entity = null;
            try {
                MultipartUtility multipart = new MultipartUtility(param[0], param[1], HTTP.UTF_8);
                multipart.addFormField("parent_id", param[2]);
                multipart.addFilePart("file", new File(param[3]));

                entity = multipart.finish();

            } catch (IOException ex) {
                Log.d("TAG error", ex.getMessage());
            }
            return entity;
        }

        @Override
        protected void onPostExecute(ResponseEntity response) {
            super.onPostExecute(response);
            if(response == null) {
                stopSelf(startId);
                return;
            }
            int result = response.getStatusCode();
            if (response.getInfo() != null) {
                if (uploadListener != null)
                    uploadListener.onUploadCompleted(name);
                FileHelper helper = new FileHelper(getApplicationContext());
                helper.copyFileOnDevice(path, KeyMap.EXT_STORAGE_PATH + "/" + name);
                helper.saveFileData(response.getInfo().getId(), name);
            } else {
                if (uploadListener != null)
                    uploadListener.onUploadFailed(result);
            }
            stopSelf(startId);
        }
    }

    public void attachListener(Context context) {
        uploadListener = (UploadListener) context;
    }

}
