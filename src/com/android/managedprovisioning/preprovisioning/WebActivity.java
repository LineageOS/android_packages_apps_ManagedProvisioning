/*
 * Copyright 2014, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.managedprovisioning.preprovisioning;

import static com.android.internal.logging.nano.MetricsProto.MetricsEvent.PROVISIONING_WEB_ACTIVITY_TIME_MS;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.webkit.URLUtil;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import com.android.managedprovisioning.R;
import com.android.managedprovisioning.analytics.TimeLogger;
import com.android.managedprovisioning.common.ProvisionLogger;
import com.android.managedprovisioning.common.SettingsFacade;
import com.android.managedprovisioning.preprovisioning.terms.TermsActivity;

/**
 * This activity shows a web view, which loads the url indicated in the starting intent. By default
 * the user can click on links and load other urls. However, by passing the allowed url base, the
 * web view can be limited to urls that start with this base.
 *
 * <p>This activity is considered for using by
 * {@link TermsActivity} to display the support web pages
 * about provisioning concepts.
 */
public class WebActivity extends Activity {
    private static final String EXTRA_URL = "extra_url";

    private WebView mWebView;
    private SettingsFacade mSettingsFacade = new SettingsFacade();
    private TimeLogger mTimeLogger;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mTimeLogger = new TimeLogger(this, PROVISIONING_WEB_ACTIVITY_TIME_MS);

        final String extraUrl = getIntent().getStringExtra(EXTRA_URL);
        if (extraUrl == null) {
            Toast.makeText(this, R.string.url_error, Toast.LENGTH_SHORT).show();
            ProvisionLogger.loge("No url provided to WebActivity.");
            finish();
        }

        mWebView = new WebView(this);
        // We need a custom WebViewClient. Without this an external browser will load the URL.
        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                final String url = request.getUrl().toString();
                if (URLUtil.isNetworkUrl(url)) {
                    view.loadUrl(url);
                    return true;
                }
                return false;
            }
        });
        mWebView.loadUrl(extraUrl);
        // Enable zoom gesture in web view.
        WebSettings webSettings = mWebView.getSettings();
        webSettings.setBuiltInZoomControls(true);
        webSettings.setDisplayZoomControls(false);
        webSettings.setJavaScriptEnabled(true); // TODO: review with security http://b/34292506
        if (!mSettingsFacade.isUserSetupCompleted(this)) {
            // User should not be able to escape provisioning if user setup isn't complete.
            mWebView.setOnLongClickListener(v -> true);
        }
        mTimeLogger.start();
        setContentView(mWebView);
    }

    @Override
    public void onDestroy() {
        mTimeLogger.stop();
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (mWebView.canGoBack()) {
            mWebView.goBack();
        } else {
            super.onBackPressed();
        }
    }

    /**
     * Creates an intent to launch the {@link WebActivity}.
     * @param url the url to be shown upon launching this activity
     */
    @Nullable
    static Intent createIntent(Context context, String url) {
        if (URLUtil.isNetworkUrl(url)) {
            return new Intent(context, WebActivity.class).putExtra(EXTRA_URL, url);
        }
        return null;
    }
}