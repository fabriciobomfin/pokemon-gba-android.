package com.fabricio.gbaoffline;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.ActivityInfo;
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
    private boolean backgrounded;
    private int pauseGeneration;

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
                    if (path.equals("/roms/" + getPreferences(MODE_PRIVATE).getString("romId", "game") + ".gba")) {
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
        @JavascriptInterface public String library() {
            JSONObject games = new JSONObject();
            for (java.util.Map.Entry<String,?> entry : getPreferences(MODE_PRIVATE).getAll().entrySet())
                if (entry.getKey().startsWith("title.")) try {
                    games.put(entry.getKey().substring(6), entry.getValue());
                } catch (Exception ignored) {}
            try {
                JSONObject active = new JSONObject(info());
                if (active.getBoolean("available")) games.put(active.getString("id"), active.getString("name"));
            } catch (Exception ignored) {}
            return games.toString();
        }
        @JavascriptInterface public void select(String id) {
            runOnUiThread(() -> {
                if (id.equals(getPreferences(MODE_PRIVATE).getString("romId",""))) { play(); return; }
                if (id.matches("[a-f0-9]{64}")) {
                    File file = new File(getFilesDir(), id + ".gba");
                    if (file.exists()) importRom(Uri.fromFile(file));
                }
            });
        }
        @JavascriptInterface public void orientation(int value) {
            runOnUiThread(() -> setRequestedOrientation(value == 1 ? ActivityInfo.SCREEN_ORIENTATION_PORTRAIT : value == 2 ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE : ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED));
        }
        @JavascriptInterface public void immersive(boolean value) {
            runOnUiThread(() -> getWindow().getDecorView().setSystemUiVisibility(value ? 5894 : 0));
        }
        @JavascriptInterface public void pulse() {
            runOnUiThread(() -> web.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY));
        }
        @JavascriptInterface public boolean stagePoint(int index,String json) {
            try {
                if(index<0 || json.length()>16777216) return false;
                try(FileOutputStream out=new FileOutputStream(stage(index))) {out.write(json.getBytes("UTF-8"));}
                return true;
            } catch(IOException e){return false;}
        }
        @JavascriptInterface public String staged(int index) {
            try(FileInputStream in=new FileInputStream(stage(index))){return readEntry(in,16777216);}
            catch(IOException e){return "";}
        }
        @JavascriptInterface public void finishBackup(String message){notifyUser(message);}
        @JavascriptInterface public void exportPoints(int count){pointCount=count;backup(true);}
        @JavascriptInterface public void backup(boolean export) {
            runOnUiThread(() -> {
                Intent intent = new Intent(export ? Intent.ACTION_CREATE_DOCUMENT : Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType(export ? "application/zip" : "*/*");
                if (export) intent.putExtra(Intent.EXTRA_TITLE,"Fire-Game-" + getPreferences(MODE_PRIVATE).getString("romId","game").substring(0,4) + ".zip");
                startActivityForResult(intent, export ? 13 : 14);
            });
        }
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
            runOnUiThread(() -> { if (!importing && new File(getFilesDir(), "game.gba").isFile()) openGame(); });
        }
        @JavascriptInterface public void home() { runOnUiThread(() -> { orientation(0); immersive(false); web.loadUrl(HOME); }); }
        @JavascriptInterface public boolean saveFiles(String json) {
            try {
                if (json.length() > 8 * 1024 * 1024) return false;
                JSONObject files = new JSONObject(json);
                if (files.length() > 32) return false;
                File directory = saveDirectory();
                java.util.Iterator<String> names = files.keys();
                while (names.hasNext()) {
                    String name = names.next();
                    if (!safeSaveName(name)) return false;
                    byte[] bytes = android.util.Base64.decode(files.getString(name), android.util.Base64.DEFAULT);
                    if (bytes.length > 2 * 1024 * 1024) return false;
                    File destination = new File(directory, name);
                    File parent = destination.getParentFile();
                    if (!parent.isDirectory() && !parent.mkdirs()) return false;
                    AtomicFile file = new AtomicFile(destination);
                    FileOutputStream out = file.startWrite();
                    try { out.write(bytes); file.finishWrite(out); }
                    catch (IOException error) { file.failWrite(out); throw error; }
                }
                return true;
            } catch (Exception error) { return false; }
        }
        @JavascriptInterface public String loadFiles() {
            JSONObject result = new JSONObject();
            try {
                collectSaves(saveDirectory(), "", result);
            } catch (Exception ignored) { }
            return result.toString();
        }
    }

    private void collectSaves(File directory, String prefix, JSONObject result) throws Exception {
        File[] entries = directory.listFiles();
        if (entries == null) return;
        for (File entry : entries) {
            String name = prefix + entry.getName();
            if (!safeSaveName(name)) continue;
            if (entry.isDirectory()) {
                collectSaves(entry, name + "/", result);
            } else if (entry.isFile() && !name.endsWith(".new") && !name.endsWith(".bak") && entry.length() <= 2 * 1024 * 1024) {
                try (InputStream input = new AtomicFile(entry).openRead(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192]; int count;
                    while ((count = input.read(buffer)) != -1) out.write(buffer, 0, count);
                    result.put(name, android.util.Base64.encodeToString(out.toByteArray(), android.util.Base64.NO_WRAP));
                }
            }
        }
    }
    private boolean safeSaveName(String name) {
        if (name.length() > 320) return false;
        String[] parts = name.split("/", -1);
        if (parts.length > 4) return false;
        for (String part : parts) {
            if (!part.matches("[a-zA-Z0-9._-]{1,160}") || part.equals(".") || part.equals("..")) return false;
        }
        return true;
    }
    private File saveDirectory() throws IOException {
        String id = getPreferences(MODE_PRIVATE).getString("romId", "game");
        if (!id.matches("[a-zA-Z0-9_-]{1,80}")) throw new IOException("Invalid ROM id");
        File directory = new File(new File(getFilesDir(), "saves"), id);
        if (!directory.isDirectory() && !directory.mkdirs()) throw new IOException("Unable to create save directory");
        return directory;
    }

    private volatile int pointCount;
    private File stage(int index){return new File(getCacheDir(),"checkpoint-"+index+".tmp");}
    private String readEntry(InputStream in,int max) throws IOException {
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buf=new byte[8192];int n;
        while((n=in.read(buf))!=-1){if(out.size()+n>max)throw new IOException("Entrada muito grande");out.write(buf,0,n);}
        return out.toString("UTF-8");
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if ((request == 13 || request == 14) && result == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            imports.execute(() -> {
                try {
                    Bridge bridge = new Bridge();
                    String id = getPreferences(MODE_PRIVATE).getString("romId","game");
                    if (request == 13) {
                        JSONObject files = new JSONObject(bridge.loadFiles());
                        if(files.length()==0 && pointCount==0)throw new IOException("Nenhum save disponível.");
                        try(java.util.zip.ZipOutputStream zip=new java.util.zip.ZipOutputStream(getContentResolver().openOutputStream(uri,"wt"))){
                            zip.putNextEntry(new java.util.zip.ZipEntry("backup.json"));
                            zip.write(new JSONObject().put("version",2).put("rom",id).put("count",pointCount).put("saves",files).toString().getBytes("UTF-8"));
                            for(int i=0;i<pointCount;i++){
                                zip.putNextEntry(new java.util.zip.ZipEntry("point-"+i+".json"));
                                try(InputStream in=new FileInputStream(stage(i))){byte[] buf=new byte[8192];int n;while((n=in.read(buf))!=-1)zip.write(buf,0,n);}
                            }
                        }
                        notifyUser("Backup exportado: "+pointCount+" pontos de save.");
                    } else try(BufferedInputStream input=new BufferedInputStream(getContentResolver().openInputStream(uri))){
                        input.mark(2);boolean zipped=input.read()==80;input.reset();
                        java.util.zip.ZipInputStream zip=zipped?new java.util.zip.ZipInputStream(input):null;
                        java.util.zip.ZipEntry header=zipped?zip.getNextEntry():null;
                        if(zipped && (header==null || !"backup.json".equals(header.getName())))throw new IOException("Formato inválido");
                        JSONObject backup=new JSONObject(readEntry(zipped?zip:input,8388608));
                        if(!id.equals(backup.getString("rom")))throw new IOException("Importe a mesma ROM antes de restaurar.");
                        int count=zipped?backup.getInt("count"):0;
                        if(count<0 || count>100000)throw new IOException("Contagem inválida");
                        for(int i=0;i<count;i++){
                            java.util.zip.ZipEntry entry=zip.getNextEntry();
                            if(entry==null || !entry.getName().equals("point-"+i+".json") || !bridge.stagePoint(i,readEntry(zip,16777216)))throw new IOException("Ponto inválido");
                        }
                        JSONObject saves=backup.getJSONObject("saves");
                        if(saves.length()>0 && !bridge.saveFiles(saves.toString()))throw new IOException("Progresso inválido");
                        runOnUiThread(()->web.evaluateJavascript("restorePoints("+count+","+JSONObject.quote(id)+")",null));
                    }
                } catch (Exception ex) { notifyUser("Falha no backup: " + ex.getMessage()); }
            });
        }
        if (request == PICK_ROM && result == RESULT_OK && data != null && data.getData() != null) importRom(data.getData());
    }

    private void notifyUser(String message) {
        runOnUiThread(() -> Toast.makeText(this,message,Toast.LENGTH_LONG).show());
    }

    private void archiveActive() throws IOException {
        android.content.SharedPreferences prefs = getPreferences(MODE_PRIVATE);
        String id = prefs.getString("romId","");
        File current = new File(getFilesDir(),"game.gba");
        if (!id.matches("[a-f0-9]{64}") || !current.exists()) return;
        File archive = new File(getFilesDir(),id + ".gba");
        if (!archive.exists()) {
            File tmp = new File(getFilesDir(),"archive.tmp");
            try (InputStream input = new FileInputStream(current); OutputStream out = new FileOutputStream(tmp)) {
                byte[] buffer = new byte[65536]; int n;
                while ((n=input.read(buffer)) != -1) out.write(buffer,0,n);
            }
            if (!tmp.renameTo(archive)) throw new IOException("Falha ao guardar jogo anterior.");
        }
        prefs.edit().putString("title."+id,prefs.getString("romName","ROM GBA")).commit();
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
                if ("file".equals(uri.getScheme())) name = getPreferences(MODE_PRIVATE).getString("title."+new File(uri.getPath()).getName().replace(".gba",""),"ROM GBA");
                else try (Cursor cursor = getContentResolver().query(uri, new String[]{OpenableColumns.DISPLAY_NAME}, null, null, null)) {
                    if (cursor != null && cursor.moveToFirst()) name = cursor.getString(0);
                }
                if (name == null) name = "ROM GBA";
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] header = new byte[192];
                int total = 0;
                archiveActive();
                try (InputStream raw = getContentResolver().openInputStream(uri)) {
                    InputStream input = raw;
                    if (name.toLowerCase(java.util.Locale.ROOT).endsWith(".zip")) {
                        java.util.zip.ZipInputStream zip = new java.util.zip.ZipInputStream(raw);
                        java.util.zip.ZipEntry entry = zip.getNextEntry();
                        int entries = 0, skipped = 0;
                        byte[] skipBuffer = new byte[8192];
                        while (entry != null && (entry.isDirectory() || !entry.getName().toLowerCase(java.util.Locale.ROOT).endsWith(".gba"))) {
                            if (++entries > 256) throw new IOException("ZIP contém muitas entradas.");
                            int n;
                            while ((n=zip.read(skipBuffer)) != -1) {
                                skipped+=n;
                                if (skipped > 33554432) throw new IOException("ZIP muito grande.");
                            }
                            entry = zip.getNextEntry();
                        }
                        if (entry == null) throw new IOException("Nenhuma ROM .gba no ZIP.");
                        name = new File(entry.getName()).getName();
                        input = zip;
                    }
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
                if (total < 192 || (header[0xB2] & 255) != 0x96) throw new IOException("Arquivo inválido. O cabeçalho GBA não foi reconhecido.");
                target.finishWrite(output);
                output = null;
                StringBuilder hash = new StringBuilder();
                for (byte b : digest.digest()) hash.append(String.format("%02x", b & 255));
                getPreferences(MODE_PRIVATE).edit().putString("romName", name).putString("romId", hash.toString()).commit();
                runOnUiThread(() -> { importing = false; if (!isDestroyed()) openGame(); });
            } catch (Exception e) {
                if (output != null) target.failWrite(output);
                String message = e.getMessage() == null ? "Não foi possível importar a ROM." : e.getMessage();
                runOnUiThread(() -> { importing = false; if (!isDestroyed()) Toast.makeText(this, message, Toast.LENGTH_LONG).show(); });
            }
        });
    }
    private void openGame() {
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        web.loadUrl(PLAY);
    }
    @Override protected void onPause() {
        backgrounded = true;
        final int generation = ++pauseGeneration;
        // Keep the WebView running until its synchronous native save has completed.
        web.evaluateJavascript("if(window.flushAndPause) flushAndPause()", result -> {
            if (backgrounded && generation == pauseGeneration && !isDestroyed()) web.onPause();
        });
        super.onPause();
    }
    @Override protected void onResume() {
        super.onResume();
        backgrounded = false;
        ++pauseGeneration;
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

