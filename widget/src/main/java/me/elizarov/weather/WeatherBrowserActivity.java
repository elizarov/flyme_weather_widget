package me.elizarov.weather;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.WindowManager;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.LinearLayout;

/**
 * Embedded HTTPS browser. API credentials are never passed to this Activity or to JavaScript.
 */
public final class WeatherBrowserActivity extends Activity {
    static final int WIDTH = 1440;
    static final int HEIGHT = 900;
    private WebView browser;

    static void open(Context context, String url) {
        Intent intent = new Intent(context, WeatherBrowserActivity.class).putExtra("url", url)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        ActivityOptions options = ActivityOptions.makeBasic();
        if (context instanceof Activity) {
            Activity caller = (Activity) context;
            options.setLaunchDisplayId(caller.getDisplay().getDisplayId());
            PreviewPopup.setLaunchBounds(context, options, intent,
                    caller.getWindowManager().getMaximumWindowMetrics().getBounds(), WIDTH, HEIGHT);
        }
        context.startActivity(intent, options.toBundle());
    }

    @Override
    public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        Button back = new Button(this);
        back.setText("← Назад");
        back.setAllCaps(false);
        back.setTextSize(24);
        back.setOnClickListener(v -> {
            if (browser != null && browser.canGoBack()) browser.goBack();
            else finish();
        });
        LinearLayout toolbar = new LinearLayout(this);
        int toolbarHeight = Math.round(64 * getResources().getDisplayMetrics().density);
        toolbar.addView(back, new LinearLayout.LayoutParams(-2, -1));
        toolbar.addView(new android.widget.Space(this), new LinearLayout.LayoutParams(0, 1, 1));
        ImageButton close = new ImageButton(this);
        close.setTag("browser_close");
        close.setContentDescription("Закрыть браузер");
        close.setImageResource(R.drawable.ic_close);
        close.setOnClickListener(v -> finishAndRemoveTask());
        toolbar.addView(close, new LinearLayout.LayoutParams(toolbarHeight, -1));
        root.addView(toolbar, new LinearLayout.LayoutParams(-1, toolbarHeight));
        android.widget.ProgressBar progress = new android.widget.ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        progress.setIndeterminate(false);
        root.addView(progress, new LinearLayout.LayoutParams(-1, 4));
        browser = new WebView(this);
        browser.setBackgroundColor(android.graphics.Color.WHITE);
        browser.setTag("browser");
        browser.getSettings().setJavaScriptEnabled(true);
        browser.getSettings().setDomStorageEnabled(true);
        browser.getSettings().setAllowFileAccess(false);
        browser.getSettings().setAllowContentAccess(false);
        browser.setWebChromeClient(new android.webkit.WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int value) {
                progress.setProgress(value);
                progress.setVisibility(value < 100 ? android.view.View.VISIBLE : android.view.View.INVISIBLE);
            }
        });
        browser.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageStarted(WebView view, String url, android.graphics.Bitmap favicon) {
                progress.setVisibility(android.view.View.VISIBLE);
            }

            @Override
            public void onPageCommitVisible(WebView view, String url) {
                progress.setVisibility(android.view.View.INVISIBLE);
            }

            @Override
            public boolean onRenderProcessGone(WebView view, android.webkit.RenderProcessGoneDetail detail) {
                progress.setVisibility(android.view.View.INVISIBLE);
                root.removeView(view);
                view.destroy();
                if (browser == view) browser = null;
                if (!isFinishing() && !isDestroyed()) {
                    LinearLayout message = new LinearLayout(WeatherBrowserActivity.this);
                    message.setOrientation(LinearLayout.VERTICAL);
                    message.setGravity(Gravity.CENTER);
                    android.widget.TextView text = new android.widget.TextView(WeatherBrowserActivity.this);
                    text.setText("Не удалось открыть страницу");
                    text.setTextSize(28);
                    message.addView(text);
                    Button retry = new Button(WeatherBrowserActivity.this);
                    retry.setText("Повторить");
                    retry.setOnClickListener(v -> recreate());
                    message.addView(retry);
                    root.addView(message, new LinearLayout.LayoutParams(-1, 0, 1));
                }
                return true;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, android.webkit.WebResourceRequest request) {
                return !"https".equalsIgnoreCase(request.getUrl().getScheme());
            }
        });
        root.addView(browser, new LinearLayout.LayoutParams(-1, 0, 1));
        setContentView(root);
        // A resized/freeform task may report widthPixels=heightPixels=0 during creation.
        // Use display window bounds; negative layout dimensions wrap to enormous WebView sizes.
        Rect display = getWindowManager().getMaximumWindowMetrics().getBounds();
        WindowManager.LayoutParams bounds = getWindow().getAttributes();
        if (!PreviewPopup.layoutInTask(this, bounds, WIDTH, HEIGHT)) {
            bounds.width = Math.min(WIDTH, Math.max(1, display.width() - 64));
            bounds.height = Math.min(HEIGHT, Math.max(1, display.height() - 160));
            bounds.gravity = Gravity.TOP | Gravity.END;
            bounds.x = Math.min(512, Math.max(0, (display.width() - bounds.width) / 2));
            bounds.y = Math.min(180, Math.max(0, display.height() - bounds.height - 40));
        }
        getWindow().setAttributes(bounds);
        loadPage(getIntent());
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        if (browser == null) recreate();
        else loadPage(intent);
    }

    private void loadPage(Intent intent) {
        String url = intent.getStringExtra("url");
        if (url != null && "https".equals(Uri.parse(url).getScheme())) browser.loadUrl(url);
        else finish();
    }

    @Override
    protected void onDestroy() {
        if (browser != null) browser.destroy();
        super.onDestroy();
    }
}
