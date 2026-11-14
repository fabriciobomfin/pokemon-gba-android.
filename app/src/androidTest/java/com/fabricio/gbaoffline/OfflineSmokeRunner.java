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
            require("true".equals(js(app,"document.body.classList.contains('focus') && !document.getElementById('app-header').offsetHeight && document.getElementById('speed-button').offsetHeight>=40")), "Fullscreen and speed shortcut missing");
            require("true".equals(js(app,"var speedBtn=document.getElementById('speed-button'); speedBtn.click(); speedBtn.textContent==='2×' && document.getElementById('settings-panel').hidden")), "Direct speed change failed");
            require("true".equals(js(app,"document.getElementById('reopen').click(); !!document.querySelector('#reopen svg path') && document.querySelectorAll('.category summary svg path').length===5 && !document.getElementById('settings-panel').hidden && !document.querySelector('.category[open]')")), "Settings overview failed");
            require("true".equals(js(app,"document.querySelector('.category summary').click(); document.getElementById('pref-fit').offsetHeight>0 && !document.getElementById('pref-volume').offsetHeight")), "Video category failed");
            require("true".equals(js(app,"Array.from(document.querySelectorAll('#quick button')).map(b=>b.id).join()==='save-point,load-point,all-points' && el('load-point').disabled")), "Quick menu order failed");
            require("true".equals(js(app,"[[0,-.4,4],[0,.4,5],[-.4,0,6],[.4,0,7]].every(([x,y,d])=>directionCodes(x,y,false).join()===String(d)) && directionCodes(.32,.45,false).length===1 && directionCodes(.4,.4,true).length===2")), "Directional mapping failed");
            require("true".equals(js(app,"!EJS_emulator.elements.menuToggle.isConnected && !el('pref-accent') && el('pref-theme').options.length>=10")),"Legacy menu or split themes still present");
            js(app,"settings.shortcut=true;settings.saveShortcut=true;apply();toggleSettings(false)");
            require("true".equals(js(app,"!el('save-shortcut').hidden && !!el('save-shortcut').querySelector('svg') && !!el('restore-shortcut').querySelector('svg') && getComputedStyle(el('reopen')).display==='grid'")),"Save/restore shortcuts or centered icons missing");
            js(app,"window.originalA=buttons.A.getBoundingClientRect().width;window.originalB=buttons.B.getBoundingClientRect().width;startEditing();selectControl('A');resizeInput.value=160;resizeInput.dispatchEvent(new Event('input'));finishEditing()");
            require("true".equals(js(app,"Math.abs(buttons.A.getBoundingClientRect().width/originalA-1.6)<.05 && Math.abs(buttons.B.getBoundingClientRect().width-originalB)<1 && read(profileKey,{}).sizes.A===160")),"Individual button size or persistence failed");
            js(app,"startEditing();selectControl('reopen');resizeInput.value=130;resizeInput.dispatchEvent(new Event('input'));finishEditing();settings.theme='dourado';apply();persist()");
            require("true".equals(js(app,"document.documentElement.dataset.theme==='dourado' && read('fire-game-theme','')==='dourado' && getComputedStyle(buttons.A).backgroundColor===getComputedStyle(el('save-point')).backgroundColor && scaleFor('reopen')===1.3")),"Unified theme or shortcut scaling failed");
            js(app,"settings.screen='full';settings.fit='fill';settings.orientation='horizontal';apply();persist()");
            waitJs(app,"innerWidth>innerHeight && Math.abs(el('game').getBoundingClientRect().width-innerWidth)<2 && Math.abs(el('game').getBoundingClientRect().height-innerHeight)<2");
            Thread.sleep(700);
            android.graphics.Bitmap wide=getUiAutomation().takeScreenshot();
            try(FileOutputStream image=new FileOutputStream(new File(getTargetContext().getCacheDir(),"controls-wide.png"))){wide.compress(android.graphics.Bitmap.CompressFormat.PNG,100,image);}wide.recycle();
            js(app,"settings.screen='console';settings.fit='contain';settings.orientation='vertical';apply();persist();el('save-shortcut').click()");
            // The new on-screen save shortcut creates a checkpoint.

            waitJs(app,"!el('load-point').disabled && el('checkpoint-status').textContent.includes('salvo')");
            js(app,"el('all-points').click()");
            waitJs(app,"document.querySelector('#save-history img')?.naturalWidth>0");
            js(app,"historyBox.close();toggleSettings(false)");
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
            // Export via the production document callback, erase the local records, then import.
            js(app,"el('exp').click()");
            Thread.sleep(1500);
            getUiAutomation().executeShellCommand("input keyevent 4").close();
            File archive=new File(getTargetContext().getCacheDir(),"transfer.zip");
            runOnMainSync(()->app.onActivityResult(13,Activity.RESULT_OK,new Intent().setData(Uri.fromFile(archive))));
            for(int i=0;i<80&&archive.length()<100;i++)Thread.sleep(100);
            Thread.sleep(500);
            try(java.util.zip.ZipFile z=new java.util.zip.ZipFile(archive)){require(z.getEntry("point-0.json")!=null,"Checkpoint missing from export");}
            js(app,"window.erased=false;historyDb().then(db=>{const tx=db.transaction('history','readwrite');tx.objectStore('history').clear();tx.oncomplete=()=>{db.close();window.erased=true}})");
            waitJs(app,"window.erased");
            runOnMainSync(()->app.onActivityResult(14,Activity.RESULT_OK,new Intent().setData(Uri.fromFile(archive))));
            js(app,"window.recovered=false;window.checkRecovery=setInterval(async()=>{const db=await historyDb();const count=await query(db,s=>s.count());db.close();if(count){clearInterval(checkRecovery);window.recovered=true}},200)");
            waitJs(app,"window.recovered");
            js(app,"Android.play();true");
            waitJs(app,"!!(window.EJS_emulator && EJS_emulator.started && EJS_emulator.gameManager && EJS_emulator.gameManager.getFrameNum()>60)");
            require("true".equals(js(app,"Array.from(EJS_emulator.gameManager.FS.readFile('/data/saves/offline-test')).join(',')==='11,22,33'")),"Save persistence failed");
            require("true".equals(js(app,"settings.sizes.A===160 && settings.sizes.reopen===130 && settings.theme==='dourado' && !el('save-shortcut').hidden")),"Control sizes, shortcut or theme lost after reopening");
            waitJs(app,"!el('load-point').disabled");
            js(app,"el('reopen').click();el('load-point').click()");
            waitJs(app,"el('checkpoint-status').textContent==='Save restaurado.' && el('settings-panel').hidden");
            status.putString("stream","\nOffline ROM import, >120 emulated frames, reopen, invalid import rollback and persistent saves passed.\n");
            sendStatus(0,status);
            Bundle result=new Bundle();result.putString("stream","\nOK (1 test)\n"); finish(Activity.RESULT_OK,result);
        } catch (Throwable failure) {
            status.putString("stack",android.util.Log.getStackTraceString(failure)); sendStatus(-2,status);
            Bundle result=new Bundle(); result.putString("shortMsg",failure.toString()); result.putString("stream","\nFAILURES!!!\n"+android.util.Log.getStackTraceString(failure)); finish(Activity.RESULT_OK,result);
        } finally { if(activity!=null){final Activity app=activity;runOnMainSync(app::finish);} }
    }
}
