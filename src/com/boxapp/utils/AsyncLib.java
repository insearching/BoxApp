package com.boxapp.utils;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Environment;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.boxapp.MainActivity;
import com.boxapp.R;

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
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
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
    private DownloadService mService;
    private boolean isSameFolder = true;

    public AsyncLib(Context context, String accessToken, String refreshToken) {
        mContext = context;
        mAccessToken = accessToken;
        mRefreshToken = refreshToken;
        mFolderList = new ArrayList<TextView>();
        mFileListView = (ListView) ((Activity) mContext).findViewById(R.id.fileListView);
        mProgressLayout = (RelativeLayout) ((Activity) mContext).findViewById(R.id.loadFilesProgress);
        mTopMenu = (LinearLayout) ((Activity) mContext).findViewById(R.id.pathLayout);

        extStoragePath = Environment.getExternalStorageDirectory().getPath() + mContext.getString(R.string.app_name);
    }

    public void getData(String requestUrl, String curDir, ArrayList<TextView> folderList) {
        mCurDirId = curDir;
        mFolderList = folderList;
        GetData gd = new GetData();
        gd.execute(requestUrl + curDir);
    }

    public void downloadFile(String requestUrl, String ident, String fileName, String position) {
        ServiceConnection mConn = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder binder) {
                mService = ((DownloadService.FileDownloadBinder) binder).getService();
                mService.attachListener(mContext);
            }

            @Override
            public void onServiceDisconnected(ComponentName name) {
                mService = null;
            }
        };
        Intent intent = new Intent(mContext, DownloadService.class)
                .putExtra(KeyMap.REQUEST_URL, requestUrl)
                .putExtra(KeyMap.ACCESS_TOKEN, mAccessToken)
                .putExtra(KeyMap.FILE_IDENT, ident)
                .putExtra(KeyMap.FILE_NAME, fileName)
                .putExtra(KeyMap.POSITION, position);
        mContext.bindService(intent, mConn, Context.BIND_AUTO_CREATE);
        mContext.startService(intent);
    }

    public void uploadFile(String requestUrl, String folder_id, String path) {
        UploadData upload = new UploadData();
        upload.execute(requestUrl, folder_id, path);
    }

    public void renameItem(String requestUrl, String data,
                           String oldName, String newName, String ident) {
        PutData put = new PutData();
        put.execute(requestUrl, data, oldName, newName, ident);
    }

    public void createItem(String requestUrl, String data) {
        PostData post = new PostData();
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
                GNATAfterGetData gnat = new GNATAfterGetData();
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
    class GetAccessToken extends AsyncTask<String, String, Integer> {
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
                recordPreferences(mAccessToken, mRefreshToken);
            }
        }
    }

    class GNATAfterGetData extends GetAccessToken {
        @Override
        protected void onPostExecute(Integer result) {
            super.onPostExecute(result);
            if (result == 200) {
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
        String response_type = "code";
        Uri uri = Uri.parse(Credentials.AUTH_URL + "?response_type=" + response_type + "&client_id=" + Credentials.CLIENT_ID);
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        mContext.startActivity(intent);
        System.exit(0);
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

    private void recordPreferences(String access_token, String refresh_token) {
        SharedPreferences userPrefs = mContext.getSharedPreferences(KeyMap.USER_DETAILS, Context.MODE_PRIVATE);
        Editor edit = userPrefs.edit();
        edit.clear();
        edit.putString(KeyMap.ACCESS_TOKEN, access_token.trim());
        edit.putString(KeyMap.REFRESH_TOKEN, refresh_token.trim());
        edit.commit();
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
                MainActivity.jsonQuery = responseStr;
                getFolderItems(MainActivity.jsonQuery);
            }
        }
    }

    private void getFolderItems(String json) {
        JSONObject data;
        String name = null;
        String type = null;
        String id = null;
        ArrayList<FileInfo> fileList = new ArrayList<FileInfo>();
        try {
            data = new JSONObject(json).getJSONObject(KeyMap.ITEM_COLLECTION);
            JSONArray files = data.getJSONArray(KeyMap.ENTRIES);
            int fileCount = files.length();
            JSONObject text = null;
            for (int i = 0; i < fileCount; i++) {
                text = files.getJSONObject(i);
                name = text.getString(KeyMap.NAME);
                type = text.getString(KeyMap.TYPE);
                id = text.getString(KeyMap.ID);
                FileInfo fi = new FileInfo(name, type, id);
                fileList.add(fi);
                MainActivity.fileList = fileList;
            }
            displayFileStructure(fileList);
        } catch (JSONException e) {
            e.printStackTrace();
        }
    }

    public void getFileName(String json) {
        JSONObject data;
        String name = null;
        ArrayList<FileInfo> fileList = new ArrayList<FileInfo>();
        try {
            data = new JSONObject(json).getJSONObject(KeyMap.ITEM_COLLECTION);
            JSONArray files = data.getJSONArray(KeyMap.ENTRIES);
            int fileCount = files.length();
            JSONObject text = null;
            for (int i = 0; i < fileCount; i++) {
                name = text.getString(KeyMap.NAME);
            }
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

        isSameFolder = !isSameFolder;

        Map<String, Integer> formats = new HashMap<String, Integer>();
        formats.put(KeyMap.JPG, R.drawable.jpeg_icon);
        formats.put(KeyMap.JPEG, R.drawable.jpeg_icon);
        formats.put(KeyMap.DOC, R.drawable.docx_icon);
        formats.put(KeyMap.DOCX, R.drawable.docx_icon);
        formats.put(KeyMap.PNG, R.drawable.png_icon);
        formats.put(KeyMap.PDF, R.drawable.pdf_icon);
        formats.put(KeyMap.TXT, R.drawable.txt_icon);

        final int folderImg = R.drawable.folder;
        final int not_downloaded = R.drawable.non_downloaded;
        final int downloaded = R.drawable.file_downloaded;

        // packing data into structure for adapter
        ArrayList<Map<String, Object>> data = new ArrayList<Map<String, Object>>(fileList.size());
        Map<String, Object> itemMap;
        for (int i = 0; i < fileList.size(); i++) {
            itemMap = new HashMap<String, Object>();
            FileInfo fi = (FileInfo) fileList.get(i);
            String name = fi.getName();
            String type = fi.getType();
            String ident = fi.getId();
            itemMap.put(ATTRIBUTE_NAME_TITLE, name);

            if (type.equals(KeyMap.FOLDER)) {
                itemMap.put(ATTRIBUTE_NAME_IMAGE, folderImg);
            } else if (type.equals(KeyMap.FILE)) {
                String fileType = null;
                Integer format = null;
                if (name.contains(".")) {
                    fileType = name.toLowerCase().substring(name.lastIndexOf("."), name.length());
                    format = formats.get(fileType);
                }
                if (format == null)
                    format = R.drawable.default_icon;
                itemMap.put(ATTRIBUTE_NAME_IMAGE, format);
            }

            if (type.equals(KeyMap.FOLDER)) {
                itemMap.put(ATTRIBUTE_NAME_DOWNLOADED, null);
            } else if (type.equals(KeyMap.FILE)) {
                if (isFileOnDevice(name, ident, i)) {
                    itemMap.put(ATTRIBUTE_NAME_DOWNLOADED, downloaded);
                } else {
                    itemMap.put(ATTRIBUTE_NAME_DOWNLOADED, not_downloaded);
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
        // Add path buttons to navigate
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
            Integer result = 0;
            if (param.length == 2)
                result = deleteFile(param[0], param[1]);
            else
                result = deleteDir(param[0]);
            return result;
        }
    }

    private boolean isFileOnDevice(String name, String ident, final int num) {
        boolean result = false;
        File data = new File(extStoragePath + "/" + name);
        if (data.exists()) {
            SharedPreferences downloadPrefs = mContext.getSharedPreferences(KeyMap.DOWNLOADED_FILES, Context.MODE_MULTI_PROCESS);
            String _name = downloadPrefs.getString(ident, null);
            if (_name != null && _name.equals(name)) {
                result = true;
            }
        }
        return result;
    }

    /**
     * Gets info about file or folder, without array
     * in JSON query.
     *
     * @param json  JSON data got from response
     * @param number number of file or folder in JSON
     * @return FileInfo object with info about file
     */
    private static FileInfo findObject(String json, int number) {
        FileInfo fi = null;
        String name = null;
        String type = null;
        String id = null;
        String etag = null;
        try {
            JSONArray files = new JSONObject(json).getJSONArray("entries");
            JSONObject text = null;
            text = files.getJSONObject(number);
            name = text.getString(KeyMap.NAME);
            type = text.getString(KeyMap.TYPE);
            id = text.getString(KeyMap.ID);
            etag = text.getString(KeyMap.ETAG);
            fi = new FileInfo(name, type, id, etag);
        } catch (JSONException e) {
            Log.e(TAG, e.getMessage());
        }
        return fi;
    }

    private static void copyFileOnDevice(String srcPath, String destPath) {
        try {
            File srcFile = new File(srcPath);
            File destFile = new File(destPath);
            InputStream in = new FileInputStream(srcFile);
            OutputStream out = new FileOutputStream(destFile);
            byte[] buf = new byte[4096];
            int len;
            while ((len = in.read(buf)) > 0) {
                out.write(buf, 0, len);
            }
            in.close();
            out.close();
        } catch (FileNotFoundException ex) {
            String x = ex.getMessage();
            Log.d("File copy", x);
        } catch (IOException e) {
            Log.e(TAG, "IO error");
        }
    }

    /**
     * Saves file id and file name when file is downloaded
     * of uploaded (exists on device)
     *
     * @param ident of file
     * @param name  of file
     */
    private void saveFileData(String ident, String name) {
        SharedPreferences downloadPrefs = mContext.getSharedPreferences(KeyMap.DOWNLOADED_FILES, Context.MODE_MULTI_PROCESS);
        Editor edit = downloadPrefs.edit();
        edit.putString(ident, name);
        edit.commit();
    }

    /**
     * Deletes file info from shared preferences when
     * file deleting from device
     *
     * @param ident of file to delete
     */
    private void deleteFileData(String ident) {
        SharedPreferences downloadPrefs = mContext.getSharedPreferences(KeyMap.DOWNLOADED_FILES, Context.MODE_MULTI_PROCESS);
        Editor edit = downloadPrefs.edit();
        edit.remove(ident);
        edit.commit();
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
                deleteFileData(ident);
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
    public Integer deleteDir(String requestUrl) {
        Integer result = 0;
        HttpClient http = new DefaultHttpClient();
        HttpDelete delete = new HttpDelete(requestUrl);
        try {
            HttpResponse response = null;
            delete.setHeader("Authorization", "Bearer " + mAccessToken);
            response = http.execute(delete);
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
    class PutData extends AsyncGetData {
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
                HttpResponse response = null;
                put.setHeader("Authorization", "Bearer " + accessToken);
                response = client.execute(put);
                result = response.getStatusLine().getStatusCode();
                Log.i("RESULT RENAME", String.valueOf(result));
            } catch (Exception e) {
                Log.e("PUT DATA", e.getMessage());
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
                renameFileOnDevice(ident, oldName, newName);
            }
        }
    }

    private boolean renameFileOnDevice(String ident, String oldName, String newName) {
        SharedPreferences downloadPref = mContext.getSharedPreferences(KeyMap.DOWNLOADED_FILES, Context.MODE_MULTI_PROCESS);
        Editor edit = downloadPref.edit();
        Map<String, ?> idList = downloadPref.getAll();
        if (idList.get(ident) != null) {
            edit.putString(ident, newName);
            edit.commit();
        }

        File dir = new File(extStoragePath);
        if (dir.exists()) {
            File from = new File(dir, oldName);
            File to = new File(dir, newName);
            if (from.exists()) {
                from.renameTo(to);
                return true;
            }
        }
        return false;
    }

    /**
     * Creates new file or folder
     *
     * @param[0] - request URL
     * @param[1] - a raw data
     */
    class PostData extends AsyncGetData {
        @Override
        protected Integer doInBackground(String... param) {
            String requestUrl = param[0];
            String data = param[1];
            Integer result = 0;
            // Create a new HttpClient and Post Header
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

    /**
     * Upload file on service using HTTP-post method
     *
     * @param[0] - request URL
     * @param[1] - the ID of folder where this file should be uploaded
     * @param[2] - the path of the file to be uploaded
     */
    class UploadData extends AsyncGetData {
        @Override
        protected Integer doInBackground(String... param) {
            Integer result = 0;
            String json = null;
            String requestUrl = param[0];
            String folder_id = param[1];
            String path = param[2];
            File file = new File(path);
            HttpClient client = new DefaultHttpClient();
            HttpPost post = new HttpPost(requestUrl);
            try {
                post.setHeader("Authorization", "Bearer " + mAccessToken);
                MultipartEntity entity = new MultipartEntity();
                entity.addPart(KeyMap.FILE_ID, new StringBody(folder_id));
                entity.addPart(KeyMap.FILE_NAME, new FileBody(file));
                post.setEntity(entity);

                HttpResponse response = client.execute(post);
                result = response.getStatusLine().getStatusCode();
                if (result == 201) {
                    HttpEntity resEntityPost = response.getEntity();
                    if (resEntityPost != null) {
                        json = EntityUtils.toString(resEntityPost);
                        FileInfo fi = findObject(json, 0);
                        String fileName = fi.getName();
                        String ident = fi.getId();
                        saveFileData(ident, fileName);
                        if (!path.equals(extStoragePath + "/" + fileName)) {
                            copyFileOnDevice(path, extStoragePath + "/" + fileName);
                        }
                    }
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
            if (result == 400)
                Toast.makeText(mContext, "Failed to upload file.",
                        Toast.LENGTH_LONG).show();
        }
    }
}