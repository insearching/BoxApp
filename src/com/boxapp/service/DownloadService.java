package com.boxapp.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;
import android.widget.Toast;

import com.boxapp.DownloadListener;
import com.boxapp.R;
import com.boxapp.utils.BoxHelper;
import com.boxapp.utils.FileHelper;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.HttpClient;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;

public class DownloadService extends Service {
    private static final String TAG = "DOWANLOAD SERVICE";
    private Context mContext = this;
    private DownloadListener callback;
    private Integer mProgress = 0;
    private IBinder mBinder = new FileDownloadBinder();
    private int startId;

    public class FileDownloadBinder extends Binder {
        public DownloadService getService() {
            return DownloadService.this;
        }
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

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }


    public void downloadFile(String requestUrl, String accessToken, String fileIdent, String fileName, String position, String path){
        new DownloadFileTask(startId, path, fileName, position).execute(requestUrl, accessToken, fileIdent);
    }
    /**
     * Downloads file from service
     * param[0] - request URL
     * param[1] - accessToken
     * param[2] - id of file to download
     * param[3] - name of file to download
     * param[4] - position of file
     */
    class DownloadFileTask extends AsyncTask<String, Integer, Integer> {
        int startId;
        String path;
        String fileName;
        String position;

        public DownloadFileTask(int startId, String path, String fileName, String position) {
            this.startId = startId;
            this.path = path;
            this.fileName = fileName;
            this.position = position;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            mProgress = 0;
            if (callback != null)
                callback.onDownloadStarted(fileName);
        }

        @Override
        protected Integer doInBackground(String... param) {

            Integer count, result = null;
            try {
                HttpClient client = new DefaultHttpClient();
                HttpGet get = new HttpGet(param[0]);
                get.setHeader("Authorization", "Bearer " + param[1]);
                HttpResponse responseGet = client.execute(get);
                result = responseGet.getStatusLine().getStatusCode();
                if (result == HttpURLConnection.HTTP_OK) {
                    HttpEntity entity = responseGet.getEntity();
                    if (entity != null) {
                        InputStream is = entity.getContent();
                        long lengthOfFile = entity.getContentLength();

                        InputStream input = new BufferedInputStream(is, 8192);
                        File file = createFile(path, fileName);
                        OutputStream output = new FileOutputStream(file);
                        byte data[] = new byte[1024];
                        long total = 0;
                        while ((count = input.read(data)) != -1) {
                            total += count;
                            publishProgress((int) ((total * 100) / lengthOfFile), (int) lengthOfFile);
                            output.write(data, 0, count);
                        }
                        output.flush();
                        output.close();
                        input.close();
                        new FileHelper(mContext).saveFileData(param[2], fileName);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
            }
            return result;
        }

        @Override
        protected void onProgressUpdate(Integer... progress) {
            super.onProgressUpdate(progress);
            if ((progress[0] % 5) == 0 && mProgress != progress[0]) {
                mProgress = progress[0];
                BoxHelper.updateDownloadNotification(mContext, fileName, getString(R.string.downloading), mProgress, android.R.drawable.stat_sys_download, false);
                if (callback != null) {
                    callback.onProgressChanged(mProgress, fileName, getString(R.string.downloading));
                }
            }
        }

        @Override
        protected void onPostExecute(Integer result) {
            super.onPostExecute(result);

            if (callback != null)
                callback.onDownloadCompleted(Integer.parseInt(position), fileName, result);
            if (result != null && result == HttpURLConnection.HTTP_OK) {
                BoxHelper.showNotification(mContext, fileName, getString(R.string.download_completed), path, android.R.drawable.stat_sys_download_done);

            } else {
                BoxHelper.showNotification(mContext, fileName, getString(R.string.download_completed), path, android.R.drawable.stat_sys_download_done);
                Toast.makeText(mContext, getString(R.string.download_failed), Toast.LENGTH_LONG).show();
            }
            stopSelf(startId);
        }
    }

    private File createFile(String path, String fileName) {
        String sFolder = path + "/";
        File file = new File(sFolder);
        if (!file.exists())
            file.mkdirs();

        try {
            file = new File(sFolder + fileName);

            if (!file.createNewFile()) {
                file.delete();
                if (!file.createNewFile()) {
                    return null;
                }
            }
        } catch (Exception e) {
            return null;
        }
        return file;
    }

    public void attachListener(Context context) {
        callback = (DownloadListener) context;
    }
}
