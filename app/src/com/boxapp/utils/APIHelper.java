package com.boxapp.utils;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.boxapp.LoginActivity;
import com.boxapp.LoginListener;
import com.boxapp.MainActivity;
import com.boxapp.R;
import com.boxapp.entity.Item;
import com.boxapp.entity.LoginDetails;
import com.boxapp.service.DownloadService;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import org.apache.http.HttpStatus;

import java.io.IOException;
import java.util.List;

import retrofit.Callback;
import retrofit.RequestInterceptor;
import retrofit.RestAdapter;
import retrofit.RetrofitError;
import retrofit.client.Response;
import retrofit.converter.GsonConverter;
import retrofit.http.Body;
import retrofit.http.DELETE;
import retrofit.http.Field;
import retrofit.http.FormUrlEncoded;
import retrofit.http.GET;
import retrofit.http.POST;
import retrofit.http.Path;

public final class APIHelper {

    public static final String TAG = "APIHelper";

    private Context mContext;
    private LoginDetails loginDetails = null;

    private RestListener restListener;
    private LoginListener loginListener;
    private long currentFolder = 0;

    public APIHelper(Context context) {
        mContext = context;
        if (mContext.getClass().getSimpleName().equals(MainActivity.class.getSimpleName()))
            restListener = (RestListener) mContext;
        else if (mContext.getClass().getSimpleName().equals(LoginActivity.class.getSimpleName()))
            loginListener = (LoginListener) mContext;
    }

    public void setLoginDetails(LoginDetails details) {
        loginDetails = details;
    }

    public void authorize(String code) {
        RestAdapter restAdapter = new RestAdapter.Builder()
                .setLogLevel(RestAdapter.LogLevel.FULL)
                .setEndpoint(Credentials.AUTH_URL)
                .build();
        BoxService service = restAdapter.create(BoxService.class);
        service.getAccessToken(KeyHelper.AUTH_CODE,
                code,
                Credentials.CLIENT_ID,
                Credentials.CLIENT_SECRET,
                new Callback<LoginDetails>() {
                    @Override
                    public void success(LoginDetails details, Response response) {
                        loginListener.onAccessTokenReceived(details, response);
                    }

                    @Override
                    public void failure(RetrofitError error) {

                    }
                });
    }

    public void refreshAccessToken(final LoginListener listener) {
        RestAdapter restAdapter = new RestAdapter.Builder()
                .setLogLevel(RestAdapter.LogLevel.FULL)
                .setEndpoint(Credentials.AUTH_URL)
                .build();
        BoxService service = restAdapter.create(BoxService.class);
        service.refreshAccessToken(KeyHelper.REFRESH_TOKEN,
                loginDetails.getRefreshToken(),
                Credentials.CLIENT_ID,
                Credentials.CLIENT_SECRET,
                new Callback<LoginDetails>() {
                    @Override
                    public void success(LoginDetails details, Response response) {
                        loginDetails = details;
                        listener.onAccessTokenReceived(details, response);
                    }

                    @Override
                    public void failure(RetrofitError error) {
                        BoxHelper.authorize(mContext);
                    }
                });
    }

