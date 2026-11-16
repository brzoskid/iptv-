package pl.brzoskid.webosiptv;

import android.annotation.SuppressLint;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.exoplayer2.C;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.ExoPlaybackException;
import com.google.android.exoplayer2.Format;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.SimpleExoPlayer;
import com.google.android.exoplayer2.source.TrackGroup;
import com.google.android.exoplayer2.source.TrackGroupArray;
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector;
import com.google.android.exoplayer2.trackselection.MappingTrackSelector;
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout;
import com.google.android.exoplayer2.ui.PlayerView;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.util.Util;

import org.json.JSONArray;
import org.json.JSONObject;

public class MainActivity extends AppCompatActivity {

    private PlayerView playerView;
    private SimpleExoPlayer player;
    private DefaultTrackSelector trackSelector;
    private WebView webView;
    private String currentAspectMode = "fit";
    private volatile boolean jsInputActive = false;

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemUI();

        setContentView(R.layout.activity_main);

        playerView = findViewById(R.id.player_view);
        webView = findViewById(R.id.web_view);

        initExoPlayer();
        initWebView();
    }

    private void hideSystemUI() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                        | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
        );
    }

    private void initExoPlayer() {
        trackSelector = new DefaultTrackSelector(this);

        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
                .setBufferDurationsMs(4000, 20000, 1500, 2500)
                .build();

        String userAgent = Util.getUserAgent(this, "VLC/3.0.18 LibVLC/3.0.18");
        DefaultHttpDataSourceFactory httpFactory = new DefaultHttpDataSourceFactory(
                userAgent,
                8000,
                8000,
                true
        );
        DefaultDataSourceFactory dataSourceFactory = new DefaultDataSourceFactory(this, httpFactory);

        player = new SimpleExoPlayer.Builder(this)
                .setTrackSelector(trackSelector)
                .setLoadControl(loadControl)
                .setMediaSourceFactory(new com.google.android.exoplayer2.source.DefaultMediaSourceFactory(dataSourceFactory))
                .build();

        playerView.setPlayer(player);
        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
        playerView.setFocusable(false);

        player.addListener(new Player.EventListener() {
            @Override
            public void onPlaybackStateChanged(int state) {
                if (state == Player.STATE_READY) {
                    notifyJs("window.onNativePlayerReady && window.onNativePlayerReady()");
                } else if (state == Player.STATE_BUFFERING) {
                    notifyJs("window.onNativePlayerBuffering && window.onNativePlayerBuffering()");
                }
            }

            @Override
            public void onPlayerError(ExoPlaybackException error) {
                notifyJs("window.onNativePlayerError && window.onNativePlayerError()");
            }
        });
    }

    @SuppressLint({"SetJavaScriptEnabled", "AddJavascriptInterface"})
    private void initWebView() {
        webView.setBackgroundColor(Color.TRANSPARENT);
        webView.setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setAllowFileAccessFromFileURLs(true);
        settings.setAllowUniversalAccessFromFileURLs(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                webView.requestFocus();
            }
        });
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new AndroidPlayerBridge(), "AndroidPlayer");

        webView.loadUrl("file:///android_asset/index.html");
        webView.requestFocus();
    }

    private void notifyJs(final String script) {
        runOnUiThread(() -> {
            if (webView != null) {
                webView.evaluateJavascript(script, null);
            }
        });
    }

    private void sendKeyToJs(String jsKey) {
        String safeKey = jsKey.replace("'", "\'");
        String script = "(function(){" +
                "document.dispatchEvent(new KeyboardEvent('keydown', { key: '" + safeKey + "', bubbles: true }));" +
                "})();";
        notifyJs(script);
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_BUTTON_PRESS && ev.getButtonState() == MotionEvent.BUTTON_SECONDARY) {
            sendKeyToJs("Escape");
            return true;
        }
        return super.dispatchGenericMotionEvent(ev);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int keyCode = event.getKeyCode();

            if (jsInputActive && keyCode != KeyEvent.KEYCODE_BACK && keyCode != KeyEvent.KEYCODE_ESCAPE) {
                return super.dispatchKeyEvent(event);
            }

            switch (keyCode) {
                case KeyEvent.KEYCODE_DPAD_UP:
                    sendKeyToJs("ArrowUp");
                    return true;
                case KeyEvent.KEYCODE_DPAD_DOWN:
                    sendKeyToJs("ArrowDown");
                    return true;
                case KeyEvent.KEYCODE_DPAD_LEFT:
                    sendKeyToJs("ArrowLeft");
                    return true;
                case KeyEvent.KEYCODE_DPAD_RIGHT:
                    sendKeyToJs("ArrowRight");
                    return true;

                case KeyEvent.KEYCODE_DPAD_CENTER:
                case KeyEvent.KEYCODE_ENTER:
                case KeyEvent.KEYCODE_NUMPAD_ENTER:
                    sendKeyToJs("Enter");
                    return true;

                case KeyEvent.KEYCODE_BACK:
                case KeyEvent.KEYCODE_ESCAPE:
                    sendKeyToJs("Escape");
                    return true;

                case KeyEvent.KEYCODE_APP_SWITCH:
                case KeyEvent.KEYCODE_WINDOW:
                case KeyEvent.KEYCODE_TV_ZOOM_MODE:
                    sendKeyToJs("z");
                    return true;

                case KeyEvent.KEYCODE_MENU:
                    sendKeyToJs("m");
                    return true;

                case KeyEvent.KEYCODE_DEL:
                case KeyEvent.KEYCODE_FORWARD_DEL:
                    sendKeyToJs("a");
                    return true;

                case KeyEvent.KEYCODE_MEDIA_PREVIOUS:
                case KeyEvent.KEYCODE_F5:
                case KeyEvent.KEYCODE_GUIDE:
                    sendKeyToJs("e");
                    return true;

                case KeyEvent.KEYCODE_ALL_APPS:
                case KeyEvent.KEYCODE_INFO:
                case KeyEvent.KEYCODE_EXPLORER:
                    sendKeyToJs("i");
                    return true;

                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                case KeyEvent.KEYCODE_MEDIA_PLAY:
                case KeyEvent.KEYCODE_MEDIA_PAUSE:
                    togglePlayPause();
                    return true;

                case KeyEvent.KEYCODE_MEDIA_REWIND:
                    seekRelativeMs(-30000);
                    return true;
                case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                case KeyEvent.KEYCODE_MEDIA_NEXT:
                    seekRelativeMs(30000);
                    return true;

                case KeyEvent.KEYCODE_CHANNEL_UP:
                case KeyEvent.KEYCODE_PAGE_UP:
                    sendKeyToJs("ChannelUp");
                    return true;
                case KeyEvent.KEYCODE_CHANNEL_DOWN:
                case KeyEvent.KEYCODE_PAGE_DOWN:
                    sendKeyToJs("ChannelDown");
                    return true;
            }

            if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
                sendKeyToJs(String.valueOf(keyCode - KeyEvent.KEYCODE_0));
                return true;
            }
            if (keyCode >= KeyEvent.KEYCODE_NUMPAD_0 && keyCode <= KeyEvent.KEYCODE_NUMPAD_9) {
                sendKeyToJs(String.valueOf(keyCode - KeyEvent.KEYCODE_NUMPAD_0));
                return true;
            }

            int unicodeChar = event.getUnicodeChar();
            if (unicodeChar > 0) {
                char c = (char) unicodeChar;
                if (Character.isLetter(c) || c == ' ') {
                    sendKeyToJs(String.valueOf(c));
                    return true;
                }
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private void togglePlayPause() {
        runOnUiThread(() -> {
            if (player == null) return;
            boolean nextState = !player.getPlayWhenReady();
            player.setPlayWhenReady(nextState);
            notifyJs("showOsdToast && showOsdToast('" + (nextState ? "▶ Odtwarzanie" : "⏸ Pauza") + "')");
        });
    }

    private void seekRelativeMs(long deltaMs) {
        runOnUiThread(() -> {
            if (player == null || !player.isCurrentWindowSeekable()) return;
            long target = Math.max(0, player.getCurrentPosition() + deltaMs);
            player.seekTo(target);
            String label = deltaMs < 0 ? "⏪ -30 sek." : "⏩ +30 sek.";
            notifyJs("showOsdToast && showOsdToast('" + label + "')");
        });
    }

    public class AndroidPlayerBridge {

        @JavascriptInterface
        public void setInputFocused(boolean focused) {
            jsInputActive = focused;
        }

        @JavascriptInterface
        public void playStream(final String url) {
            runOnUiThread(() -> {
                if (player == null || url == null || url.isEmpty()) return;
                MediaItem mediaItem = MediaItem.fromUri(Uri.parse(url));
                player.setMediaItem(mediaItem);
                player.prepare();
                player.setPlayWhenReady(true);
            });
        }

        @JavascriptInterface
        public void stopStream() {
            runOnUiThread(() -> {
                if (player != null) {
                    player.stop();
                }
            });
        }

        @JavascriptInterface
        public String cycleAspectRatio() {
            if ("fit".equals(currentAspectMode)) {
                currentAspectMode = "fill";
            } else if ("fill".equals(currentAspectMode)) {
                currentAspectMode = "zoom";
            } else {
                currentAspectMode = "fit";
            }
            applyAspectRatio(currentAspectMode);
            return currentAspectMode;
        }

        @JavascriptInterface
        public void setAspectRatio(final String mode) {
            currentAspectMode = mode;
            applyAspectRatio(mode);
        }

        private void applyAspectRatio(final String mode) {
            runOnUiThread(() -> {
                if (playerView == null) return;
                switch (mode) {
                    case "fill":
                        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FILL);
                        break;
                    case "zoom":
                        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_ZOOM);
                        break;
                    case "fit":
                    default:
                        playerView.setResizeMode(AspectRatioFrameLayout.RESIZE_MODE_FIT);
                        break;
                }
            });
        }

        @JavascriptInterface
        public String getTracksJson() {
            JSONObject result = new JSONObject();
            JSONArray audioArr = new JSONArray();
            JSONArray textArr = new JSONArray();

            try {
                MappingTrackSelector.MappedTrackInfo trackInfo = trackSelector.getCurrentMappedTrackInfo();
                if (trackInfo != null) {
                    for (int rendererIndex = 0; rendererIndex < trackInfo.getRendererCount(); rendererIndex++) {
                        int trackType = trackInfo.getRendererType(rendererIndex);
                        if (trackType != C.TRACK_TYPE_AUDIO && trackType != C.TRACK_TYPE_TEXT) continue;

                        TrackGroupArray trackGroups = trackInfo.getTrackGroups(rendererIndex);
                        DefaultTrackSelector.Parameters params = trackSelector.getParameters();
                        boolean isDisabled = params.getRendererDisabled(rendererIndex);
                        DefaultTrackSelector.SelectionOverride override = params.getSelectionOverride(rendererIndex, trackGroups);

                        for (int groupIndex = 0; groupIndex < trackGroups.length; groupIndex++) {
                            TrackGroup group = trackGroups.get(groupIndex);
                            for (int trackIndex = 0; trackIndex < group.length; trackIndex++) {
                                Format format = group.getFormat(trackIndex);
                                JSONObject item = new JSONObject();
                                item.put("rendererIndex", rendererIndex);
                                item.put("groupIndex", groupIndex);
                                item.put("trackIndex", trackIndex);

                                String lang = format.language != null ? format.language.toUpperCase() : "UND";
                                String label = format.label != null ? format.label : ("Ścieżka " + (groupIndex + 1));
                                String codec = format.sampleMimeType != null ? format.sampleMimeType.replace("audio/", "").replace("application/", "").toUpperCase() : "";
                                item.put("title", lang + " • " + label + (codec.isEmpty() ? "" : " [" + codec + "]"));

                                boolean selected = !isDisabled && (
                                        (override != null && override.groupIndex == groupIndex && override.containsTrack(trackIndex))
                                                || (override == null && groupIndex == 0 && trackIndex == 0)
                                );
                                item.put("selected", selected);

                                if (trackType == C.TRACK_TYPE_AUDIO) {
                                    audioArr.put(item);
                                } else {
                                    textArr.put(item);
                                }
                            }
                        }
                    }
                }
                result.put("audio", audioArr);
                result.put("subtitles", textArr);
            } catch (Exception e) {
                e.printStackTrace();
            }
            return result.toString();
        }

        @JavascriptInterface
        public void selectTrack(final int rendererIndex, final int groupIndex, final int trackIndex) {
            runOnUiThread(() -> {
                MappingTrackSelector.MappedTrackInfo trackInfo = trackSelector.getCurrentMappedTrackInfo();
                if (trackInfo == null) return;

                TrackGroupArray trackGroups = trackInfo.getTrackGroups(rendererIndex);
                DefaultTrackSelector.ParametersBuilder builder = trackSelector.buildUponParameters();

                if (trackIndex < 0) {
                    builder.setRendererDisabled(rendererIndex, true);
                } else {
                    builder.setRendererDisabled(rendererIndex, false);
                    builder.setSelectionOverride(
                            rendererIndex,
                            trackGroups,
                            new DefaultTrackSelector.SelectionOverride(groupIndex, trackIndex)
                    );
                }
                trackSelector.setParameters(builder);
            });
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) player.setPlayWhenReady(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        hideSystemUI();
        if (webView != null) webView.requestFocus();
        if (player != null) player.setPlayWhenReady(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (player != null) {
            player.release();
            player = null;
        }
    }
}
