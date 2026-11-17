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

/** Tests a homebrew that writes cartridge SRAM on A and shows green after a cold boot. */
public class OfflineSmokeRunner extends Instrumentation {
    private boolean coldBoot;
    private String phase;
    @Override public void onCreate(Bundle arguments) {
        super.onCreate(arguments);
        phase=arguments.getString("phase","");
        coldBoot = "battery-reopen".equals(phase);
        start();
    }
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
    private void capture(String name) throws Exception {
        android.graphics.Bitmap bitmap=getUiAutomation().takeScreenshot();
        if(bitmap==null)throw new AssertionError("Screenshot unavailable");
        try(java.io.ByteArrayOutputStream data=new java.io.ByteArrayOutputStream()){
            if(!bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,data))throw new AssertionError("Screenshot encoding failed");
            try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getCacheDir(),name))){out.write(data.toByteArray());out.flush();out.getFD().sync();}
        }finally{bitmap.recycle();}
    }
    private void require(boolean valid, String message) { if (!valid) throw new AssertionError(message); }

    private int colorCount(android.graphics.Bitmap bitmap, int channel) {
        int count=0;
        for(int y=0;y<bitmap.getHeight();y+=3)for(int x=0;x<bitmap.getWidth();x+=3){
            int c=bitmap.getPixel(x,y),r=android.graphics.Color.red(c),g=android.graphics.Color.green(c),b=android.graphics.Color.blue(c);
            if(channel==0 && r>180 && g<70 && b<70 || channel==1 && g>180 && r<70 && b<70 || channel==2 && b>180 && r<70 && g<70)count++;
        }
        return count;
    }
    private void expectDsColors(int first,int second) throws Exception {
        int countFirst=0,countSecond=0;
        for(int attempt=0;attempt<30;attempt++){
            android.graphics.Bitmap bitmap=getUiAutomation().takeScreenshot();
            try{countFirst=colorCount(bitmap,first);countSecond=colorCount(bitmap,second);}finally{bitmap.recycle();}
            if(countFirst>600 && countSecond>600)return;
            Thread.sleep(200);
        }
        capture("ds-touch-failure.png");
        throw new AssertionError("DS screen colors missing: "+countFirst+","+countSecond);
    }
    private void touchDs(MainActivity app, boolean portrait) throws Exception {
        String result=js(app,"JSON.stringify({x:(el('game').getBoundingClientRect().left+el('game').clientWidth*"+(portrait?".5":".75")+")*devicePixelRatio,y:(el('game').getBoundingClientRect().top+el('game').clientHeight*"+(portrait?".75":".5")+")*devicePixelRatio})");
        org.json.JSONObject point=new org.json.JSONObject(new org.json.JSONArray("["+result+"]").getString(0));
        int[] origin=new int[2];runOnMainSync(()->app.web.getLocationOnScreen(origin));
        float x=(float)point.getDouble("x")+origin[0],y=(float)point.getDouble("y")+origin[1];
        long down=android.os.SystemClock.uptimeMillis();
        android.view.MotionEvent press=android.view.MotionEvent.obtain(down,down,0,x,y,0);
        press.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        getUiAutomation().injectInputEvent(press,true);press.recycle();
        Thread.sleep(600);
        expectDsColors(0,1);
        android.view.MotionEvent release=android.view.MotionEvent.obtain(down,android.os.SystemClock.uptimeMillis(),1,x,y,0);
        release.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);
        getUiAutomation().injectInputEvent(release,true);release.recycle();
        Thread.sleep(300);
        expectDsColors(0,2);
    }
    private void touchDsWindow(MainActivity app) throws Exception {
        String result=js(app,"JSON.stringify({x:(dsPreview.getBoundingClientRect().left+dsPreview.clientWidth*.5)*devicePixelRatio,y:(dsPreview.getBoundingClientRect().top+dsPreview.clientHeight*.5)*devicePixelRatio})");
        org.json.JSONObject point=new org.json.JSONObject(new org.json.JSONArray("["+result+"]").getString(0));
        int[] origin=new int[2];runOnMainSync(()->app.web.getLocationOnScreen(origin));
        float x=(float)point.getDouble("x")+origin[0],y=(float)point.getDouble("y")+origin[1];
        long now=android.os.SystemClock.uptimeMillis();
        android.view.MotionEvent event=android.view.MotionEvent.obtain(now,now,0,x,y,0);event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);getUiAutomation().injectInputEvent(event,true);event.recycle();
        Thread.sleep(800);android.util.Log.i("FireGameDSTouch",js(app,"JSON.stringify({touch:window.dsLastTouch,errors:window.runtimeErrors})"));expectDsColors(0,1);
        event=android.view.MotionEvent.obtain(now,android.os.SystemClock.uptimeMillis(),1,x,y,0);event.setSource(android.view.InputDevice.SOURCE_TOUCHSCREEN);getUiAutomation().injectInputEvent(event,true);event.recycle();
        Thread.sleep(500);android.util.Log.i("FireGameDSRelease",js(app,"JSON.stringify({touch:window.dsLastTouch,pointer:dsPointer,paused:EJS_emulator.paused,frame:EJS_emulator.gameManager.getFrameNum(),errors:window.runtimeErrors})"));expectDsColors(0,2);
    }
    private void testDsDisplay(MainActivity app) throws Exception {
        waitJs(app,"dsLayout==='top only' && settings.screen==='full'");Thread.sleep(700);
        android.graphics.Bitmap bitmap=getUiAutomation().takeScreenshot();
        try{require(colorCount(bitmap,0)>10000 && colorCount(bitmap,2)<100,"Single screen did not hide lower screen");}finally{bitmap.recycle();}
        capture("ds-single.png");
        js(app,"el('ds-swap').click()");waitJs(app,"dsLayout==='bottom only'");Thread.sleep(700);
        bitmap=getUiAutomation().takeScreenshot();try{require(colorCount(bitmap,2)>10000 && colorCount(bitmap,0)<100,"Swap did not show lower screen");}finally{bitmap.recycle();}
        js(app,"el('ds-swap').click();el('ds-popup').click()");waitJs(app,"dsLayout==='top/bottom' && !dsWindow.hidden");Thread.sleep(1000);
        expectDsColors(0,2);capture("ds-overlay-wide.png");touchDsWindow(app);
        js(app,"settings.orientation='vertical';apply();persist()");waitJs(app,"innerHeight>innerWidth");Thread.sleep(1000);
        expectDsColors(0,2);capture("ds-overlay-portrait.png");touchDsWindow(app);
        js(app,"settings.dsWindowPosition=[-20,140];settings.dsWindowSize=40;apply();persist()");
        require("true".equals(js(app,"dsWindow.getBoundingClientRect().left>=0 && dsWindow.getBoundingClientRect().bottom<=innerHeight")),"Floating DS window escaped viewport");
        js(app,"dsWindow.querySelector('button').click()");waitJs(app,"dsLayout==='top only' && dsWindow.hidden");
        js(app,"settings.dsMode='dual';settings.orientation='horizontal';apply();persist()");waitJs(app,"innerWidth>innerHeight && dsLayout==='left/right'");
    }
    private void testDs(MainActivity app) throws Exception {
        if("ds-reopen".equals(phase)){
            js(app,"window.cleaned=false;var req=indexedDB.deleteDatabase('/data/saves');req.onsuccess=()=>window.cleaned=true");
            waitJs(app,"window.cleaned");
            js(app,"Android.play()");
            waitJs(app,"window.EJS_emulator?.started && EJS_emulator.gameManager.getFrameNum()>120");
            Thread.sleep(600);expectDsColors(1,2);
            capture("ds-reopen.png");
            return;
        }
        File file=new File(getTargetContext().getCacheDir(),"ds-test.nds");
        try(FileOutputStream out=new FileOutputStream(file)){out.write(NdsTestProgram.create());}
        runOnMainSync(()->app.onActivityResult(12,Activity.RESULT_OK,new Intent().setData(Uri.fromFile(file))));
        waitJs(app,"window.EJS_emulator?.started && EJS_emulator.gameManager.getFrameNum()>120");
        require("true".equals(js(app,"isDS && EJS_emulator.getCore()==='desmume2015' && innerWidth>innerHeight && !!buttons.X && !!buttons.Y && !!buttons.Mic")),"DS core, layout or controls missing");
        testDsDisplay(app);
        Thread.sleep(600);expectDsColors(0,2);capture("ds-wide.png");touchDs(app,false);
        js(app,"settings.orientation='vertical';apply();persist()");
        waitJs(app,"innerHeight>innerWidth && dsLayout==='top/bottom'");
        Thread.sleep(800);expectDsColors(0,2);touchDs(app,true);capture("ds-portrait.png");
        js(app,"useCheckpoint(true)");
        waitJs(app,"!checkpointBusy && hasCheckpoint");
        js(app,"EJS_emulator.gameManager.simulateInput(0,8,1)");Thread.sleep(400);
        js(app,"EJS_emulator.gameManager.simulateInput(0,8,0)");
        waitJs(app,"Object.entries(JSON.parse(Android.loadFiles())).some(([key,value])=>key.endsWith('.dsv')&&atob(value).charCodeAt(0)===90)");
        js(app,"leaveGame()");
        waitJs(app,"!!el('nds-count') && innerHeight>innerWidth");
        require("true".equals(js(app,"el('nds-count').textContent==='1 jogo' && el('game-count').textContent==='1 jogo'")),"Mixed-console library counts incorrect");
        require("true".equals(js(app,"el('nds-category').click();el('list').querySelectorAll('article').length===1 && el('list').innerText.includes('Nintendo DS')")),"DS category failed");
        // Full-size DS checkpoints must survive the same export used by the app.
        js(app,"el('exp').click()");Thread.sleep(2500);
        getUiAutomation().executeShellCommand("input keyevent 4").close();
        File archive=new File(getTargetContext().getCacheDir(),"ds-transfer.zip");
        runOnMainSync(()->app.onActivityResult(13,Activity.RESULT_OK,new Intent().setData(Uri.fromFile(archive))));
        for(int i=0;i<100;i++){
            try(java.util.zip.ZipFile zip=new java.util.zip.ZipFile(archive)){
                require(zip.getEntry("point-0.json")!=null,"DS checkpoint not exported");
                break;
            }catch(java.io.IOException error){if(i==99)throw error;Thread.sleep(200);}
        }
    }
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
            waitJs(app, "typeof Android!=='undefined' && !!document.getElementById('import') && innerHeight>innerWidth && typeof read==='function' && typeof el==='function'");
            if(phase.startsWith("upgrade-")){
                if("upgrade-seed".equals(phase)){
                    String preserved=js(app,"JSON.stringify({library:JSON.parse(Android.library()),theme:read('fire-game-theme',null),profiles:Object.keys(localStorage).filter(k=>k.startsWith('pocket:')).sort().map(k=>[k,localStorage.getItem(k)])})");
                    waitJs(app,"Object.keys(JSON.parse(Android.library())).length===2 && read('fire-game-theme',null)==='dourado'");
                    getTargetContext().getSharedPreferences("upgrade-test",0).edit()
                        .putString("preserved",preserved).putInt("version",getTargetContext().getPackageManager().getPackageInfo(getTargetContext().getPackageName(),0).versionCode).commit();
                }else{
                    int previous=getTargetContext().getSharedPreferences("upgrade-test",0).getInt("version",-1);
                    require(previous>0 && getTargetContext().getPackageManager().getPackageInfo(getTargetContext().getPackageName(),0).versionCode>previous,"Version did not increase");
                    String preserved=js(app,"JSON.stringify({library:JSON.parse(Android.library()),theme:read('fire-game-theme',null),profiles:Object.keys(localStorage).filter(k=>k.startsWith('pocket:')).sort().map(k=>[k,localStorage.getItem(k)])})");
                    require(preserved.equals(getTargetContext().getSharedPreferences("upgrade-test",0).getString("preserved","")),"Existing preferences or ROM library changed during update: "+preserved);
                    phase="ds-reopen";testDs(app);
                    waitJs(app,"!el('load-point').disabled");
                }
                Bundle result=new Bundle();result.putString("stream","\nOK (1 test)\n");finish(Activity.RESULT_OK,result);return;
            }
            if(phase.startsWith("ds")){
                testDs(app);
                status.putString("stream","DS offline dual-screen, touch, cartridge and checkpoint tests passed.");
                sendStatus(0,status);
                Bundle result=new Bundle();result.putString("stream","\nOK (1 test)\n");finish(Activity.RESULT_OK,result);
                return;
            }
            if (coldBoot) {
                // Exclude the browser cache as a source: the Android cartridge file must restore it.
                js(app,"window.batteryCacheCleared=false;var request=indexedDB.deleteDatabase('/data/saves');request.onsuccess=()=>window.batteryCacheCleared=true");
                waitJs(app,"window.batteryCacheCleared");
                js(app,"Android.play()");
                waitJs(app,"window.EJS_emulator?.started && EJS_emulator.gameManager.getFrameNum()>120");
                require("true".equals(js(app,"innerHeight>innerWidth && settings.orientation==='vertical'")),"Reopened game must restore portrait profile");
                js(app,"EJS_emulator.pause()");
                Thread.sleep(1000);
                android.graphics.Bitmap image=getUiAutomation().takeScreenshot();
                int green=0;
                for(int y=0;y<image.getHeight();y+=3)for(int x=0;x<image.getWidth();x+=3){
                    int color=image.getPixel(x,y);
                    if(android.graphics.Color.green(color)>180 && android.graphics.Color.red(color)<70 && android.graphics.Color.blue(color)<70)green++;
                }
                image.recycle();
                require(green>1000,"The ROM did not read its saved cartridge SRAM after process death");
                require("true".equals(js(app,"EJS_emulator.gameManager.getSaveFile(false)?.[0]===90")),"Cartridge bytes lost after process death");
                capture("battery-reopen.png");
                status.putString("stream","\nCartridge SRAM recovered by the ROM after force-stop, without loading a checkpoint.\n");
                sendStatus(0,status);
                Bundle result=new Bundle();result.putString("stream","\nOK (1 test)\n");finish(Activity.RESULT_OK,result);
                return;
            }
            waitJs(app,"document.querySelectorAll('#gallery button').length===14");
            require("true".equals(js(app,"openPage('categories'); !el('categories-page').hidden && el('game-count').textContent==='0 jogos'")),"Empty category count");
            require("true".equals(js(app,"el('gba-category').click(); !el('home-page').hidden && el('recent').hidden")),"Category navigation failed");
            require("true".equals(js(app,"document.querySelector('[data-page=themes]').click(); Array.from(el('gallery').children).every(b=>{b.click();return b.getAttribute('aria-pressed')==='true' && document.documentElement.dataset.theme===b.dataset.id && read('fire-game-theme','')===b.dataset.id && (!themes[b.dataset.id].pattern || getComputedStyle(b.firstChild).backgroundImage.includes('gradient'))})")),"Theme gallery selection failed");
            js(app,"document.querySelector('[data-id=ciano]').click();openPage('home')");
            // ARM homebrew draws red on a new cartridge and green when SRAM[0] is 0x5a.
            // Holding A writes that byte through the GBA memory bus.
            byte[] rom = new byte[512];
            ByteBuffer words=ByteBuffer.wrap(rom).order(ByteOrder.LITTLE_ENDIAN);
            words.putInt(0, 0xea00002e); // branch to 0xc0
            rom[0xb2]=(byte)0x96;
            int[] program={0xe3a00301,0xe3a01b01,0xe2811003,0xe1c010b0,
                0xe3a0540e,0xe5d56000,0xe356005a,0xe3a0101f,0x03a01e3e,
                0xe3a00406,0xe3a02c96,0xe0c010b2,0xe2522001,0x1afffffc,
                0xe3a03301,0xe2833e13,0xe1d340b0,0xe3140001,0x1afffffc,
                0xe3a0605a,0xe5c56000,0xeafffff9};
            for(int n=0;n<program.length;n++)words.putInt(0xc0+n*4,program[n]);
            System.arraycopy("SRAM_V113".getBytes("US-ASCII"),0,rom,400,9);
            File file = new File(getTargetContext().getCacheDir(), "offline-smoke.gba");
            try(FileOutputStream out=new FileOutputStream(file)){out.write(rom);}
            // Exercise exactly the import callback used by ACTION_OPEN_DOCUMENT.
            runOnMainSync(() -> app.onActivityResult(12,Activity.RESULT_OK,new Intent().setData(Uri.fromFile(file))));
            waitJs(app, "location.pathname.endsWith('/play.html') && !!window.EJS_emulator");
            waitJs(app, "!!(window.EJS_emulator && EJS_emulator.started && EJS_emulator.gameManager && EJS_emulator.gameManager.getFrameNum()>120)");
            require("true".equals(js(app,"settings.theme==='ciano' && document.querySelector('canvas').width>=240")),"GBA canvas missing");
            android.graphics.Bitmap screenshot = null;
            for(int attempt=0;attempt<40;attempt++){
                if(screenshot!=null)screenshot.recycle();
                screenshot=getUiAutomation().takeScreenshot();
                if(screenshot!=null && colorCount(screenshot,0)>1000)break;
                Thread.sleep(250);
            }
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
            js(app,"EJS_emulator.gameManager.simulateInput(0,8,1)");
            Thread.sleep(400);
            js(app,"EJS_emulator.gameManager.simulateInput(0,8,0)");
            Thread.sleep(1500);
            waitJs(app,"Object.values(JSON.parse(Android.loadFiles())).some(v=>atob(v).charCodeAt(0)===90)");
            android.util.Log.i("FireGameBattery",js(app,"JSON.stringify({path:EJS_emulator.gameManager.getSaveFilePath(),nativeNames:Object.keys(JSON.parse(Android.loadFiles()))})"));
            require("true".equals(js(app,"innerWidth>innerHeight && settings.orientation==='horizontal'")),"Initial horizontal orientation failed");
            require("false".equals(js(app,"Android.saveFiles(JSON.stringify({'../escape':'Wg=='}))")),"Save traversal accepted");
            require("true".equals(js(app,"EJS_emulator.gameManager.getSaveFile(false)?.[0]===90")),"ROM cartridge write not flushed to battery file");
            require("true".equals(js(app,"Object.values(JSON.parse(Android.loadFiles())).some(v=>atob(v).charCodeAt(0)===90)")),"Real cartridge save missing from native storage");
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
            js(app,"window.originalA=buttons.A.getBoundingClientRect().width;window.originalB=buttons.B.getBoundingClientRect().width;startEditing();selectControl('A');resizeInput.value=160;resizeInput.dispatchEvent(new Event('input'))");
            js(app,"EJS_emulator.pause()");Thread.sleep(1400);capture("editor.png");js(app,"finishEditing();EJS_emulator.play()");
            require("true".equals(js(app,"Math.abs(buttons.A.getBoundingClientRect().width/originalA-1.6)<.05 && Math.abs(buttons.B.getBoundingClientRect().width-originalB)<1 && read(profileKey,{}).sizes.A===160")),"Individual button size or persistence failed");
            js(app,"startEditing();selectControl('reopen');resizeInput.value=130;resizeInput.dispatchEvent(new Event('input'));finishEditing();settings.theme='dourado';apply();persist()");
            require("true".equals(js(app,"document.documentElement.dataset.theme==='dourado' && read('fire-game-theme','')==='dourado' && getComputedStyle(buttons.A).backgroundColor===getComputedStyle(el('save-point')).backgroundColor && scaleFor('reopen')===1.3")),"Unified theme or shortcut scaling failed");
            require("true".equals(js(app,"el('control-opacity').value=35;el('control-opacity').dispatchEvent(new Event('input'));settings.contrast=true;apply();Math.abs(parseFloat(getComputedStyle(buttons.A).opacity)-.35)<.01 && Math.abs(parseFloat(getComputedStyle(el('reopen')).opacity)-.35)<.01")),"Control opacity ignored");
            js(app,"settings.contrast=false;apply()");
            js(app,"settings.screen='console';settings.fit='immersive';settings.orientation='horizontal';apply();persist()");
            waitJs(app,"innerWidth>innerHeight && Math.abs(el('game').getBoundingClientRect().width-innerWidth)<2 && Math.abs(el('game').getBoundingClientRect().height-innerHeight)<2");
            Thread.sleep(1200);js(app,"window.layoutFrame=EJS_emulator.gameManager.getFrameNum()");
            waitJs(app,"EJS_emulator.gameManager.getFrameNum()>window.layoutFrame+3");
            js(app,"EJS_emulator.pause();updateCanvasCrop()");Thread.sleep(300);
            android.util.Log.i("FireGameLayout",js(app,"JSON.stringify({window:[innerWidth,innerHeight],game:el('game').getBoundingClientRect().toJSON(),canvas:EJS_emulator.canvas.getBoundingClientRect().toJSON(),buffer:[EJS_emulator.canvas.width,EJS_emulator.canvas.height],aspect:EJS_emulator.gameManager.getVideoDimensions('aspect'),fit:document.body.dataset.fit,screen:document.body.dataset.screen})"));
            capture("controls-wide.png");
            require("true".equals(js(app,"getComputedStyle(EJS_emulator.canvas).objectFit==='contain' && settings.fit==='immersive' && document.body.dataset.screen==='full'")),"Fullscreen must preserve complete image");
            js(app,"EJS_emulator.play()");
            js(app,"settings.screen='console';settings.fit='contain';settings.orientation='vertical';apply();persist();el('save-shortcut').click()");
            waitJs(app,"innerHeight>innerWidth && Math.abs(el('game').getBoundingClientRect().width/el('game').getBoundingClientRect().height-1.5)<.05");
            require("true".equals(js(app,"buttons.A.getBoundingClientRect().top>el('game').getBoundingClientRect().bottom && buttons.D.getBoundingClientRect().top>el('game').getBoundingClientRect().bottom && el('reopen').getBoundingClientRect().top>=el('game').getBoundingClientRect().bottom")),"Portrait controls overlap the game");
            js(app,"EJS_emulator.pause()");Thread.sleep(1000);capture("portrait.png");js(app,"EJS_emulator.play()");
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
            require("true".equals(js(app,"el('game-count').textContent==='1 jogo' && !el('recent').hidden && el('recent-games').children.length===1 && document.querySelector('[data-id=dourado]').getAttribute('aria-pressed')==='true'")),"Library or theme after returning failed");
            require("true".equals(js(app,"el('search').value='impossivel';render();!el('list').querySelector('article') && (el('gba-category').click(),el('list').querySelectorAll('article').length===1)")),"Search or category reset failed");
            // Exercise Android's file chooser callback and WebView image decoding.
            android.content.ContentValues imageValues = new android.content.ContentValues();
            imageValues.put(android.provider.MediaStore.Images.Media.DISPLAY_NAME,"firegame-cover-test.png");
            imageValues.put(android.provider.MediaStore.Images.Media.MIME_TYPE,"image/png");
            Uri coverUri = getTargetContext().getContentResolver().insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI,imageValues);
            require(coverUri != null,"Cannot create cover test image");
            android.graphics.Bitmap coverBitmap=android.graphics.Bitmap.createBitmap(48,64,android.graphics.Bitmap.Config.ARGB_8888);
            coverBitmap.eraseColor(android.graphics.Color.CYAN);
            try(java.io.OutputStream out=getTargetContext().getContentResolver().openOutputStream(coverUri)){coverBitmap.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out);}
            coverBitmap.recycle();
            java.util.concurrent.atomic.AtomicBoolean chooserOpened=new java.util.concurrent.atomic.AtomicBoolean();
            ActivityMonitor coverMonitor=new ActivityMonitor(){
                @Override public ActivityResult onStartActivity(Intent intent){
                    if(Intent.ACTION_OPEN_DOCUMENT.equals(intent.getAction()) && "image/*".equals(intent.getType())){
                        chooserOpened.set(true);return new ActivityResult(Activity.RESULT_OK,new Intent().setData(coverUri));
                    }
                    return null;
                }
            };
            addMonitor(coverMonitor);
            js(app,"edit(info.id);document.querySelector('#detail input[type=file]').click()");
            for(int i=0;i<50&&!chooserOpened.get();i++)Thread.sleep(100);
            require(chooserOpened.get(),"Cover chooser did not open");
            Thread.sleep(1500);
            js(app,"Array.from(detail.querySelectorAll('button')).find(b=>b.textContent==='Salvar').click()");
            waitJs(app,"!!gameMeta(info.id).cover && !!el('list').querySelector('.game-cover img')");
            capture("library-covers.png");
            removeMonitor(coverMonitor);
            getTargetContext().getContentResolver().delete(coverUri,null,null);
            // Export via the production document callback, erase the local records, then import.
            js(app,"el('exp').click()");
            Thread.sleep(1500);
            getUiAutomation().executeShellCommand("input keyevent 4").close();
            File archive=new File(getTargetContext().getCacheDir(),"transfer.zip");
            runOnMainSync(()->app.onActivityResult(13,Activity.RESULT_OK,new Intent().setData(Uri.fromFile(archive))));
            for(int i=0;i<80&&archive.length()<100;i++)Thread.sleep(100);
            Thread.sleep(500);
            try(java.util.zip.ZipFile z=new java.util.zip.ZipFile(archive)){require(z.getEntry("point-0.json")!=null,"Checkpoint missing from export");
                try(java.io.InputStream input=z.getInputStream(z.getEntry("backup.json"))){
                    java.io.ByteArrayOutputStream bytes=new java.io.ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
                    while((n=input.read(buffer))!=-1)bytes.write(buffer,0,n);
                    org.json.JSONObject saves=new org.json.JSONObject(bytes.toString("UTF-8")).getJSONObject("saves");
                    String name="mGBA/"+new org.json.JSONObject(app.new Bridge().info()).getString("id")+".srm";
                    require(android.util.Base64.decode(saves.getString(name),0)[0]==90,"Cartridge save missing from exported backup");
                }}
            js(app,"window.erased=false;historyDb().then(db=>{const tx=db.transaction('history','readwrite');tx.objectStore('history').clear();tx.oncomplete=()=>{db.close();window.erased=true}})");
            waitJs(app,"window.erased");
            js(app,"var erasedFiles=JSON.parse(Android.loadFiles());for(const key of Object.keys(erasedFiles)){if(key.endsWith('.srm'))erasedFiles[key]=btoa(String.fromCharCode(0).repeat(atob(erasedFiles[key]).length));}Android.saveFiles(JSON.stringify(erasedFiles))");
            runOnMainSync(()->app.onActivityResult(14,Activity.RESULT_OK,new Intent().setData(Uri.fromFile(archive))));
            js(app,"window.recovered=false;window.checkRecovery=setInterval(async()=>{const db=await historyDb();const count=await query(db,s=>s.count());db.close();if(count){clearInterval(checkRecovery);window.recovered=true}},200)");
            waitJs(app,"window.recovered");
            require("true".equals(js(app,"Object.entries(JSON.parse(Android.loadFiles())).some(([key,value])=>key.endsWith('.srm')&&atob(value).charCodeAt(0)===90)")),"Imported cartridge backup did not restore the saved byte");
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