    public void getFolderItems(final long folderId) {
        Gson gson = new GsonBuilder()
                .registerTypeAdapterFactory(new ItemTypeAdapterFactory())
                .create();

        RequestInterceptor requestInterceptor = new RequestInterceptor() {
            @Override
            public void intercept(RequestFacade request) {
                request.addHeader("Authorization", "Bearer " + loginDetails.getAccessToken());
            }
        };
        RestAdapter restAdapter = new RestAdapter.Builder()
                .setLogLevel(RestAdapter.LogLevel.FULL)
                .setEndpoint(Credentials.ROOT_URL)
                .setConverter(new GsonConverter(gson))
                .setRequestInterceptor(requestInterceptor)
                .build();
        final BoxService service = restAdapter.create(BoxService.class);
        service.getFolderItems(String.valueOf(folderId), new Callback<List<Item>>() {
            @Override
            public void success(List<Item> items, Response response) {
                currentFolder = folderId;
                restListener.onFolderItemReceived(items, response);
            }

            @Override
            public void failure(RetrofitError error) {
                if (error != null) {
                    Log.e(TAG, error.getMessage());
                    if (error.getResponse() != null) {
                        if (error.getResponse().getStatus() == HttpStatus.SC_UNAUTHORIZED) {
                            if (loginDetails.getRefreshToken() == null) {
                                BoxHelper.authorize(mContext);
                            } else {
                                refreshAccessToken(new LoginListener() {
                                    @Override
                                    public void onAccessTokenReceived(LoginDetails loginDetails, Response response) {
                                        BoxHelper.saveUserDetails(mContext, loginDetails);
                                        service.getFolderItems(String.valueOf(folderId), new Callback<List<Item>>() {
                                            @Override
                                            public void success(List<Item> items, Response response) {
                                                restListener.onFolderItemReceived(items, response);
                                            }

                                            @Override
                                            public void failure(RetrofitError error) {
                                                Log.e(TAG, error.getMessage());
                                                BoxHelper.authorize(mContext);
                                            }
                                        });
                                    }
                                });
                            }
                        }
                    }
                }
            }
        });
    }

    public void createFolder(final String name, final String parentId) {
        RequestInterceptor requestInterceptor = new RequestInterceptor() {
            @Override
            public void intercept(RequestFacade request) {
                request.addHeader("Authorization", "Bearer " + loginDetails.getAccessToken());
            }
        };
        RestAdapter restAdapter = new RestAdapter.Builder()
                .setLogLevel(RestAdapter.LogLevel.FULL)
                .setEndpoint(Credentials.ROOT_URL)
                .setRequestInterceptor(requestInterceptor)
                .build();
        final BoxService service = restAdapter.create(BoxService.class);
        service.createFolder(new Folder(name, parentId), new Callback<Object>() {
                    @Override
                    public void success(Object o, Response response) {
                        getFolderItems(currentFolder);
                    }

                    @Override
                    public void failure(RetrofitError error) {
                        if (error == null)
                            return;
                        if (error.getResponse().getStatus() == HttpStatus.SC_CONFLICT) {
                            Toast.makeText(mContext, mContext.getString(R.string.folder_already_exists), Toast.LENGTH_LONG).show();
                        } else if (error.getResponse().getStatus() == HttpStatus.SC_UNAUTHORIZED) {
                            Log.e(TAG, error.getMessage());
                            if (error.getResponse() != null) {
                                if (error.getResponse().getStatus() == HttpStatus.SC_UNAUTHORIZED) {
                                    if (loginDetails == null && loginDetails.getRefreshToken() == null) {
                                        BoxHelper.authorize(mContext);
                                    } else {
                                        refreshAccessToken(new LoginListener() {
                                            @Override
                                            public void onAccessTokenReceived(LoginDetails loginDetails, Response response) {
                                                BoxHelper.saveUserDetails(mContext, loginDetails);
                                                service.createFolder(new Folder(name, parentId), new Callback<Object>() {
                                                    @Override
                                                    public void success(Object o, Response response) {
                                                        getFolderItems(currentFolder);
                                                    }

                                                    @Override
                                                    public void failure(RetrofitError error) {
                                                        Log.e(TAG, error.getMessage());
                                                        BoxHelper.authorize(mContext);
                                                    }
                                                });
                                            }
                                        });
                                    }
                                }
                            }

                        }
                    }
                }
        );
    }

