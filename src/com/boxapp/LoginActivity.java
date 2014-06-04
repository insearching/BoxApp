package com.boxapp;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

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
import java.util.ArrayList;
import java.util.List;

public class LoginActivity extends Activity {
	
	private static final String TAG = "AUTH ACTIVITY";
	private static final String CLIENT_ID = "8js646cfoogrnoi6zvnnb2yfl4f3uuok";
	private static final String CLIENT_SECRET = "KXMvD9FyBLCtsnHVtY27lBIdPyBLGjMK";
	private static final String SERVICE_REQUEST_URL = "https://api.box.com/oauth2/token";
	private static final String URL_DESRIPTION_PAGE = "https://www.box.com/services/boxsynchro";
	
	private static String code = null;
	private static String responseStr = null;
	private boolean postRequestPerforming = false;



	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.login_activity);
		Intent intent = getIntent();
		Uri uri = intent.getData();
		PostQuery post = new PostQuery();
		if (uri != null && uri.toString().startsWith(URL_DESRIPTION_PAGE + "?state")) {
			code = uri.getQueryParameter("code");
			if(code != null){
				getAccessToken();
				post.execute();
			}
		}
		else if (uri != null && uri.toString().startsWith(URL_DESRIPTION_PAGE + "?error")) {
			Toast.makeText(this, getString(R.string.error_occured), Toast.LENGTH_LONG).show();
		}
	}

	class PostQuery extends AsyncTask<Void, Integer, Credentials> {
		@Override
		protected Credentials doInBackground(Void... params) {
			while(responseStr == null || postRequestPerforming)
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}

            return new Credentials(getToken(responseStr, KeyMap.ACCESS_TOKEN), getToken(responseStr, KeyMap.REFRESH_TOKEN));
		}

		@Override
		protected void onPostExecute(Credentials result) {
			super.onPostExecute(result);
			recordPreferences(result);
			Toast.makeText(LoginActivity.this, getString(R.string.auth_success), Toast.LENGTH_LONG).show();
			responseStr = null;
			Intent intent = new Intent(LoginActivity.this, MainActivity.class);
			startActivity(intent);
			System.exit(0);
		}
	}
		
	private void getAccessToken(){
		List<NameValuePair> params = new ArrayList<NameValuePair>();
		params.add(new BasicNameValuePair(KeyMap.CLIENT_ID, CLIENT_ID));
		params.add(new BasicNameValuePair(KeyMap.CLIENT_SECRET, CLIENT_SECRET));
		params.add(new BasicNameValuePair(KeyMap.GRANT_TYPE, "authorization_code"));
		params.add(new BasicNameValuePair(KeyMap.CODE, code));
		postThreadData(params);
	}
	
	private void postThreadData(final List<NameValuePair> params){
		new Thread(new Runnable() {
			//Thread to stop network calls on the UI thread
			public void run() {
				// Create a new HttpClient and Post Header
				postRequestPerforming = true;
				HttpClient client = new DefaultHttpClient();
				HttpPost post = new HttpPost(SERVICE_REQUEST_URL);
				try {
					// Add your data
					UrlEncodedFormEntity ent = new UrlEncodedFormEntity(params, HTTP.UTF_8);
					post.setEntity(ent);
					
					// Execute HTTP Post Request
					HttpResponse response = client.execute(post); 
					HttpEntity resEntity = response.getEntity();  
					if (resEntity != null) {
						responseStr = EntityUtils.toString(resEntity);
						Log.d(TAG, responseStr);
						postRequestPerforming = false;
					}
				} catch (ClientProtocolException e) {
					e.printStackTrace();
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}).start();
		
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
		Editor edit = userDetails.edit();
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
