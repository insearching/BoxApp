package com.boxapp.utils;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.AsyncTask;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.boxapp.BoxLoginActivity;
import com.boxapp.MainActivity;
import com.boxapp.R;
import com.boxapp.entity.FileInfo;
import com.boxapp.service.DownloadService;
import com.boxapp.service.UploadService;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class AsyncLib {

    public static final String TAG = "AsyncLib";
    private String extStoragePath;

    private Context mContext;
    private String mAccessToken = null;
    private String mRefreshToken = null;
    private String mCurDirId = "0";

    private ArrayList<TextView> mFolderList;
    private ListView mFileListView;
    private RelativeLayout mProgressLayout;
    private LinearLayout mTopMenu;

    private DownloadService mDownloadService;
    private UploadService mUploadService;
    private FileHelper mFileHelper;
    private TaskListener mListner;

    public AsyncLib(Context context, String accessToken, String refreshToken) {
        mContext = context;
        mListner = (TaskListener) context;

        mAccessToken = accessToken;
        mRefreshToken = refreshToken;
        mFolderList = new ArrayList<TextView>();
        mFileListView = (ListView) ((Activity) mContext).findViewById(R.id.fileListView);
        mProgressLayout = (RelativeLayout) ((Activity) mContext).findViewById(R.id.loadFilesProgress);
        mTopMenu = (LinearLayout) ((Activity) mContext).findViewById(R.id.pathLayout);
        extStoragePath = KeyMap.EXT_STORAGE_PATH;
        mFileHelper = new FileHelper(mContext);
    }

    public void getData(String requestUrl, String curDir, ArrayList<TextView> folderList) {
        mCurDirId = curDir;
        mFolderList = folderList;

        new GetData().execute(requestUrl + curDir);
    }

    public void downloadFile(String requestUrl, String ident, String fileName, String position) {
        ServiceConnection sConn = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder binder) {
                mDownloadService = ((DownloadService.FileDownloadBinder) binder).getService();
                mDownloadService.attachListener(mContext);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                mDownloadService = null;
            }
        };
        Intent intent = new Intent(mContext, DownloadService.class)
                .putExtra(KeyMap.REQUEST_URL, requestUrl)
                .putExtra(KeyMap.ACCESS_TOKEN, mAccessToken)
                .putExtra(KeyMap.FILE_IDENT, ident)
                .putExtra(KeyMap.FILE_NAME, fileName)
                .putExtra(KeyMap.POSITION, position)
                .putExtra(KeyMap.PATH, extStoragePath);

        mContext.bindService(intent, sConn, Context.BIND_AUTO_CREATE);
        mContext.startService(intent);
    }

    public void uploadFile(final String requestUrl, final String folderId, final String path) {
        ServiceConnection sConn = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder binder) {
                mUploadService = ((UploadService.FileUploaddBinder) binder).getService();
                mUploadService.attachListener(mContext);
                mUploadService.uploadFile(requestUrl, mAccessToken, folderId, path);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                mUploadService = null;
            }
        };

        Intent intent = new Intent(mContext, UploadService.class);

        mContext.bindService(intent, sConn, Context.BIND_AUTO_CREATE);
        mContext.startService(intent);

    }

    public void renameItem(String requestUrl, String data,
                           String oldName, String newName, String ident) {
        UpdateData put = new UpdateData();
        put.execute(requestUrl, data, oldName, newName, ident);
    }

    public void createItem(String requestUrl, String data) {
        CreateData post = new CreateData();
        post.execute(requestUrl, data);
    }

    public void deleteData(String requestUrl) {
        DeleteData delete = new DeleteData();
        delete.execute(requestUrl);
    }

    public void deleteData(String requestUrl, String etag) {
        DeleteData delete = new DeleteData();
        delete.execute(requestUrl, etag);
    }

    class PerformAsyncRequest extends AsyncTask<String, String, Integer> {
        @Override
        protected void onPreExecute() {
            super.onPreExecute();
            mFileListView.setVisibility(View.INVISIBLE);
            mProgressLayout.setVisibility(View.VISIBLE);
        }

        @Override
        protected Integer doInBackground(String... params) {
            return null;
        }

        @Override
        protected void onPostExecute(Integer result) {
            super.onPostExecute(result);
            if (result == 401) {
                GetAccsesTokenTask gnat = new GetAccsesTokenTask();
                gnat.execute(Credentials.SERVICE_REQUEST_URL);
            } else if (result >= 200 || result <= 210) {
                mProgressLayout.setVisibility(View.INVISIBLE);
                mFileListView.setVisibility(View.VISIBLE);

            }
        }
    }

    class AsyncGetData extends PerformAsyncRequest {
        @Override
        protected void onPostExecute(Integer result) {
            super.onPostExecute(result);
            if (result >= 200 || result <= 210) {
                GetData gd = new GetData();
                gd.execute(Credentials.ROOT_URL + "folders/" + mCurDirId);
            }
        }
    }

    /**
     * Get new access token using refresh token
     * refresh token - 14 days
     *
     * @param[0] - service request URL
     */
    class GetAccsesTokenTask extends AsyncTask<String, String, Integer> {
        String responseStr = null;

        @Override
        protected Integer doInBackground(String... param) {
            String requestUrl = param[0];
            Integer result = 0;
            List<NameValuePair> params = new ArrayList<NameValuePair>();
            params.add(new BasicNameValuePair(KeyMap.CLIENT_ID, Credentials.CLIENT_ID));
            params.add(new BasicNameValuePair(KeyMap.CLIENT_SECRET, Credentials.CLIENT_SECRET));
            params.add(new BasicNameValuePair(KeyMap.GRANT_TYPE, KeyMap.REFRESH_TOKEN));
            params.add(new BasicNameValuePair(KeyMap.REFRESH_TOKEN, mRefreshToken));

            HttpClient client = new DefaultHttpClient();
            HttpPost post = new HttpPost(requestUrl);
            try {
                UrlEncodedFormEntity entity = new UrlEncodedFormEntity(params, HTTP.UTF_8);
                post.setEntity(entity);
                HttpResponse response = client.execute(post);
                result = response.getStatusLine().getStatusCode();
                HttpEntity resEntity = response.getEntity();
                if (resEntity != null) {
                    responseStr = EntityUtils.toString(resEntity);
                }
            } catch (ClientProtocolException e) {
                Log.e(TAG, e.getMessage());
            } catch (IOException e) {
                Log.e(TAG, e.getMessage());
            }
            return result;
        }

        @Override
        protected void onPostExecute(Integer result) {
            super.onPostExecute(result);
            if (result == 400) {
                authorize();
            } else if (result == 200) {
                mAccessToken = getToken(responseStr, KeyMap.ACCESS_TOKEN);
                mRefreshToken = getToken(responseStr, KeyMap.REFRESH_TOKEN);
                mFileHelper.recordPreferences(mAccessToken, mRefreshToken);

                GetData gd = new GetData();
                gd.execute(Credentials.ROOT_URL + "folders/" + mCurDirId);
            }
        }
    }

    /**
     * Opens new web-view to authorize user. When user is successfully
     * authorized, access & refresh tokens will be recorded to the configuration
     * file.
     */
    public void authorize() {
        String response_type = KeyMap.CODE;
        Intent intent = new Intent(mContext, BoxLoginActivity.class);
        intent.putExtra(KeyMap.REQUEST_URL, Credentials.AUTH_URL + "?response_type=" + response_type + "&client_id=" + Credentials.CLIENT_ID);
        mContext.startActivity(intent);
        ((MainActivity)mContext).finish();
    }

    /**
     * Gets value of token from JSON
     *
     * @param json,      query from response
     * @param tokenType, name of token that has to be taken
     * @return value of token
     */
    private String getToken(String json, String tokenType) {
        String token = null;
        JSONObject data;
        try {
            data = new JSONObject(json);
            token = data.getString(tokenType);
        } catch (JSONException e) {
            Log.e(TAG, e.getMessage());
        }
        return token;
    }

    /**
     * Gets data asynchronously through HTTP-get request
     * Gets data from server using the access token and specified URL
     * param[0] request URL
     */
    class GetData extends PerformAsyncRequest {
        String responseStr = null;

        @Override
        protected Integer doInBackground(String... param) {
            Integer result = null;
            String requestUrl = param[0];
            try {
                HttpClient client = new DefaultHttpClient();
                HttpGet get = new HttpGet(requestUrl);
                get.setHeader("Authorization", "Bearer " + mAccessToken);
                HttpResponse responseGet = client.execute(get);
                result = responseGet.getStatusLine().getStatusCode();
                HttpEntity resEntityGet = responseGet.getEntity();
                if (resEntityGet != null && result == 200) {
                    responseStr = EntityUtils.toString(resEntityGet);
                }
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
            }
            return result;
        }

        @Override
        protected void onPostExecute(Integer result) {
            super.onPostExecute(result);
            if (result == 200) {
                mListner.onDataRecieved(responseStr);
                getFolderItems(responseStr);
            }
        }
    }

    private void getFolderItems(String json) {
        JSONObject data;
        ArrayList<FileInfo> fileList = new ArrayList<FileInfo>();
        try {
            data = new JSONObject(json).getJSONObject(KeyMap.ITEM_COLLECTION);
            JSONArray files = data.getJSONArray(KeyMap.ENTRIES);
            int fileCount = files.length();
            for (int i = 0; i < fileCount; i++) {
                JSONObject text = files.getJSONObject(i);
                String name = text.getString(KeyMap.NAME);
                String type = text.getString(KeyMap.TYPE);
                String id = text.getString(KeyMap.ID);
                FileInfo fi = new FileInfo(name, type, id);
                fileList.add(fi);
            }
            displayFileStructure(fileList);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    /**
     * Shows file structure, including file and folders
     * Also shows download status.
     *
     * @param fileList, ArrayList of files and folders info
     *                  which have to be represented
     */
    private void displayFileStructure(ArrayList<FileInfo> fileList) {
        final String ATTRIBUTE_NAME_TITLE = "title";
        final String ATTRIBUTE_NAME_DOWNLOADED = "status";
        final String ATTRIBUTE_NAME_IMAGE = "image";


        Map<String, Integer> drawableList = new HashMap<String, Integer>();
        drawableList.put(".jpg", R.drawable.jpg);
        drawableList.put(".jpeg", R.drawable.jpeg);
        drawableList.put(".png", R.drawable.png);
        drawableList.put(".gif", R.drawable.gif);

        drawableList.put(".doc", R.drawable.doc);
        drawableList.put(".docx", R.drawable.docx);
        drawableList.put(".ppt", R.drawable.ppt);
        drawableList.put(".pptx", R.drawable.pptx);

        drawableList.put(".pdf", R.drawable.pdf);
        drawableList.put(".txt", R.drawable.txt);
        drawableList.put(".exe", R.drawable.exe);
        drawableList.put(".mp3", R.drawable.mp3);
        drawableList.put(".mp4", R.drawable.mp4);
        drawableList.put(".psd", R.drawable.psd);
        drawableList.put(".rar", R.drawable.rar);
        drawableList.put(".zip", R.drawable.zip);

        ArrayList<Map<String, Object>> data = new ArrayList<Map<String, Object>>(fileList.size());
        Map<String, Object> itemMap;
        for (int i = 0; i < fileList.size(); i++) {
            itemMap = new HashMap<String, Object>();
            FileInfo info = fileList.get(i);
            String name = info.getName();
            String type = info.getType();
            itemMap.put(ATTRIBUTE_NAME_TITLE, name);

            if (type.equals(KeyMap.FOLDER)) {
                itemMap.put(ATTRIBUTE_NAME_IMAGE, R.drawable.folder);
            } else if (type.equals(KeyMap.FILE)) {
                Integer format = null;
                if (name.contains(".")) {
                    String fileType = name.toLowerCase().substring(name.lastIndexOf("."), name.length());
                    format = drawableList.get(fileType);
                }
                if (format == null)
                    format = R.drawable.blank;
                itemMap.put(ATTRIBUTE_NAME_IMAGE, format);
            }

            if (type.equals(KeyMap.FOLDER)) {
                itemMap.put(ATTRIBUTE_NAME_DOWNLOADED, null);
            } else if (type.equals(KeyMap.FILE)) {
                if (mFileHelper.isFileOnDevice(name, info.getId(), extStoragePath)) {
                    itemMap.put(ATTRIBUTE_NAME_DOWNLOADED, R.drawable.file_downloaded);
                } else {
                    itemMap.put(ATTRIBUTE_NAME_DOWNLOADED, R.drawable.non_downloaded);
                }
            }
            data.add(itemMap);
        }

        FileListAdapter adapter = new FileListAdapter(mContext, data);
        mFileListView.setAdapter(adapter);
        displayPathButtons();
    }

    /**
     * Shows directory buttons
     */
    private void displayPathButtons() {
        mTopMenu.removeAllViewsInLayout();
        for (int i = 0; i < mFolderList.size(); i++)
            mTopMenu.addView(mFolderList.get(i));
    }

    /**
     * Deletes file or directory
     */
    class DeleteData extends AsyncGetData {
        @Override
        protected Integer doInBackground(String... param) {
            Integer result = null;
            if (param.length == 2)
                result = deleteFile(param[0], param[1]);
            else
                result = deleteDirectory(param[0]);
            return result;
        }
    }

    /**
     * Deletes file from service
     *
     * @param requestUrl, URL to make a request
     * @param etag,       string value to prevent race conditions
     */
    public Integer deleteFile(String requestUrl, String etag) {
        Integer result = 0;
        HttpClient http = new DefaultHttpClient();
        HttpDelete delete = new HttpDelete(requestUrl);
        try {
            HttpResponse response = null;
            delete.setHeader("Authorization", "Bearer " + mAccessToken);
            delete.setHeader("If-Match", etag);
            response = http.execute(delete);
            result = response.getStatusLine().getStatusCode();
            if (result == 204) {
                String files = "files/";
                String ident = requestUrl.substring(requestUrl.indexOf(files) + files.length());
                mFileHelper.deleteFileData(ident);
            }
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
        return result;
    }

    /**
     * Deletes folder from service
     *
     * @param - requestUrl, URL to make a request
     */
    public Integer deleteDirectory(String requestUrl) {
        Integer result = 0;
        HttpClient client = new DefaultHttpClient();
        HttpDelete delete = new HttpDelete(requestUrl);
        try {
            delete.setHeader("Authorization", "Bearer " + mAccessToken);
            HttpResponse response = client.execute(delete);
            result = response.getStatusLine().getStatusCode();
        } catch (Exception e) {
            Log.e(TAG, e.getMessage());
        }
        return result;
    }

    /**
     * Send request on server to update data
     *
     * @param[0] - requestUrl, URL to update data
     * @param[1] - mAccessToken, token to authorize
     * @param[2] - data, data to change
     * @param[3] - oldName
     * @param[4] - newName
     * @param[5] - ident
     */
    class UpdateData extends AsyncGetData {
        String oldName = null;
        String newName = null;
        String ident = null;

        @Override
        protected Integer doInBackground(String... param) {
            Integer result = null;
            final String requestUrl = param[0];
            final String accessToken = param[1];
            final String data = param[2];
            oldName = param[3];
            newName = param[4];
            ident = param[5];
            HttpClient client = new DefaultHttpClient();
            HttpPut put = new HttpPut(requestUrl);
            try {
                put.setEntity(new StringEntity(data));
                put.setHeader("Authorization", "Bearer " + accessToken);
                HttpResponse response = client.execute(put);
                result = response.getStatusLine().getStatusCode();
                Log.i("RESULT RENAME", String.valueOf(result));
            } catch (Exception e) {
                Log.e(TAG, e.getMessage());
            }
            return result;
        }

        @Override
        protected void onPostExecute(Integer result) {
            super.onPostExecute(result);
            if (result == 400) {
                Toast.makeText(mContext, "Failed to rename file or folder.",
                        Toast.LENGTH_LONG).show();
            } else if (result == 200) {
                Toast.makeText(mContext, "Item successfully have been renamed.",
                        Toast.LENGTH_LONG).show();
                mFileHelper.renameFileOnDevice(ident, oldName, newName, extStoragePath);
            }
        }
    }

    /**
     * Creates new file or folder
     *
     * @param[0] - request URL
     * @param[1] - a raw data
     */
    class CreateData extends AsyncGetData {
        @Override
        protected Integer doInBackground(String... param) {
            String requestUrl = param[0];
            String data = param[1];
            Integer result = 0;
            HttpClient client = new DefaultHttpClient();
            HttpPost post = new HttpPost(requestUrl);
            try {
                post.setEntity(new StringEntity(data));
                post.setHeader("Authorization", "Bearer " + mAccessToken);
                HttpResponse response = client.execute(post);
                result = response.getStatusLine().getStatusCode();
            } catch (ClientProtocolException e) {
                Log.e(TAG, e.getMessage().toString());
            } catch (IOException e) {
                Log.e(TAG, e.getMessage().toString());
            }
            return result;
        }

        @Override
        protected void onPostExecute(Integer result) {
            super.onPostExecute(result);
            if (result == 409) {
                Toast.makeText(mContext, "Item with the same name already exists.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    public interface TaskListener {
        public void onDataRecieved(String json);
    }
}