    public void copyFile(final String fileId, final String parentId) {
        RequestInterceptor requestInterceptor = new RequestInterceptor() {
            @Override
            public void intercept(RequestFacade request) {
                request.addHeader("Authorization", "Bearer " + loginDetails.getAccessToken());
            }
        };
        RestAdapter restAdapter = new RestAdapter.Builder()
                .setLogLevel(RestAdapter.LogLevel.FULL)
                .setEndpoint(Credentials.ROOT_URL)
                .setRequestInterceptor(requestInterceptor)
                .build();
        final BoxService service = restAdapter.create(BoxService.class);
        service.copyFile(new Parent(parentId), fileId, new Callback<Object>() {
            @Override
            public void success(Object o, Response response) {
                getFolderItems(currentFolder);
                Toast.makeText(mContext, mContext.getString(R.string.file_successfully_copied), Toast.LENGTH_LONG).show();
            }

            @Override
            public void failure(RetrofitError error) {
                if (error == null)
                    return;
                if (error.getResponse().getStatus() == HttpStatus.SC_CONFLICT) {
                    Toast.makeText(mContext, mContext.getString(R.string.folder_already_exists), Toast.LENGTH_LONG).show();
                } else if (error.getResponse().getStatus() == HttpStatus.SC_UNAUTHORIZED) {
                    Log.e(TAG, error.getMessage());
                    if (error.getResponse() != null) {
                        if (error.getResponse().getStatus() == HttpStatus.SC_UNAUTHORIZED) {
                            if (loginDetails == null && loginDetails.getRefreshToken() == null) {
                                BoxHelper.authorize(mContext);
                            } else {
                                refreshAccessToken(new LoginListener() {
                                    @Override
                                    public void onAccessTokenReceived(LoginDetails loginDetails, Response response) {
                                        BoxHelper.saveUserDetails(mContext, loginDetails);
                                        service.copyFile(new Parent(parentId), fileId, new Callback<Object>() {
                                            @Override
                                            public void success(Object o, Response response) {
                                                getFolderItems(currentFolder);
                                            }

                                            @Override
                                            public void failure(RetrofitError error) {
                                                Log.e(TAG, error.getMessage());
                                                BoxHelper.authorize(mContext);
                                            }
                                        });
                                    }
                                });
                            }
                        }
                    }
                }
            }
        });
    }

    public void deleteItem(final String fileId, final String etag, final boolean isFolder) {
        RequestInterceptor requestInterceptor = new RequestInterceptor() {
            @Override
            public void intercept(RequestFacade request) {
                request.addHeader("Authorization", "Bearer " + loginDetails.getAccessToken());
                request.addHeader("If-Match", etag);
            }
        };
        RestAdapter restAdapter = new RestAdapter.Builder()
                .setLogLevel(RestAdapter.LogLevel.FULL)
                .setEndpoint(Credentials.ROOT_URL)
                .setRequestInterceptor(requestInterceptor)
                .build();
        final BoxService service = restAdapter.create(BoxService.class);
        service.deleteFile(fileId, isFolder ? "folders" : "files", new Callback<Object>() {
            @Override
            public void success(Object o, Response response) {
                getFolderItems(currentFolder);
                Toast.makeText(mContext, mContext.getString(R.string.file_sucessfully_deleted), Toast.LENGTH_LONG).show();
            }

            @Override
            public void failure(RetrofitError error) {
                if (error.getResponse() == null)
                    return;
                if (error.getResponse().getStatus() == HttpStatus.SC_CONFLICT) {
                    Toast.makeText(mContext, mContext.getString(R.string.folder_already_exists), Toast.LENGTH_LONG).show();
                } else if (error.getResponse().getStatus() == HttpStatus.SC_UNAUTHORIZED) {
                    Log.e(TAG, error.getMessage());
                    if (error.getResponse() != null) {
                        if (error.getResponse().getStatus() == HttpStatus.SC_UNAUTHORIZED) {
                            if (loginDetails == null && loginDetails.getRefreshToken() == null) {
                                BoxHelper.authorize(mContext);
                            } else {
                                refreshAccessToken(new LoginListener() {
                                    @Override
                                    public void onAccessTokenReceived(LoginDetails loginDetails, Response response) {
                                        BoxHelper.saveUserDetails(mContext, loginDetails);
                                        service.deleteFile(fileId, "files", new Callback<Object>() {
                                            @Override
                                            public void success(Object o, Response response) {
                                                getFolderItems(currentFolder);
                                            }

                                            @Override
                                            public void failure(RetrofitError error) {
                                                Log.e(TAG, error.getMessage());
                                                BoxHelper.authorize(mContext);
                                            }
                                        });
                                    }
                                });
                            }
                        }
                    }
                }
            }
        });
    }

