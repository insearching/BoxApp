package com.boxapp.service;


import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import com.boxapp.R;
import com.boxapp.UploadListener;
import com.boxapp.entity.ResponseEntity;
import com.boxapp.utils.BoxHelper;
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
    private Context mContext = this;
    private UploadListener uploadListener;
    private IBinder mBinder = new FileUploadBinder();
    private Integer mProgress = 0;
    private int startId;


    public class FileUploadBinder extends Binder {
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
    class UploadFileTask extends AsyncTask<String, Integer, ResponseEntity> implements MultipartUtility.UploadStatusCallback {
        private int startId;
        private String path;
        private String fileName;

        public UploadFileTask(int startId, String path) {
            this.startId = startId;
            this.path = path;
            fileName = new File(path).getName();
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            if (uploadListener != null)
                uploadListener.onUploadStarted(fileName);
        }

        @Override
        protected ResponseEntity doInBackground(String... param) {
            ResponseEntity entity = null;
            try {
                MultipartUtility multipart = new MultipartUtility(this, param[0], param[1], HTTP.UTF_8);
                multipart.addFormField(KeyMap.PARENT_ID, param[2]);
                multipart.addFilePart(KeyMap.FILE, new File(path));

                entity = multipart.finish();

            } catch (IOException ex) {
                Log.d(TAG, ex.getMessage());
            }
            return entity;
        }

        @Override
        public void onProgressUpdate(Integer... progress) {
            super.onProgressUpdate(progress);
            if ((progress[0] % 5) == 0 && mProgress != progress[0]) {
                mProgress = progress[0];
                if(mProgress >= 75)
                    return;
                Log.d(TAG, ""+mProgress);
                BoxHelper.updateDownloadNotification(mContext, fileName, getString(R.string.uploading), mProgress, android.R.drawable.stat_sys_upload, true);
                if (uploadListener != null) {
                    uploadListener.onProgressChanged(mProgress, fileName, getString(R.string.uploading));
                }
            }
        }

        @Override
        protected void onPostExecute(ResponseEntity entity) {
            super.onPostExecute(entity);
            if(entity == null) {
                stopSelf(startId);
                return;
            }
            int result = entity.getStatusCode();
            if (entity.getInfo() != null) {
                if (uploadListener != null)
                    uploadListener.onUploadCompleted(fileName);

                BoxHelper.showNotification(mContext, fileName, getString(R.string.upload_completed), path, android.R.drawable.stat_sys_upload_done);
                FileHelper helper = new FileHelper(getApplicationContext());
                helper.copyFileOnDevice(path, KeyMap.EXT_STORAGE_PATH + "/" + fileName);
                helper.saveFileData(entity.getInfo().getId(), fileName);
            } else {
                if (uploadListener != null)
                    uploadListener.onUploadFailed(result);
                BoxHelper.showNotification(mContext, fileName, getString(R.string.upload_failed), path, android.R.drawable.stat_sys_upload_done);
            }
            stopSelf(startId);
        }
    }


    /*class UploadFileTask extends AsyncTask<String, Integer, String> implements MultipartUtility.UploadStatusCallback {
        private int startId;
        private String path;
        private String fileName;

        public UploadFileTask(int startId, String path) {
            this.startId = startId;
            this.path = path;
            fileName = new File(path).getName();
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            if (uploadListener != null)
                uploadListener.onUploadStarted(fileName);
        }

        @Override
        protected String doInBackground(String... param) {
            String response = null;
            try {
                response = postFile(param[0], param[1], path, param[2]);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return response;
        }

        @Override
        public void onProgressUpdate(Integer... progress) {
            super.onProgressUpdate(progress);
            if ((progress[0] % 5) == 0 && mProgress != progress[0]) {
                mProgress = progress[0];
                if(mProgress >= 75)
                    return;
                Log.d(TAG, "" + mProgress);
                BoxHelper.updateDownloadNotification(mContext, fileName, getString(R.string.uploading), mProgress, android.R.drawable.stat_sys_upload, true);
                if (uploadListener != null) {
                    uploadListener.onProgressChanged(mProgress, fileName, getString(R.string.uploading));
                }
            }
        }

        @Override
        protected void onPostExecute(String response) {
            super.onPostExecute(response);
            if(response == null) {
                stopSelf(startId);
                return;
            }
            Log.d(TAG, response);
            stopSelf(startId);
        }
    }


    public static String postFile(String requestUrl, String accessToken, String path, String parentId) throws Exception {
        HttpClient client = new DefaultHttpClient();
        HttpPost post = new HttpPost(requestUrl);
        post.setHeader("Authorization", "Bearer " + accessToken);

        MultipartEntityBuilder builder = MultipartEntityBuilder.create();
        builder.setMode(HttpMultipartMode.BROWSER_COMPATIBLE);

        builder.addTextBody(KeyMap.PARENT_ID, parentId);
        builder.addPart("file",  new FileBody(new File(path)));

        final HttpEntity yourEntity = builder.build();

        class ProgressiveEntity implements HttpEntity {
            @Override
            public void consumeContent() throws IOException {
                yourEntity.consumeContent();
            }
            @Override
            public InputStream getContent() throws IOException,
                    IllegalStateException {
                return yourEntity.getContent();
            }
            @Override
            public Header getContentEncoding() {
                return yourEntity.getContentEncoding();
            }
            @Override
            public long getContentLength() {
                return yourEntity.getContentLength();
            }
            @Override
            public Header getContentType() {
                return yourEntity.getContentType();
            }
            @Override
            public boolean isChunked() {
                return yourEntity.isChunked();
            }
            @Override
            public boolean isRepeatable() {
                return yourEntity.isRepeatable();
            }
            @Override
            public boolean isStreaming() {
                return yourEntity.isStreaming();
            } // CONSIDER put a _real_ delegator into here!

            @Override
            public void writeTo(OutputStream outstream) throws IOException {

                class ProxyOutputStream extends FilterOutputStream {

                    public ProxyOutputStream(OutputStream proxy) {
                        super(proxy);
                    }
                    public void write(int idx) throws IOException {
                        out.write(idx);
                    }
                    public void write(byte[] bts) throws IOException {
                        out.write(bts);
                    }
                    public void write(byte[] bts, int st, int end) throws IOException {
                        out.write(bts, st, end);
                    }
                    public void flush() throws IOException {
                        out.flush();
                    }
                    public void close() throws IOException {
                        out.close();
                    }
                } // CONSIDER import this class (and risk more Jar File Hell)

                class ProgressiveOutputStream extends ProxyOutputStream {
                    public ProgressiveOutputStream(OutputStream proxy) {
                        super(proxy);
                    }
                    int total = 0;
                    public void write(byte[] bts, int st, int end) throws IOException {
                        Log.d(TAG, bts.length + " " + st + " " + end);
                        out.write(bts, st, end);
                    }
                }

                yourEntity.writeTo(new ProgressiveOutputStream(outstream));
            }

        };
        ProgressiveEntity myEntity = new ProgressiveEntity();

        post.setEntity(myEntity);
        HttpResponse response = client.execute(post);
        return getContent(response);

    }

    public static String getContent(HttpResponse response) throws IOException {
        BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
        String body;
        String content = "";

        while ((body = rd.readLine()) != null)
        {
            content += body + "\n";
        }
        return content.trim();
    }*/

    public void attachListener(Context context) {
        uploadListener = (UploadListener) context;
    }

}
