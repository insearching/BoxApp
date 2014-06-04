package com.boxapp;

import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.boxapp.utils.KeyMap;

import java.net.MalformedURLException;
import java.net.URL;


public class BoxLoginActivity extends ActionBarActivity {

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
                        URL url = null;
                        try {
                            url = new URL(urlStr);

                            if (url.getProtocol().equals("https") && url.getHost().equals("www.box.com")
                                    && url.getPath().contains("services") && url.getQuery().contains("code")){
                                Uri uri = Uri.parse(urlStr);
                                String code = uri.getQueryParameter("code");
                                Log.d ("BoxApp", code);
                                //TODO: Copy authorization process here from login activity.
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
}
