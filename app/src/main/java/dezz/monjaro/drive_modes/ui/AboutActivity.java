/*
 * Copyright © 2026 Dezz
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package dezz.monjaro.drive_modes.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.os.Bundle;
import android.text.Html;
import android.text.method.LinkMovementMethod;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.webkit.WebResourceError;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import dezz.monjaro.drive_modes.R;
import dezz.monjaro.drive_modes.databinding.ActivityAboutBinding;

/**
 * About screen. Loads the online About page in a WebView and falls back to a
 * compact offline blurb (author, source, link) when there is no connectivity
 * or the page can't be reached.
 */
public class AboutActivity extends AppCompatActivity {

    private static final String PAGE_HOST = "dezzk.github.io";

    private ActivityAboutBinding binding;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityAboutBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        binding.backButton.setOnClickListener(v -> finish());

        binding.aboutFallback.setMovementMethod(LinkMovementMethod.getInstance());
        binding.aboutFallback.setText(Html.fromHtml(
                getString(R.string.about_fallback), Html.FROM_HTML_MODE_COMPACT));

        WebView web = binding.aboutWebView;
        web.getSettings().setJavaScriptEnabled(false);
        web.getSettings().setDomStorageEnabled(false);
        web.setBackgroundColor(0x00000000);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                binding.aboutProgress.setVisibility(View.VISIBLE);
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                binding.aboutProgress.setVisibility(View.GONE);
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request, WebResourceError error) {
                if (request.isForMainFrame()) {
                    showFallback();
                }
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, @NonNull WebResourceRequest request) {
                // Keep the About page itself in the WebView; open every other
                // link (Telegram, GitHub) in the system browser.
                String host = request.getUrl().getHost();
                if (host != null && host.equalsIgnoreCase(PAGE_HOST)) {
                    return false;
                }
                Intent intent = new Intent(Intent.ACTION_VIEW, request.getUrl());
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                try {
                    startActivity(intent);
                } catch (Exception ignored) {
                    return false;
                }
                return true;
            }
        });

        if (isOnline()) {
            web.loadUrl(getString(R.string.about_url));
        } else {
            showFallback();
        }
    }

    private boolean isOnline() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        if (cm == null) {
            return false;
        }
        Network active = cm.getActiveNetwork();
        if (active == null) {
            return false;
        }
        NetworkCapabilities caps = cm.getNetworkCapabilities(active);
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    private void showFallback() {
        binding.aboutProgress.setVisibility(View.GONE);
        binding.aboutWebView.setVisibility(View.GONE);
        binding.aboutFallbackContainer.setVisibility(View.VISIBLE);
    }

    @Override
    protected void onDestroy() {
        if (binding != null) {
            // Detach from the view tree and destroy() so the native WebView
            // resources are released, not just the Java references.
            WebView web = binding.aboutWebView;
            web.stopLoading();
            web.setWebViewClient(new WebViewClient());
            web.loadUrl("about:blank");
            ViewParent parent = web.getParent();
            if (parent instanceof ViewGroup) {
                ((ViewGroup) parent).removeView(web);
            }
            web.destroy();
        }
        super.onDestroy();
    }
}
