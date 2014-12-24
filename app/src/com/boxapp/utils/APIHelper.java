package com.boxapp.utils;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.util.Log;
import android.widget.LinearLayout;
import android.widget.Toast;

import com.boxapp.BoxLoginActivity;
import com.boxapp.MainActivity;
import com.boxapp.R;
import com.handmark.pulltorefresh.library.PullToRefreshListView;

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
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.List;

public final class APIHelper {

    public static final String TAG = "AsyncLib";

    private Context mContext;
    private String mAccessToken = null;
    private String mRefreshToken = null;
    private String mCurDirId = "0";

    //private ArrayList<TextView> mFolderList;
    private PullToRefreshListView mFileListView;
    private LinearLayout mTopMenu;

    private FileHelper mFileHelper;
    private TaskListener mListner;

    public APIHelper(Context context, String accessToken, String refreshToken) {
        mContext = context;
        mListner = (TaskListener) context;

        mAccessToken = accessToken;
        mRefreshToken = refreshToken;

        mFileListView = (PullToRefreshListView) ((Activity) mContext).findViewById(R.id.fileListView);
        mTopMenu = (LinearLayout) ((Activity) mContext).findViewById(R.id.pathLayout);
        mFileHelper = new FileHelper(mContext);
    }

    public void getData(String requestUrl, String curDir) {
        mCurDirId = curDir;

        new GetData().execute(requestUrl + curDir);
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
        protected Integer doInBackground(String... params) {
            return null;
        }

        @Override
        protected void onPostExecute(Integer result) {
            super.onPostExecute(result);
            if (result == HttpURLConnection.HTTP_UNAUTHORIZED) {
                GetAccessTokenTask task = new GetAccessTokenTask();
                task.execute(Credentials.AUTH_URL + "token");
            } else if (result >= HttpURLConnection.HTTP_OK || result <= HttpURLConnection.HTTP_PARTIAL) {
                mFileListView.onRefreshComplete();
            }
        }
    }

    class AsyncGetData extends PerformAsyncRequest {
        @Override
        protected void onPostExecute(Integer result) {
            super.onPostExecute(result);
            if (result >= HttpURLConnection.HTTP_OK || result <= HttpURLConnection.HTTP_PARTIAL) {
                GetData task = new GetData();
                task.execute(Credentials.ROOT_URL + "folders/" + mCurDirId);
            }
        }
    }

    /**
     * Get new access token using refresh token
     * refresh token - 14 days
     *
     * @param[0] - service request URL
     */
    class GetAccessTokenTask extends AsyncTask<String, String, Integer> {
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
            if (result == HttpURLConnection.HTTP_BAD_REQUEST) {
                authorize();
            } else if (result == HttpURLConnection.HTTP_OK) {
                mAccessToken = getToken(responseStr, KeyMap.ACCESS_TOKEN);
                mRefreshToken = getToken(responseStr, KeyMap.REFRESH_TOKEN);
                mFileHelper.recordPreferences(mAccessToken, mRefreshToken);

                GetData task = new GetData();
                task.execute(Credentials.ROOT_URL + "folders/" + mCurDirId);
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
        intent.putExtra(KeyMap.REQUEST_URL, Credentials.AUTH_URL + "authorize?response_type=" + response_type + "&client_id=" + Credentials.CLIENT_ID);
        mContext.startActivity(intent);
        ((MainActivity) mContext).finish();
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
                if (resEntityGet != null && result == HttpURLConnection.HTTP_OK) {
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
            if (result == HttpURLConnection.HTTP_OK) {
                mListner.onDataReceived(responseStr);
            }
        }
    }

    /**
     * Deletes file or directory
     */
    class DeleteData extends AsyncGetData {
        @Override
        protected Integer doInBackground(String... param) {
            Integer result;
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
            delete.setHeader("Authorization", "Bearer " + mAccessToken);
            delete.setHeader("If-Match", etag);
            HttpResponse response = http.execute(delete);
            result = response.getStatusLine().getStatusCode();
            if (result == HttpURLConnection.HTTP_NO_CONTENT) {
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
        Integer result = null;
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
            if (result == HttpURLConnection.HTTP_BAD_REQUEST) {
                Toast.makeText(mContext, "Failed to rename file or folder.",
                        Toast.LENGTH_LONG).show();
            } else if (result == HttpURLConnection.HTTP_OK) {
                Toast.makeText(mContext, "Item successfully have been renamed.",
                        Toast.LENGTH_LONG).show();
                mFileHelper.renameFileOnDevice(ident, oldName, newName, KeyMap.EXT_STORAGE_PATH);
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
            if (result == HttpURLConnection.HTTP_CONFLICT) {
                Toast.makeText(mContext, "Item with the same name already exists.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    public interface TaskListener {
        public void onDataReceived(String json);
    }
}