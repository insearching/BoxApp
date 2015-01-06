package com.boxapp.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.util.Log;

import com.boxapp.DownloadListener;
import com.boxapp.R;
import com.boxapp.entity.LoginDetails;
import com.boxapp.utils.APIHelper;
import com.boxapp.utils.BoxHelper;
import com.boxapp.utils.Credentials;
import com.boxapp.utils.FileHelper;
import com.boxapp.utils.KeyHelper;

import org.apache.http.HttpStatus;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import retrofit.Callback;
import retrofit.RequestInterceptor;
import retrofit.RestAdapter;
import retrofit.RetrofitError;
import retrofit.client.Response;
import retrofit.http.GET;
import retrofit.http.Path;

public class DownloadService extends Service {
    private static final String TAG = "DOWANLOAD SERVICE";
    private Context mContext = this;
    private DownloadListener callback;
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


    public void downloadFile(final String fileId, final String fileName, final LoginDetails loginDetails, final APIHelper.LoginDataListener listener) {
        RequestInterceptor requestInterceptor = new RequestInterceptor() {
            @Override
            public void intercept(RequestFacade request) {
                request.addHeader("Authorization", "Bearer " + loginDetails.getAccessToken());
            }
        };
        RestAdapter restAdapter = new RestAdapter.Builder()
                .setLogLevel(RestAdapter.LogLevel.BASIC)
                .setEndpoint(Credentials.ROOT_URL)
                .setRequestInterceptor(requestInterceptor)
                .build();

        if (callback != null)
            callback.onDownloadStarted(fileName);

        final BoxService service = restAdapter.create(BoxService.class);
        service.downloadFile(fileId, new Callback<Response>() {
            @Override
            public void success(Response r, Response response) {
                try {
                    InputStream is = response.getBody().in();
                    long lengthOfFile = response.getBody().length();

                    InputStream input = new BufferedInputStream(is, 8192);
                    File file = FileHelper.createFile(KeyHelper.EXT_STORAGE_PATH, String.valueOf(fileId.hashCode()));
                    OutputStream output = new FileOutputStream(file);
                    byte data[] = new byte[1024];
                    long total = 0;
                    int count;
                    int lastProgress = 0;
                    while ((count = input.read(data)) != -1) {
                        total += count;
                        output.write(data, 0, count);
                        int progress = (int) ((total * 100) / lengthOfFile);
                        if (progress != lastProgress && (progress - lastProgress) >= 5) {
                            lastProgress = progress;
                            publishProgress(progress, fileName, (int) lengthOfFile);
                        }
                    }
                    if (callback != null)
                        callback.onDownloadCompleted(fileId, fileName);
                    Log.d("TAG", "Finish downloading");
                    output.flush();
                    output.close();
                    input.close();
                    FileHelper.saveFileData(mContext, String.valueOf(fileId.hashCode()));
                    stopSelf(startId);
                } catch (FileNotFoundException e) {
                    e.printStackTrace();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            @Override
            public void failure(RetrofitError error) {
                if (error != null) {
                    if (callback != null)
                        callback.onDownloadFailed(fileName);
                    Log.e(TAG, error.getMessage());
                    if (error.getResponse() != null) {
                        if (error.getResponse().getStatus() == HttpStatus.SC_UNAUTHORIZED) {
                            if (loginDetails == null && loginDetails.getRefreshToken() == null) {
                                BoxHelper.authorize(mContext);
                            } else {
                                listener.OnLoginDataRequired();
                            }
                        }
                    }
                }
                stopSelf(startId);
            }
        });
    }

    private void publishProgress(int progress, String fileName, int total) {
        Log.d("DOWNLOAD PROGRESS", "Downloaded " + progress + " of " + total);

        if (callback != null) {
            callback.onProgressChanged(progress, fileName, getString(R.string.downloading));
        }
    }

    public interface BoxService {
        @GET("/files/{file_id}/content")
        void downloadFile(@Path("file_id") String fileId, retrofit.Callback<Response> callback);
    }

//    public void downloadFile(String requestUrl, String accessToken, String fileId, String fileName, String position, String path) {
////        new DownloadFileTask(startId, path, fileName, position).execute(requestUrl, accessToken, fileId);
//    }
//    /**
//     * Downloads file from service
//     * param[0] - request URL
//     * param[1] - accessToken
//     * param[2] - id of file to download
//     * param[3] - name of file to download
//     * param[4] - position of file
//     */
//    class DownloadFileTask extends AsyncTask<String, Integer, Integer> {
//        int startId;
//        String path;
//        String fileName;
//        String position;
//
//        public DownloadFileTask(int startId, String path, String fileName, String position) {
//            this.startId = startId;
//            this.path = path;
//            this.fileName = fileName;
//            this.position = position;
//        }
//
//        @Override
//        protected void onPreExecute() {
//            super.onPreExecute();
//            mProgress = 0;
//            if (callback != null)
//                callback.onDownloadStarted(fileName);
//        }
//
//        @Override
//        protected Integer doInBackground(String... param) {
//
//            Integer count, result = null;
//            try {
//                HttpClient client = new DefaultHttpClient();
//                HttpGet get = new HttpGet(param[0]);
//                get.setHeader("Authorization", "Bearer " + param[1]);
//                HttpResponse responseGet = client.execute(get);
//                result = responseGet.getStatusLine().getStatusCode();
//                if (result == HttpURLConnection.HTTP_OK) {
//                    HttpEntity entity = responseGet.getEntity();
//                    if (entity != null) {
//                        InputStream is = entity.getContent();
//                        long lengthOfFile = entity.getContentLength();
//
//                        InputStream input = new BufferedInputStream(is, 8192);
//                        File file = createFile(path, fileName);
//                        OutputStream output = new FileOutputStream(file);
//                        byte data[] = new byte[1024];
//                        long total = 0;
//                        while ((count = input.read(data)) != -1) {
//                            total += count;
//                            publishProgress((int) ((total * 100) / lengthOfFile), (int) lengthOfFile);
//                            output.write(data, 0, count);
//                        }
//                        output.flush();
//                        output.close();
//                        input.close();
//                        new FileHelper(mContext).saveFileData(param[2], fileName);
//                    }
//                }
//            } catch (Exception e) {
//                Log.e(TAG, e.getMessage());
//            }
//            return result;
//        }
//
//        @Override
//        protected void onProgressUpdate(Integer... progress) {
//            super.onProgressUpdate(progress);
//            if ((progress[0] % 5) == 0 && mProgress != progress[0]) {
//                mProgress = progress[0];
//                BoxHelper.updateDownloadNotification(mContext, fileName, getString(R.string.downloading), mProgress, android.R.drawable.stat_sys_download, false);
//                if (callback != null) {
//                    callback.onProgressChanged(mProgress, fileName, getString(R.string.downloading));
//                }
//            }
//        }
//
//        @Override
//        protected void onPostExecute(Integer result) {
//            super.onPostExecute(result);
//
//            if (callback != null)
//                callback.onDownloadCompleted(Integer.parseInt(position), fileName, result);
//            if (result != null && result == HttpURLConnection.HTTP_OK) {
//                BoxHelper.showNotification(mContext, fileName, getString(R.string.download_completed), path, android.R.drawable.stat_sys_download_done);
//
//            } else {
//                BoxHelper.showNotification(mContext, fileName, getString(R.string.download_failed), path, android.R.drawable.stat_notify_error);
//                Toast.makeText(mContext, getString(R.string.download_failed) + " " + result, Toast.LENGTH_LONG).show();
//            }
//            stopSelf(startId);
//        }
//    }

//    private File createFile(String path, String fileName) {
//        String sFolder = path + "/";
//        File file = new File(sFolder);
//        if (!file.exists())
//            file.mkdirs();
//
//        try {
//            file = new File(sFolder + fileName);
//
//            if (!file.createNewFile()) {
//                file.delete();
//                if (!file.createNewFile()) {
//                    return null;
//                }
//            }
//        } catch (Exception e) {
//            return null;
//        }
//        return file;
//    }

    public void attachListener(DownloadListener callback) {
        this.callback = callback;
    }
}
