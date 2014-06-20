package com.boxapp;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.boxapp.utils.KeyMap;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.protocol.HTTP;
import org.apache.http.util.EntityUtils;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;


public class BoxLoginActivity extends ActionBarActivity {

    private static final String TAG = "BoxApp";
    private static final String CLIENT_ID = "8js646cfoogrnoi6zvnnb2yfl4f3uuok";
    private static final String CLIENT_SECRET = "KXMvD9FyBLCtsnHVtY27lBIdPyBLGjMK";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_box_login);

        WebView webView = (WebView) findViewById(R.id.webView);

        Bundle bundle = getIntent().getExtras();
        if (bundle != null) {
            if (bundle.containsKey(KeyMap.REQUEST_URL)) {
                String url = bundle.getString(KeyMap.REQUEST_URL);
                webView.loadUrl(url);
                webView.setWebViewClient(new WebViewClient() {
                    public boolean shouldOverrideUrlLoading(WebView view, String urlStr) {
                        try {
                            URL url = new URL(urlStr);

                            if (url.getProtocol().equals("https") && url.getHost().equals("www.box.com")
                                    && url.getPath().contains("services") && url.getQuery().contains("code")){
                                Uri uri = Uri.parse(urlStr);
                                String code = uri.getQueryParameter("code");
                                AuthTask post = new AuthTask();
                                if(code != null){
                                    post.execute(code);
                                }
                            }

                        } catch (MalformedURLException e) {
                            e.printStackTrace();
                        }
                        return true;
                    }
                });
            }
        }
    }

    class AuthTask extends AsyncTask<String, Integer, Credentials> {
        @Override
        protected Credentials doInBackground(String... params) {
            String responseStr = null;
            HttpClient client = new DefaultHttpClient();
            HttpPost post = new HttpPost(com.boxapp.utils.Credentials.AUTH_URL+"token");
            try {
                List<NameValuePair> values = new ArrayList<NameValuePair>();
                values.add(new BasicNameValuePair(KeyMap.CLIENT_ID, CLIENT_ID));
                values.add(new BasicNameValuePair(KeyMap.CLIENT_SECRET, CLIENT_SECRET));
                values.add(new BasicNameValuePair(KeyMap.GRANT_TYPE, "authorization_code"));
                values.add(new BasicNameValuePair(KeyMap.CODE, params[0]));

                // Add your data
                UrlEncodedFormEntity ent = new UrlEncodedFormEntity(values, HTTP.UTF_8);
                post.setEntity(ent);

                // Execute HTTP Post Request
                HttpResponse response = client.execute(post);
                HttpEntity resEntity = response.getEntity();
                if (resEntity != null) {
                    responseStr = EntityUtils.toString(resEntity);
                }
            } catch (ClientProtocolException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return new Credentials(getToken(responseStr, KeyMap.ACCESS_TOKEN), getToken(responseStr, KeyMap.REFRESH_TOKEN));
        }

        @Override
        protected void onPostExecute(Credentials result) {
            super.onPostExecute(result);
            recordPreferences(result);
            Intent intent = new Intent(BoxLoginActivity.this, MainActivity.class);
            startActivity(intent);
            finish();
        }
    }

    private String getToken(String json, String tokenType) {
        String token = null;
        JSONObject data;
        try {
            data = new JSONObject(json);
            token = data.getString(tokenType);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return token;
    }

    private void recordPreferences(Credentials cred) {
        SharedPreferences userDetails = getSharedPreferences(KeyMap.USER_DETAILS, MODE_PRIVATE);
        SharedPreferences.Editor edit = userDetails.edit();
        edit.clear();
        edit.putString(KeyMap.ACCESS_TOKEN, cred.getAccessToken());
        edit.putString(KeyMap.REFRESH_TOKEN, cred.getRefreshToken());
        edit.commit();
    }

    class Credentials {
        String accessToken;
        String refreshToken;

        Credentials(String accessToken, String refreshToken) {
            this.accessToken = accessToken;
            this.refreshToken = refreshToken;
        }

        public String getAccessToken() {
            return accessToken.trim();
        }

        public String getRefreshToken() {
            return refreshToken.trim();
        }


    }
}