    public void downloadFile(final DownloadService service, final String fileId, final String fileName) {
        service.downloadFile(fileId, fileName, BoxHelper.getUserDetails(mContext), new LoginDataListener() {
            @Override
            public void OnLoginDataRequired() {
                refreshAccessToken(new LoginListener() {
                    @Override
                    public void onAccessTokenReceived(LoginDetails loginDetails, Response response) {
                        service.downloadFile(fileId, fileName, BoxHelper.getUserDetails(mContext), new LoginDataListener() {
                            @Override
                            public void OnLoginDataRequired() {
                                BoxHelper.authorize(mContext);
                            }
                        });
                    }
                });
            }
        });
    }

    private void publishProgress(int progress, int total) {
        Log.d("DOWNLOAD PROGRESS", "Downloaded " + progress + " of " + total);
    }

//    public void renameItem(String requestUrl, String data,
//                           String oldName, String newName, String ident) {
//        UpdateData put = new UpdateData();
//        put.execute(requestUrl, data, oldName, newName, ident);
//    }
//
//    public void createItem(String requestUrl, String data) {
//        CreateData post = new CreateData();
//        post.execute(requestUrl, data);
//    }
//
//    public void deleteData(String requestUrl) {
//        DeleteData delete = new DeleteData();
//        delete.execute(requestUrl);
//    }
//
//    public void deleteData(String requestUrl, String etag) {
//        DeleteData delete = new DeleteData();
//        delete.execute(requestUrl, etag);
//    }
//    class PerformAsyncRequest extends AsyncTask<String, String, Integer> {
//        @Override
//        protected Integer doInBackground(String... params) {
//            return null;
//        }
//
//        @Override
//        protected void onPostExecute(Integer result) {
//            super.onPostExecute(result);
//            if (result == HttpURLConnection.HTTP_UNAUTHORIZED) {
//                GetAccessTokenTask task = new GetAccessTokenTask();
//                task.execute(Credentials.AUTH_URL + "token");
//            } else if (result >= HttpURLConnection.HTTP_OK || result <= HttpURLConnection.HTTP_PARTIAL) {
//                mFileListView.onRefreshComplete();
//            }
//        }
//    }
//    class AsyncGetData extends PerformAsyncRequest {
//        @Override
//        protected void onPostExecute(Integer result) {
//            super.onPostExecute(result);
////            if (result >= HttpURLConnection.HTTP_OK || result <= HttpURLConnection.HTTP_PARTIAL) {
////                GetData task = new GetData();
////                task.execute(Credentials.ROOT_URL + "folders/" + mCurDirId);
////            }
//        }
//    }
//    /**
//     * Get new access token using refresh token
//     * refresh token - 14 days
//     *
//     * @param[0] - service request URL
//     */
//    class GetAccessTokenTask extends AsyncTask<String, String, Integer> {
//        String responseStr = null;
//
//        @Override
//        protected Integer doInBackground(String... param) {
//            String requestUrl = param[0];
//            Integer result = 0;
//            List<NameValuePair> params = new ArrayList<NameValuePair>();
//            params.add(new BasicNameValuePair(KeyHelper.CLIENT_ID, Credentials.CLIENT_ID));
//            params.add(new BasicNameValuePair(KeyHelper.CLIENT_SECRET, Credentials.CLIENT_SECRET));
//            params.add(new BasicNameValuePair(KeyHelper.GRANT_TYPE, KeyHelper.REFRESH_TOKEN));
//            params.add(new BasicNameValuePair(KeyHelper.REFRESH_TOKEN, loginDetails.getRefreshToken()));
//
//            HttpClient client = new DefaultHttpClient();
//            HttpPost post = new HttpPost(requestUrl);
//            try {
//                UrlEncodedFormEntity entity = new UrlEncodedFormEntity(params, HTTP.UTF_8);
//                post.setEntity(entity);
//                HttpResponse response = client.execute(post);
//                result = response.getStatusLine().getStatusCode();
//                HttpEntity resEntity = response.getEntity();
//                if (resEntity != null) {
//                    responseStr = EntityUtils.toString(resEntity);
//                }
//            } catch (ClientProtocolException e) {
//                Log.e(TAG, e.getMessage());
//            } catch (IOException e) {
//                Log.e(TAG, e.getMessage());
//            }
//            return result;
//        }
//
//        @Override
//        protected void onPostExecute(Integer result) {
//            super.onPostExecute(result);
//            if (result == HttpURLConnection.HTTP_BAD_REQUEST) {
//                authorize();
//            } else if (result == HttpURLConnection.HTTP_OK) {
//                mAccessToken = getToken(responseStr, KeyHelper.ACCESS_TOKEN);
//                mRefreshToken = getToken(responseStr, KeyHelper.REFRESH_TOKEN);
//                mFileHelper.recordPreferences(mAccessToken, mRefreshToken);
//
//                GetData task = new GetData();
////                task.execute(Credentials.ROOT_URL + "folders/" + mCurDirId);
//            }
//        }
//    }

