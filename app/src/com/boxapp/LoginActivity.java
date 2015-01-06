package com.boxapp;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.boxapp.entity.LoginDetails;
import com.boxapp.utils.APIHelper;
import com.boxapp.utils.BoxHelper;
import com.boxapp.utils.Credentials;
import com.boxapp.utils.KeyHelper;

import retrofit.client.Response;


public class LoginActivity extends ActionBarActivity implements LoginListener {

    private String TAG = "TAG";
    private APIHelper helper;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_box_login);
        helper = new APIHelper(this);
        initializeWebView();
    }

    private void initializeWebView(){
        WebView webView = (WebView) findViewById(R.id.webView);
        Bundle bundle = getIntent().getExtras();
        if (bundle != null) {
            if (bundle.containsKey(KeyHelper.REQUEST_URL)) {
                String url = bundle.getString(KeyHelper.REQUEST_URL);
                webView.loadUrl(url);
                webView.setWebViewClient(new WebViewClient() {
                    public boolean shouldOverrideUrlLoading(WebView view, String redirectUrl) {
                        Log.e(TAG, redirectUrl);
                        if (redirectUrl.startsWith(Credentials.REDIRECT_URL)) {
                            view.setClickable(false);
                            Uri uri = Uri.parse(redirectUrl);
                            String code = uri.getQueryParameter(KeyHelper.CODE);
                            if (code != null) {
                                helper.authorize(code);
                            }
                        }
                        return true;
                    }
                });
            }
        }
    }

    @Override
    public void onAccessTokenReceived(LoginDetails loginDetails, Response response) {
        BoxHelper.saveUserDetails(this, loginDetails);
        Intent intent = new Intent(LoginActivity.this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}
