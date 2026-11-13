package com.fabricio.gbaoffline;
import android.app.Activity;
import android.os.Bundle;
import android.content.Intent;
import android.net.Uri;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebSettings;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebViewAssetLoader;
import androidx.annotation.Nullable;
import java.io.*;
public class MainActivity extends Activity {
  private static final int PICK_ROM=12;
  WebView web;
  @Override public void onCreate(Bundle b){super.onCreate(b);
    web=new WebView(this); setContentView(web);
    web.getSettings().setJavaScriptEnabled(true);web.getSettings().setDomStorageEnabled(true);
    web.getSettings().setAllowFileAccess(false);web.getSettings().setAllowContentAccess(false);
    web.setWebViewClient(new WebViewClient(){
      @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){
        Uri u=r.getUrl();if(!"appassets.androidplatform.net".equals(u.getHost()))return null;
        String path=u.getPath();if(path==null)return null;
        if(path.equals("/rom.gba")){try {return new WebResourceResponse("application/octet-stream",null,openFileInput("game.gba"));}catch(Exception e){return new WebResourceResponse("text/plain","UTF-8",404,"Not Found",new java.util.HashMap<>(),new ByteArrayInputStream(new byte[0]));}}
        if(path.startsWith("/assets/")){try{return new WebResourceResponse(mime(path),null,getAssets().open(path.substring(8)));}catch(Exception e){return null;}}
        return null;
      }
    });
    web.addJavascriptInterface(new Object(){@android.webkit.JavascriptInterface public void pick(){runOnUiThread(()->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("*/*");i.addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,PICK_ROM);});}}, "Android");
    web.loadUrl("https://appassets.androidplatform.net/assets/index.html");
  }
  private String mime(String p){if(p.endsWith(".js"))return "text/javascript";if(p.endsWith(".css"))return "text/css";if(p.endsWith(".wasm"))return "application/wasm";if(p.endsWith(".html"))return "text/html";return "application/octet-stream";}
  @Override protected void onActivityResult(int req,int result,@Nullable Intent data){super.onActivityResult(req,result,data);if(req==PICK_ROM&&result==RESULT_OK&&data!=null){try(InputStream in=getContentResolver().openInputStream(data.getData());OutputStream out=openFileOutput("game.gba",MODE_PRIVATE)){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);web.loadUrl("https://appassets.androidplatform.net/assets/play.html");}catch(Exception e){android.widget.Toast.makeText(this,"Erro ao importar ROM",0).show();}}}
  @Override public void onBackPressed(){if(web.canGoBack())web.goBack();else super.onBackPressed();}
}