    /**
     * Opens new web-view to authorize user. When user is successfully
     * authorized, access & refresh tokens will be recorded to the configuration
     * file.
     */
//    public void authorize() {
//        SharedPreferences userDetails = mContext.getSharedPreferences(KeyHelper.USER_DETAILS, Context.MODE_PRIVATE);
//        SharedPreferences.Editor edit = userDetails.edit();
//        edit.clear();
//        edit.commit();
//
//        String responseType = KeyHelper.CODE;
//        Intent intent = new Intent(mContext, LoginActivity.class);
//        intent.putExtra(KeyHelper.REQUEST_URL, Credentials.AUTH_URL + "authorize?response_type=" + responseType + "&client_id=" + Credentials.CLIENT_ID);
//        mContext.startActivity(intent);
//        ((MainActivity) mContext).finish();
//    }

//    /**
//     * Gets value of token from JSON
//     *
//     * @param json,      query from response
//     * @param tokenType, name of token that has to be taken
//     * @return value of token
//     */
//    private String getToken(String json, String tokenType) {
//        String token = null;
//        JSONObject data;
//        try {
//            data = new JSONObject(json);
//            token = data.getString(tokenType);
//        } catch (JSONException e) {
//            Log.e(TAG, e.getMessage());
//        }
//        return token;
//    }
//
//    /**
//     * Gets data asynchronously through HTTP-get request
//     * Gets data from server using the access token and specified URL
//     * param[0] request URL
//     */
//    class GetData extends PerformAsyncRequest {
//        String responseStr = null;
//
//        @Override
//        protected Integer doInBackground(String... param) {
//            Integer result = null;
//            String requestUrl = param[0];
//            try {
//                HttpClient client = new DefaultHttpClient();
//                HttpGet get = new HttpGet(requestUrl);
//                get.setHeader("Authorization", "Bearer " + mAccessToken);
//                HttpResponse responseGet = client.execute(get);
//                result = responseGet.getStatusLine().getStatusCode();
//                HttpEntity resEntityGet = responseGet.getEntity();
//                if (resEntityGet != null && result == HttpURLConnection.HTTP_OK) {
//                    responseStr = EntityUtils.toString(resEntityGet);
//                }
//            } catch (Exception e) {
//                Log.e(TAG, e.getMessage());
//            }
//            return result;
//        }
//
//        @Override
//        protected void onPostExecute(Integer result) {
//            super.onPostExecute(result);
//            if (result == HttpURLConnection.HTTP_OK) {
//            }
//        }
//    }
//
//    /**
//     * Deletes file or directory
//     */
//    class DeleteData extends AsyncGetData {
//        @Override
//        protected Integer doInBackground(String... param) {
//            Integer result;
//            if (param.length == 2)
//                result = deleteItem(param[0], param[1]);
//            else
//                result = deleteDirectory(param[0]);
//            return result;
//        }
//    }
//
//    /**
//     * Deletes file from service
//     *
//     * @param requestUrl, URL to make a request
//     * @param etag,       string value to prevent race conditions
//     */
//    public Integer deleteItem(String requestUrl, String etag) {
//        Integer result = 0;
//        HttpClient http = new DefaultHttpClient();
//        HttpDelete delete = new HttpDelete(requestUrl);
//        try {
//            delete.setHeader("Authorization", "Bearer " + mAccessToken);
//            delete.setHeader("If-Match", etag);
//            HttpResponse response = http.execute(delete);
//            result = response.getStatusLine().getStatusCode();
//            if (result == HttpURLConnection.HTTP_NO_CONTENT) {
//                String files = "files/";
//                String ident = requestUrl.substring(requestUrl.indexOf(files) + files.length());
//                mFileHelper.deleteFileData(ident);
//            }
//        } catch (Exception e) {
//            Log.e(TAG, e.getMessage());
//        }
//        return result;
//    }
//
//    /**
//     * Deletes folder from service
//     *
//     * @param - requestUrl, URL to make a request
//     */
//    public Integer deleteDirectory(String requestUrl) {
//        Integer result = null;
//        HttpClient client = new DefaultHttpClient();
//        HttpDelete delete = new HttpDelete(requestUrl);
//        try {
//            delete.setHeader("Authorization", "Bearer " + mAccessToken);
//            HttpResponse response = client.execute(delete);
//            result = response.getStatusLine().getStatusCode();
//        } catch (Exception e) {
//            Log.e(TAG, e.getMessage());
//        }
//        return result;
//    }
//
//    /**
//     * Send request on server to update data
//     *
//     * @param[0] - requestUrl, URL to update data
//     * @param[1] - mAccessToken, token to authorize
//     * @param[2] - data, data to change
//     * @param[3] - oldName
//     * @param[4] - newName
//     * @param[5] - ident
//     */
//    class UpdateData extends AsyncGetData {
//        String oldName = null;
//        String newName = null;
//        String ident = null;
//
//        @Override
//        protected Integer doInBackground(String... param) {
//            Integer result = null;
//            final String requestUrl = param[0];
//            final String accessToken = param[1];
//            final String data = param[2];
//            oldName = param[3];
//            newName = param[4];
//            ident = param[5];
//            HttpClient client = new DefaultHttpClient();
//            HttpPut put = new HttpPut(requestUrl);
//            try {
//                put.setEntity(new StringEntity(data));
//                put.setHeader("Authorization", "Bearer " + accessToken);
//                HttpResponse response = client.execute(put);
//                result = response.getStatusLine().getStatusCode();
//                Log.i("RESULT RENAME", String.valueOf(result));
//            } catch (Exception e) {
//                Log.e(TAG, e.getMessage());
//            }
//            return result;
//        }
//
//        @Override
//        protected void onPostExecute(Integer result) {
//            super.onPostExecute(result);
//            if (result == HttpURLConnection.HTTP_BAD_REQUEST) {
//                Toast.makeText(mContext, "Failed to rename file or folder.",
//                        Toast.LENGTH_LONG).show();
//            } else if (result == HttpURLConnection.HTTP_OK) {
//                Toast.makeText(mContext, "Item successfully have been renamed.",
//                        Toast.LENGTH_LONG).show();
//                mFileHelper.renameFileOnDevice(ident, oldName, newName, KeyHelper.EXT_STORAGE_PATH);
//            }
//        }
//    }
//
//    /**
//     * Creates new file or folder
//     *
//     * @param[0] - request URL
//     * @param[1] - a raw data
//     */
//    class CreateData extends AsyncGetData {
//        @Override
//        protected Integer doInBackground(String... param) {
//            String requestUrl = param[0];
//            String data = param[1];
//            Integer result = 0;
//            HttpClient client = new DefaultHttpClient();
//            HttpPost post = new HttpPost(requestUrl);
//            try {
//                post.setEntity(new StringEntity(data));
//                post.setHeader("Authorization", "Bearer " + mAccessToken);
//                HttpResponse response = client.execute(post);
//                result = response.getStatusLine().getStatusCode();
//            } catch (ClientProtocolException e) {
//                Log.e(TAG, e.getMessage().toString());
//            } catch (IOException e) {
//                Log.e(TAG, e.getMessage().toString());
//            }
//            return result;
//        }
//
//        @Override
//        protected void onPostExecute(Integer result) {
//            super.onPostExecute(result);
//            if (result == HttpURLConnection.HTTP_CONFLICT) {
//                Toast.makeText(mContext, "Item with the same name already exists.",
//                        Toast.LENGTH_LONG).show();
//            }
//        }
//    }

