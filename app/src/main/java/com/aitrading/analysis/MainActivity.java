package com.aitrading.analysis;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebChromeClient;
import android.util.Base64;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private WebView webView;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        webView = new WebView(this);
        setContentView(webView);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new ApiBridge(), "NativeApi");
        webView.loadUrl("file:///android_asset/index.html");
    }

    private class ApiBridge {
        @JavascriptInterface
        public void getForexData(String symbol, String apiKey, String callbackName) {
            executor.execute(() -> {
                String result;
                HttpURLConnection connection = null;
                try {
                    String encodedSymbol = URLEncoder.encode(symbol, "UTF-8");
                    String encodedKey = URLEncoder.encode(apiKey, "UTF-8");
                    URL url = new URL("https://api.twelvedata.com/time_series?symbol=" +
                        encodedSymbol + "&interval=1min&outputsize=60&apikey=" + encodedKey);
                    connection = (HttpURLConnection) url.openConnection();
                    connection.setRequestMethod("GET");
                    connection.setConnectTimeout(15000);
                    connection.setReadTimeout(15000);
                    int status = connection.getResponseCode();
                    BufferedReader reader = new BufferedReader(new InputStreamReader(
                        status >= 200 && status < 400 ? connection.getInputStream() : connection.getErrorStream()));
                    StringBuilder body = new StringBuilder();
                    String line;
                    while ((line = reader.readLine()) != null) body.append(line);
                    reader.close();
                    result = body.toString();
                } catch (Exception e) {
                    result = "{\"status\":\"error\",\"message\":\"Network error. Internet/API check karein.\"}";
                } finally {
                    if (connection != null) connection.disconnect();
                }
                String encoded = Base64.encodeToString(result.getBytes(java.nio.charset.StandardCharsets.UTF_8), Base64.NO_WRAP);
                String js = "window.onNativeApiResult('" + callbackName + "','" + encoded + "')";
                runOnUiThread(() -> webView.evaluateJavascript(js, null));
            });
        }
    }

    @Override protected void onDestroy() {
        executor.shutdownNow();
        if (webView != null) webView.destroy();
        super.onDestroy();
    }
}
