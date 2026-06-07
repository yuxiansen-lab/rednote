package com.rednote.app;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.content.Intent;
import android.net.Uri;
import android.widget.FrameLayout;
import android.widget.ProgressBar;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;

public class MainActivity extends AppCompatActivity {

    private WebView webView;
    private SwipeRefreshLayout swipeRefreshLayout;
    private ProgressBar progressBar;
    private static final String HOME_URL = "https://rednote.sulsul.top";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Root layout
        FrameLayout rootLayout = new FrameLayout(this);

        // SwipeRefresh for pull-to-refresh
        swipeRefreshLayout = new SwipeRefreshLayout(this);
        swipeRefreshLayout.setColorSchemeColors(0xFFFF2442);
        swipeRefreshLayout.setProgressBackgroundColorSchemeColor(0xFFFFFFFF);

        // Progress bar at top
        progressBar = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progressBar.setLayoutParams(new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                dpToPx(3)
        ));
        progressBar.setProgressDrawable(getResources().getDrawable(R.drawable.progress_bar));
        progressBar.setMax(100);

        // WebView
        webView = new WebView(this);
        setupWebView();

        swipeRefreshLayout.addView(webView);
        rootLayout.addView(swipeRefreshLayout);
        rootLayout.addView(progressBar);

        swipeRefreshLayout.setOnRefreshListener(() -> {
            webView.reload();
        });

        setContentView(rootLayout);

        webView.loadUrl(HOME_URL);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void setupWebView() {
        WebSettings settings = webView.getSettings();

        // JavaScript
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        // Rendering
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(true);

        // Mixed content: allow HTTP images on HTTPS pages
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        }

        // Autofill
        settings.setSaveFormData(false);
        settings.setSavePassword(false);

        // User agent (spoof slightly to avoid mobile restrictions)
        String ua = settings.getUserAgentString();
        settings.setUserAgentString(ua + " RedNoteApp/1.0");

        // Enable WebRTC and media for possible future features
        settings.setMediaPlaybackRequiresUserGesture(true);

        // JavaScript interface for native features
        webView.addJavascriptInterface(new WebAppInterface(), "RedNoteAndroid");

        // WebViewClient
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                super.onPageStarted(view, url, favicon);
                progressBar.setVisibility(View.VISIBLE);
                progressBar.setProgress(10);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                progressBar.setVisibility(View.GONE);
                swipeRefreshLayout.setRefreshing(false);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String url = request.getUrl().toString();
                // Stay within the app for rednote.sulsul.top
                if (url.startsWith(HOME_URL) || url.startsWith("https://rednote.sulsul.top")) {
                    return false;
                }
                // Open external links in browser
                try {
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
                    startActivity(intent);
                } catch (Exception e) {
                    // fallback: load in WebView
                    return false;
                }
                return true;
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl) {
                super.onReceivedError(view, errorCode, description, failingUrl);
                swipeRefreshLayout.setRefreshing(false);
                // Show error page if no internet
                if (!isNetworkAvailable()) {
                    loadErrorPage("网络连接失败，请检查网络后重试");
                }
            }
        });

        // WebChromeClient for progress
        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                super.onProgressChanged(view, newProgress);
                progressBar.setProgress(newProgress);
                if (newProgress == 100) {
                    progressBar.setVisibility(View.GONE);
                }
            }
        });

        // Enable cookies
        CookieManager.getInstance().setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);
        }
    }

    private boolean isNetworkAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
        return activeNetwork != null && activeNetwork.isConnectedOrConnecting();
    }

    private void loadErrorPage(String message) {
        String html = "<html><body style='display:flex;align-items:center;justify-content:center;height:100vh;margin:0;background:#f5f5f5;font-family:sans-serif'>"
                + "<div style='text-align:center;padding:20px'>"
                + "<div style='font-size:64px;margin-bottom:16px'>😵</div>"
                + "<p style='font-size:16px;color:#666;margin-bottom:20px'>" + message + "</p>"
                + "<button onclick='window.RedNoteAndroid.retry()' style='padding:10px 32px;border-radius:8px;border:none;background:#ff2442;color:#fff;font-size:15px'>重试</button>"
                + "</div></body></html>";
        webView.loadDataWithBaseURL(HOME_URL, html, "text/html", "UTF-8", null);
    }

    private int dpToPx(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_BACK && webView.canGoBack()) {
            webView.goBack();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        webView.saveState(outState);
    }

    @Override
    protected void onRestoreInstanceState(Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        webView.restoreState(savedInstanceState);
    }

    @Override
    public void onDestroy() {
        if (webView != null) {
            webView.destroy();
        }
        super.onDestroy();
    }

    // JavaScript interface class
    public class WebAppInterface {
        @JavascriptInterface
        public void retry() {
            runOnUiThread(() -> webView.reload());
        }

        @JavascriptInterface
        public void share(String title, String url) {
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, title + "\n" + url);
            startActivity(Intent.createChooser(shareIntent, "分享"));
        }
    }
}
