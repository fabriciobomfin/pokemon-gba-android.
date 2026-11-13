package com.fabricio.gbaoffline;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileInputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Uses an original 256-byte homebrew test program, never a commercial ROM. */
public class OfflineSmokeRunner extends Instrumentation {
    @Override public void onCreate(Bundle arguments) { super.onCreate(arguments); start(); }
    private String js(MainActivity activity, String script) throws Exception {
        CountDownLatch done = new CountDownLatch(1);
        AtomicReference<String> value = new AtomicReference<>();
        runOnMainSync(() -> activity.web.evaluateJavascript(script, result -> {value.set(result); done.countDown();}));
        if (!done.await(10, TimeUnit.SECONDS)) throw new AssertionError("JavaScript timed out");
        return value.get();
    }
    private void waitJs(MainActivity activity, String script) throws Exception {
        for (int attempt=0; attempt<120; attempt++) {
            if ("true".equals(js(activity, script))) return;
            Thread.sleep(500);
        }
        throw new AssertionError("Condition timed out: " + script + " URL=" + js(activity,"location.href") + " body=" + js(activity,"document.body.innerText") + " errors=" + js(activity,"JSON.stringify(window.runtimeErrors)"));
    }
    private void require(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
    @Override public void onStart() {
        Bundle status = new Bundle();
        status.putString("id", "InstrumentationTestRunner");
        status.putInt("numtests", 1); status.putInt("current", 1);
        status.putString("class", getClass().getName()); status.putString("test", "offlineImportAndFrames");
        sendStatus(1, status);
        MainActivity activity = null;
        try {
            require(getTargetContext().checkSelfPermission("android.permission.INTERNET") != android.content.pm.PackageManager.PERMISSION_GRANTED, "App must not have Internet permission");
            Intent launch = new Intent(getTargetContext(), MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            activity = (MainActivity)startActivitySync(launch);
            final MainActivity app = activity;
            waitJs(app, "typeof Android!=='undefined' && !!document.getElementById('import')");
            // ARM code: set mode 3, fill VRAM red, then loop. No BIOS/logo/game assets.
            byte[] rom = new byte[256];
            ByteBuffer words=ByteBuffer.wrap(rom).order(ByteOrder.LITTLE_ENDIAN);
            words.putInt(0, 0xea00002e); // branch to 0xc0
            rom[0xb2]=(byte)0x96;
            int[] program={0xe3a00301,0xe3a01b01,0xe2811003,0xe1c010b0,0xe3a00406,0xe3a0101f,0xe3a02c96,0xe0c010b2,0xe2522001,0x1afffffc,0xeafffffe};
            for(int n=0;n<program.length;n++)words.putInt(0xc0+n*4,program[n]);
            File file = new File(getTargetContext().getCacheDir(), "offline-smoke.gba");
            try(FileOutputStream out=new FileOutputStream(file)){out.write(rom);}
            // Exercise exactly the import callback used by ACTION_OPEN_DOCUMENT.
            runOnMainSync(() -> app.onActivityResult(12,Activity.RESULT_OK,new Intent().setData(Uri.fromFile(file))));
            waitJs(app, "location.pathname.endsWith('/play.html') && !!window.EJS_emulator");
            waitJs(app, "!!document.querySelector('.ejs_start_button')");
            js(app, "document.querySelector('.ejs_start_button').click();true");
            waitJs(app, "!!(window.EJS_emulator && EJS_emulator.started && EJS_emulator.gameManager && EJS_emulator.gameManager.getFrameNum()>120)");
            require("true".equals(js(app,"document.querySelector('canvas').width>=240")),"GBA canvas missing");
            android.graphics.Bitmap screenshot = getUiAutomation().takeScreenshot();
            require(screenshot != null, "Android screenshot unavailable");
            try(FileOutputStream image=new FileOutputStream(new File(getTargetContext().getCacheDir(),"smoke.png"))) {
                screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,image);
            }
            int redPixels=0;
            for(int y=0;y<screenshot.getHeight();y+=3)for(int x=0;x<screenshot.getWidth();x+=3){
                int color=screenshot.getPixel(x,y);
                if(android.graphics.Color.red(color)>200 && android.graphics.Color.green(color)<80 && android.graphics.Color.blue(color)<80)redPixels++;
            }
            require(redPixels>1000,"Expected red homebrew framebuffer, found " + redPixels + " red pixels");
            screenshot.recycle();
            require("true".equals(js(app,"document.querySelectorAll('#console button.pad').length===7 && document.querySelector('#console .action').getBoundingClientRect().width>30")),"Touch controls missing");
            require("true".equals(js(app,"document.getElementById('settings-toggle').click(); !document.getElementById('settings-panel').hidden && !!document.getElementById('pref-volume')")), "Profile panel failed");
            js(app,"toggleSettings(false)");
            // Exercise production native save persistence and restore before the core starts.
            js(app,"window.persistDone=false;EJS_emulator.gameManager.FS.writeFile('/data/saves/offline-test',new Uint8Array([11,22,33]));EJS_emulator.gameManager.FS.syncfs(false,()=>window.persistDone=true);true");
            waitJs(app,"window.persistDone===true");
            require("true".equals(js(app,"saveGame(false)")),"Native save write failed");
            require("true".equals(js(app,"!!JSON.parse(Android.loadFiles())['offline-test']")),"Native save read failed");
            // Invalid imports must not destroy an existing valid ROM.
            File bad = new File(getTargetContext().getCacheDir(),"invalid.gba");
            try(FileOutputStream out=new FileOutputStream(bad)){out.write(new byte[256]);}
            runOnMainSync(()->app.importRom(Uri.fromFile(bad)));
            Thread.sleep(1500);
            try(FileInputStream previous=new FileInputStream(new File(getTargetContext().getFilesDir(),"game.gba"))){
                byte[] original=new byte[256];require(previous.read(original)==256 && original[0xb2]==(byte)0x96,"Invalid import damaged previous ROM");
            }
            js(app,"leaveGame();true");
            waitJs(app,"!!document.getElementById('continue') && !document.getElementById('saved').hidden");
            js(app,"Android.play();true");
            waitJs(app,"!!document.querySelector('.ejs_start_button')");
            js(app,"document.querySelector('.ejs_start_button').click();true");
            waitJs(app,"!!(window.EJS_emulator && EJS_emulator.started && EJS_emulator.gameManager && EJS_emulator.gameManager.getFrameNum()>60)");
            require("true".equals(js(app,"Array.from(EJS_emulator.gameManager.FS.readFile('/data/saves/offline-test')).join(',')==='11,22,33'")),"Save persistence failed");
            status.putString("stream","\nOffline ROM import, >120 emulated frames, reopen, invalid import rollback and persistent saves passed.\n");
            sendStatus(0,status);
            Bundle result=new Bundle();result.putString("stream","\nOK (1 test)\n"); finish(Activity.RESULT_OK,result);
        } catch (Throwable failure) {
            status.putString("stack",android.util.Log.getStackTraceString(failure)); sendStatus(-2,status);
            Bundle result=new Bundle(); result.putString("shortMsg",failure.toString()); result.putString("stream","\nFAILURES!!!\n"+android.util.Log.getStackTraceString(failure)); finish(Activity.RESULT_OK,result);
        } finally { if(activity!=null){final Activity app=activity;runOnMainSync(app::finish);} }
    }
}