    public interface BoxService {
        @FormUrlEncoded
        @POST("/token")
        void getAccessToken(@Field(KeyHelper.GRANT_TYPE) String authCode,
                            @Field(KeyHelper.CODE) String code,
                            @Field(KeyHelper.CLIENT_ID) String clientId,
                            @Field(KeyHelper.CLIENT_SECRET) String clientSecret,
                            retrofit.Callback<LoginDetails> callback);

        @FormUrlEncoded
        @POST("/token")
        void refreshAccessToken(@Field(KeyHelper.GRANT_TYPE) String authCode,
                                @Field(KeyHelper.REFRESH_TOKEN) String refreshToken,
                                @Field(KeyHelper.CLIENT_ID) String clientId,
                                @Field(KeyHelper.CLIENT_SECRET) String clientSecret,
                                retrofit.Callback<LoginDetails> callback);

        @GET("/folders/{id}/items")
        void getFolderItems(@Path("id") String id, retrofit.Callback<List<Item>> callback);

        @POST("/folders")
        void createFolder(@Body Folder folder, retrofit.Callback<Object> callback);

        @POST("/files/{id}/copy")
        void copyFile(@Body Parent parent, @Path("id") String id, retrofit.Callback<Object> callback);

        @DELETE("/{item_type}/{file_id}")
        void deleteFile(@Path("file_id") String fileId, @Path("item_type") String itemType, retrofit.Callback<Object> callback);

