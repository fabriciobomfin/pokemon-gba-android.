package com.fabricio.gbaoffline;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.util.AtomicFile;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import org.json.JSONObject;
import java.io.*;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends Activity {
    private static final int PICK_ROM = 12;
    private static final String HOST = "appassets.androidplatform.net";
    private static final String HOME = "https://" + HOST + "/assets/index.html";
    private static final String PLAY = "https://" + HOST + "/assets/play.html";
    private final ExecutorService imports = Executors.newSingleThreadExecutor();
    WebView web;
    private boolean importing;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        web = new WebView(this);
        android.widget.FrameLayout frame = new android.widget.FrameLayout(this);
        frame.setBackgroundColor(android.graphics.Color.rgb(7,12,22));
        frame.addView(web);
        frame.setOnApplyWindowInsetsListener((view, insets) -> {
            view.setPadding(insets.getSystemWindowInsetLeft(), insets.getSystemWindowInsetTop(), insets.getSystemWindowInsetRight(), insets.getSystemWindowInsetBottom());
            return insets.consumeSystemWindowInsets();
        });
        setContentView(frame);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.setWebChromeClient(new WebChromeClient());
        web.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                return !"https".equals(uri.getScheme()) || !HOST.equals(uri.getHost());
            }
            @Override public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
                Uri uri = request.getUrl();
                if (!"https".equals(uri.getScheme()) || !HOST.equals(uri.getHost())) return error(403, "Offline only");
                String path = uri.getPath();
                if (path == null || path.contains("..")) return error(404, "Not Found");
                try {
                    InputStream stream;
                    if (path.equals("/rom.gba")) {
                        stream = new AtomicFile(new File(getFilesDir(), "game.gba")).openRead();
                    } else if (path.startsWith("/assets/")) {
                        stream = getAssets().open(path.substring(8));
                    } else return error(404, "Not Found");
                    HashMap<String, String> headers = new HashMap<>();
                    headers.put("Cache-Control", "no-store");
                    headers.put("X-Content-Type-Options", "nosniff");
                    return new WebResourceResponse(mime(path), null, 200, "OK", headers, stream);
                } catch (IOException e) { return error(404, "Not Found"); }
            }
        });
        web.addJavascriptInterface(new Bridge(), "Android");
        web.loadUrl(HOME);
    }

    private WebResourceResponse error(int code, String reason) {
        return new WebResourceResponse("text/plain", "UTF-8", code, reason, new HashMap<>(), new ByteArrayInputStream(new byte[0]));
    }
    private String mime(String path) {
        if (path.endsWith(".js")) return "text/javascript";
        if (path.endsWith(".css")) return "text/css";
        if (path.endsWith(".json")) return "application/json";
        if (path.endsWith(".wasm")) return "application/wasm";
        if (path.endsWith(".html")) return "text/html";
        if (path.endsWith(".png")) return "image/png";
        if (path.endsWith(".svg")) return "image/svg+xml";
        return "application/octet-stream";
    }
    public class Bridge {
        @JavascriptInterface public void pick() {
            runOnUiThread(() -> {
                if (importing) return;
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.setType("*/*");
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                startActivityForResult(intent, PICK_ROM);
            });
        }
        @JavascriptInterface public String info() {
            JSONObject result = new JSONObject();
            try {
                result.put("available", new File(getFilesDir(), "game.gba").isFile());
                result.put("name", getPreferences(MODE_PRIVATE).getString("romName", "ROM GBA"));
                result.put("id", getPreferences(MODE_PRIVATE).getString("romId", "game"));
            } catch (Exception ignored) { }
            return result.toString();
        }
        @JavascriptInterface public void play() {
            runOnUiThread(() -> { if (!importing && new File(getFilesDir(), "game.gba").isFile()) web.loadUrl(PLAY); });
        }
        @JavascriptInterface public void home() { runOnUiThread(() -> web.loadUrl(HOME)); }
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == PICK_ROM && result == RESULT_OK && data != null && data.getData() != null) importRom(data.getData());
    }

    void importRom(Uri uri) {
        if (importing) return;
        importing = true;
        Toast.makeText(this, "Importando ROM…", Toast.LENGTH_SHORT).show();
        imports.execute(() -> {
            AtomicFile target = new AtomicFile(new File(getFilesDir(), "game.gba"));
            FileOutputStream output = null;
            try {
                String name = "ROM GBA";
                try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) name = cursor.getString(0);
                }
                if (name == null) name = "ROM GBA";
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] header = new byte[192];
                int total = 0;
                try (InputStream input = getContentResolver().openInputStream(uri)) {
                    if (input == null) throw new IOException("Não foi possível abrir o arquivo.");
                    output = target.startWrite();
                    byte[] buffer = new byte[65536];
                    int count;
                    while ((count = input.read(buffer)) != -1) {
                        if (count == 0) continue;
                        if (total + count > 32 * 1024 * 1024) throw new IOException("ROM maior que 32 MB. Selecione o arquivo .gba descompactado.");
                        if (total < header.length) System.arraycopy(buffer, 0, header, total, Math.min(count, header.length - total));
                        output.write(buffer, 0, count);
                        digest.update(buffer, 0, count);
                        total += count;
                    }
                }
                if (total < 192 || (header[0xB2] & 255) != 0x96) throw new IOException("Arquivo inválido. Selecione uma ROM .gba, sem ZIP.");
                target.finishWrite(output);
                output = null;
                StringBuilder hash = new StringBuilder();
                for (byte b : digest.digest()) hash.append(String.format("%02x", b & 255));
                getPreferences(MODE_PRIVATE).edit().putString("romName", name).putString("romId", hash.toString()).commit();
                runOnUiThread(() -> { importing = false; if (!isDestroyed()) web.loadUrl(PLAY); });
            } catch (Exception e) {
                if (output != null) target.failWrite(output);
                String message = e.getMessage() == null ? "Não foi possível importar a ROM." : e.getMessage();
                runOnUiThread(() -> { importing = false; if (!isDestroyed()) Toast.makeText(this, message, Toast.LENGTH_LONG).show(); });
            }
        });
    }
    @Override protected void onPause() {
        web.evaluateJavascript("if(window.flushAndPause) flushAndPause()", null);
        web.onPause();
        super.onPause();
    }
    @Override protected void onResume() {
        super.onResume();
        if (web != null) { web.onResume(); web.evaluateJavascript("if(window.resumeGame) resumeGame()", null); }
    }
    @Override public void onBackPressed() {
        if (PLAY.equals(web.getUrl())) {
            web.evaluateJavascript("if(window.leaveGame) leaveGame()", null);
        } else super.onBackPressed();
    }
    @Override protected void onDestroy() {
        imports.shutdown();
        web.removeJavascriptInterface("Android");
        web.destroy();
        super.onDestroy();
    }
}
