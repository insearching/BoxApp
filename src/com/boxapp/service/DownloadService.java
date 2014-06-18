package com.boxapp.service;

import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Binder;
import android.os.IBinder;
import android.support.v4.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import com.boxapp.DownloadListener;
import com.boxapp.R;
import com.boxapp.utils.FileHelper;
import com.boxapp.utils.KeyMap;

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

public class DownloadService extends Service {
    //Application info on service
    private static final String TAG = "DOWANLOAD SERVICE";
    private String mPath;
    private Context mContext = this;
    private DownloadListener downloadListener;
    private Integer mProgress = 0;
    private IBinder mBinder = new FileDownloadBinder();

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
        String requestUrl = intent.getStringExtra(KeyMap.REQUEST_URL);
        String accessToken = intent.getStringExtra(KeyMap.ACCESS_TOKEN);
        String fileIdent = intent.getStringExtra(KeyMap.FILE_IDENT);
        String fileName = intent.getStringExtra(KeyMap.FILE_NAME);
        String position = intent.getStringExtra(KeyMap.POSITION);
        mPath = intent.getStringExtra(KeyMap.PATH);

        new DownloadFileTask(startId).execute(requestUrl, accessToken, fileIdent, fileName, position);
        return super.onStartCommand(intent, flags, startId);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
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

        String fileName = null;
        String position = null;

        public DownloadFileTask(int startId) {
            this.startId = startId;
        }

        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            mProgress = 0;
        }

        @Override
        protected Integer doInBackground(String... param) {
            fileName = param[3];
            position = param[4];
            if (downloadListener != null)
                downloadListener.onDownloadStarted(fileName);
            Integer count, result = null;
            try {
                HttpClient client = new DefaultHttpClient();
                HttpGet get = new HttpGet(param[0]);
                get.setHeader("Authorization", "Bearer " + param[1]);
                HttpResponse responseGet = client.execute(get);
                result = responseGet.getStatusLine().getStatusCode();
                if (result == 200) {
                    HttpEntity resEntityGet = responseGet.getEntity();
                    if (resEntityGet != null) {
                        InputStream is = resEntityGet.getContent();
                        long lengthOfFile = resEntityGet.getContentLength();

                        InputStream input = new BufferedInputStream(is, 8192);
                        File file = createFile(fileName);
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
                showDownloadNotification(fileName, getString(R.string.downloading), mProgress);
                if (downloadListener != null) {
                    downloadListener.onProgressChanged(mProgress, fileName);
                }
            }
        }

        @Override
        protected void onPostExecute(Integer result) {
            super.onPostExecute(result);
            if (result != null && result == 200) {
                showNotification(fileName, getString(R.string.downloaded));
                if (downloadListener != null)
                    downloadListener.onDownloadCompleted(Integer.parseInt(position), fileName);
            } else {
                Toast.makeText(mContext, getString(R.string.download_failed), Toast.LENGTH_LONG).show();
            }
            stopSelf(startId);
        }
    }

    private void showDownloadNotification(String title, String text, int progress) {
        NotificationManager nm = (NotificationManager)
                getApplicationContext()
                        .getSystemService(Context.NOTIFICATION_SERVICE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        builder.setAutoCancel(true)
                .setContentTitle(title)
                .setContentText(text + " " + progress + "%")
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher))
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setProgress(100, progress, false)
                .setTicker(getString(R.string.downloading) + " " + title);
        nm.notify(0, builder.build());
    }

    /**
     * Show a notification when file is downloaded
     */
    private void showNotification(String title, String text) {
        Intent notificationIntent = new Intent(android.content.Intent.ACTION_VIEW);
        File file = new File(mPath + "/" + title);
        String extension = android.webkit.MimeTypeMap.getFileExtensionFromUrl(Uri.fromFile(file).toString().toLowerCase());
        String mimetype = android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension);
        notificationIntent.setDataAndType(Uri.fromFile(file), mimetype);

        PendingIntent contentIntent = PendingIntent.getActivity(mContext,
                1, notificationIntent,
                PendingIntent.FLAG_CANCEL_CURRENT);

        NotificationManager nm = (NotificationManager)
                getApplicationContext()
                        .getSystemService(Context.NOTIFICATION_SERVICE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this);
        builder.setContentIntent(contentIntent)
                .setAutoCancel(true)
                .setContentTitle(title)
                .setContentText(text)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.ic_launcher))
                .setSmallIcon(R.drawable.file_downloaded);
        nm.notify(0, builder.build());
    }

    private File createFile(String fileName) {
        String sFolder = mPath + "/";
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
        downloadListener = (DownloadListener) context;
    }
}