        @GET("/files/{file_id}/content")
        void downloadFile(@Path("file_id") String fileId, retrofit.Callback<Response> callback);
    }

    public class ItemTypeAdapterFactory implements TypeAdapterFactory {

        public <T> TypeAdapter<T> create(Gson gson, final TypeToken<T> type) {

            final TypeAdapter<T> delegate = gson.getDelegateAdapter(this, type);
            final TypeAdapter<JsonElement> elementAdapter = gson.getAdapter(JsonElement.class);

            return new TypeAdapter<T>() {

                public void write(JsonWriter out, T value) throws IOException {
                    delegate.write(out, value);
                }

                public T read(JsonReader in) throws IOException {
                    JsonElement jsonElement = elementAdapter.read(in);
                    if (jsonElement.isJsonObject()) {
                        JsonObject jsonObject = jsonElement.getAsJsonObject();
                        if (jsonObject.has("entries") && jsonObject.get("entries").isJsonArray()) {
                            jsonElement = jsonObject.get("entries");
                        }
                    }

                    return delegate.fromJsonTree(jsonElement);
                }
            }.nullSafe();
        }
    }

    class Folder {
        final String name;
        final Parent parent;

        Folder(String name, String parentId) {
            this.name = name;
            this.parent = new Parent(parentId);
        }
    }

    class Parent {
        final String id;

        Parent(String id) {
            this.id = id;
        }
    }

    public interface RestListener {
        public void onFolderItemReceived(List<Item> items, Response response);
    }

    public interface LoginDataListener {
        public void OnLoginDataRequired();
    }
}