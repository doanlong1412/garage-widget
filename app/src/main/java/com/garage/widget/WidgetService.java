package com.garage.widget;

import android.app.Service;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.DisplayMetrics;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.RemoteViews;

public class WidgetService extends Service {

    private Handler mainHandler;

    @Override
    public void onCreate() {
        super.onCreate();
        mainHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        int appWidgetId = intent != null ?
                intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                        AppWidgetManager.INVALID_APPWIDGET_ID)
                : AppWidgetManager.INVALID_APPWIDGET_ID;

        mainHandler.post(() -> loadWebViewAndCapture(appWidgetId));
        return START_NOT_STICKY;
    }

    private void loadWebViewAndCapture(int appWidgetId) {
        // Widget size: 4x4 cells = roughly 400x400dp
        DisplayMetrics dm = getResources().getDisplayMetrics();
        int widthPx = Math.round(400 * dm.density);
        int heightPx = Math.round(400 * dm.density);

        WebView webView = new WebView(getApplicationContext());
        webView.measure(
                android.view.View.MeasureSpec.makeMeasureSpec(widthPx, android.view.View.MeasureSpec.EXACTLY),
                android.view.View.MeasureSpec.makeMeasureSpec(heightPx, android.view.View.MeasureSpec.EXACTLY)
        );
        webView.layout(0, 0, widthPx, heightPx);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);

        // Use same cookies as main app (shared login session)
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(webView, true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                // Wait for JS to render
                mainHandler.postDelayed(() -> {
                    try {
                        Bitmap bitmap = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888);
                        Canvas canvas = new Canvas(bitmap);
                        view.draw(canvas);

                        // Push bitmap to widget
                        AppWidgetManager manager = AppWidgetManager.getInstance(getApplicationContext());
                        RemoteViews views = new RemoteViews(getPackageName(), R.layout.widget_layout);
                        views.setImageViewBitmap(R.id.widget_image, bitmap);

                        // Click opens full app
                        Intent openIntent = new Intent(getApplicationContext(), MainActivity.class);
                        openIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        android.app.PendingIntent pi = android.app.PendingIntent.getActivity(
                                getApplicationContext(), 0, openIntent,
                                android.app.PendingIntent.FLAG_UPDATE_CURRENT |
                                android.app.PendingIntent.FLAG_IMMUTABLE
                        );
                        views.setOnClickPendingIntent(R.id.widget_container, pi);

                        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                            manager.updateAppWidget(appWidgetId, views);
                        } else {
                            ComponentName cn = new ComponentName(getApplicationContext(), GarageWidgetProvider.class);
                            manager.updateAppWidget(cn, views);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    stopSelf();
                }, 3000); // wait 3s for JS render
            }
        });

        webView.loadUrl(MainActivity.HASS_URL);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
