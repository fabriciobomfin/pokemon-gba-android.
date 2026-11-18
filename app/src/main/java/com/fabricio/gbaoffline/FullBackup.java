package com.fabricio.gbaoffline;

import java.io.*;
import java.util.*;
import java.util.zip.*;
import org.json.*;

/** Versioned, ROM-free backup. Decode and validate the whole archive before touching live saves. */
final class FullBackup {
    private final MainActivity app;
    private final File stage, previous, next, marker;
    private JSONObject manifest;
    private JSONObject cartridges;
    private static final int MAX_FILE = 64 * 1024 * 1024;
    private static final long MAX_TOTAL = 256L * 1024 * 1024;
    FullBackup(MainActivity app) {
        this.app=app;
        stage=new File(app.getCacheDir(),"full-backup-stage");
        previous=new File(app.getFilesDir(),"saves-before-full-backup");
        next=new File(app.getFilesDir(),"saves-next-full-backup");
        marker=new File(app.getFilesDir(),"full-backup-pending");
        if(marker.exists()){
            String token="";
            try(InputStream in=new FileInputStream(marker)){token=new String(read(in,200),"UTF-8");}catch(IOException ignored){}
            if(!token.isEmpty()&&token.equals(committed())){
                try{remove(previous);remove(next);remove(marker);}catch(IOException ignored){}
            }else rollback();
        }
    }
    String committed(){return app.getPreferences(android.app.Activity.MODE_PRIVATE).getString("fullBackupCommitted", "");}
    private File live(){return new File(app.getFilesDir(),"saves");}
    static boolean id(String value){return value.matches("[a-f0-9]{64}");}
    static boolean path(String name){
        if(name.length()>320 || name.split("/",-1).length>4)return false;
        for(String part:name.split("/",-1))if(!part.matches("[a-zA-Z0-9._ -]{1,160}")||part.equals(".")||part.equals(".."))return false;
        return true;
    }
    private static void remove(File file) throws IOException {
        if(!file.exists())return;
        File[] children=file.listFiles();if(children!=null)for(File child:children)remove(child);
        if(!file.delete())throw new IOException("Não foi possível limpar os arquivos temporários.");
    }
    private static void copy(File source,File destination) throws IOException {
        if(!source.exists())return;
        if(source.isDirectory()){
            if(!destination.isDirectory()&&!destination.mkdirs())throw new IOException("Sem espaço para preparar a restauração.");
            File[] children=source.listFiles();if(children==null)throw new IOException("Falha ao ler os saves.");
            for(File child:children)copy(child,new File(destination,child.getName()));
        }else{File parent=destination.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("Falha ao preparar pasta.");
            try(InputStream in=new FileInputStream(source);OutputStream out=new FileOutputStream(destination)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}
        }
    }
    private static byte[] read(InputStream in,int limit) throws IOException {
        if(in==null)throw new IOException("Arquivo indisponível.");
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[65536];int n;
        while((n=in.read(b))!=-1){if(out.size()+(long)n>limit)throw new IOException("Backup excede o limite de tamanho.");out.write(b,0,n);}return out.toByteArray();
    }
    private static void write(File file,byte[] bytes) throws IOException {
        File parent=file.getParentFile();if(!parent.isDirectory()&&!parent.mkdirs())throw new IOException("Sem espaço disponível.");
        try(FileOutputStream out=new FileOutputStream(file)){out.write(bytes);out.getFD().sync();}
    }
    private void collect(File directory,String prefix,JSONObject files) throws Exception {
        File[] entries=directory.listFiles();if(entries==null)return;
        for(File file:entries){String name=prefix+file.getName();if(!path(name)||name.endsWith(".new")||name.endsWith(".bak"))continue;
            if(file.isDirectory())collect(file,name+"/",files);
            else try(InputStream in=new FileInputStream(file)){files.put(name,android.util.Base64.encodeToString(read(in,16*1024*1024),android.util.Base64.NO_WRAP));}
        }
    }
    void prepareExport(String settings,int count) throws Exception {
        if(settings.length()>8*1024*1024||count<0||count>10000)throw new IOException("Backup grande demais.");
        manifest=new JSONObject().put("format","fire-game-full").put("version",1).put("count",count).put("created",System.currentTimeMillis()).put("settings",new JSONObject(settings));
    }
    void exportTo(OutputStream destination) throws Exception {
        if(manifest==null)throw new IOException("Backup não preparado.");
        JSONObject all=new JSONObject();File[] dirs=live().listFiles();
        if(dirs!=null)for(File dir:dirs)if(dir.isDirectory()&&id(dir.getName())){JSONObject files=new JSONObject();collect(dir,"",files);all.put(dir.getName(),files);}
        long total=0;
        try(ZipOutputStream zip=new ZipOutputStream(destination)){
            total+=entry(zip,"manifest.json",manifest.toString().getBytes("UTF-8"));
            total+=entry(zip,"cartridges.json",all.toString().getBytes("UTF-8"));
            for(int i=0;i<manifest.getInt("count");i++)try(InputStream in=new FileInputStream(new File(app.getCacheDir(),"checkpoint-"+i+".tmp"))){
                byte[] bytes=read(in,MAX_FILE);total+=bytes.length;if(total>MAX_TOTAL)throw new IOException("Backup excede 256 MB descompactado.");entry(zip,"point-"+i+".json",bytes);
            }
        }
    }
    private long entry(ZipOutputStream zip,String name,byte[] bytes) throws IOException {
        if(bytes.length>MAX_FILE)throw new IOException("Entrada grande demais.");zip.putNextEntry(new ZipEntry(name));zip.write(bytes);zip.closeEntry();return bytes.length;
    }
    private byte[] expected(ZipInputStream zip,String name,int limit) throws IOException {
        ZipEntry entry=zip.getNextEntry();if(entry==null||entry.isDirectory()||!name.equals(entry.getName()))throw new IOException("Backup incompleto ou formato inválido.");
        byte[] bytes=read(zip,limit);zip.closeEntry();return bytes;
    }
    void importFrom(InputStream source) throws Exception {
        remove(stage);if(!stage.mkdirs())throw new IOException("Sem espaço para validar o backup.");
        try(ZipInputStream zip=new ZipInputStream(source)){
            byte[] bytes=expected(zip,"manifest.json",8*1024*1024);long total=bytes.length;
            JSONObject m=new JSONObject(new String(bytes,"UTF-8"));
            if(!"fire-game-full".equals(m.getString("format"))||m.getInt("version")!=1)throw new IOException("Este arquivo não é um backup completo compatível.");
            int count=m.getInt("count");if(count<0||count>10000)throw new IOException("Contagem inválida.");m.getJSONObject("settings");
            bytes=expected(zip,"cartridges.json",MAX_FILE);total+=bytes.length;JSONObject saves=new JSONObject(new String(bytes,"UTF-8"));
            Iterator<String> ids=saves.keys();while(ids.hasNext()){
                String game=ids.next();if(!id(game))throw new IOException("Identificação de jogo inválida.");JSONObject files=saves.getJSONObject(game);
                Iterator<String> names=files.keys();while(names.hasNext()){String name=names.next();if(!path(name))throw new IOException("Caminho de save inválido.");byte[] data=android.util.Base64.decode(files.getString(name),android.util.Base64.DEFAULT);if(data.length>16*1024*1024)throw new IOException("Save grande demais.");}
            }
            for(int i=0;i<count;i++){
                bytes=expected(zip,"point-"+i+".json",MAX_FILE);total+=bytes.length;if(total>MAX_TOTAL)throw new IOException("Backup excede 256 MB descompactado.");
                JSONObject point=new JSONObject(new String(bytes,"UTF-8"));if(!id(point.getString("game"))||point.getLong("time")<0)throw new IOException("Ponto de save inválido.");
                if(android.util.Base64.decode(point.getString("bytes"),android.util.Base64.DEFAULT).length==0)throw new IOException("Ponto de save vazio.");
                write(new File(stage,"point-"+i+".json"),bytes);
            }
            if(zip.getNextEntry()!=null)throw new IOException("Conteúdo inesperado no backup.");
            m.put("restoreToken",UUID.randomUUID().toString());manifest=m;cartridges=saves;
        }catch(Exception e){remove(stage);throw e;}
    }
    String manifest(){return manifest==null?"{}":manifest.toString();}
    String point(int index) throws IOException {if(manifest==null||index<0||index>=manifest.optInt("count"))throw new IOException("Ponto inválido.");try(InputStream in=new FileInputStream(new File(stage,"point-"+index+".json"))){return new String(read(in,MAX_FILE),"UTF-8");}}
    boolean install(){
        try{
            if(cartridges==null||marker.exists())return false;
            remove(next);remove(previous);if(!next.mkdirs())return false;copy(live(),next);
            Iterator<String> ids=cartridges.keys();while(ids.hasNext()){String id=ids.next();JSONObject files=cartridges.getJSONObject(id);Iterator<String> names=files.keys();while(names.hasNext()){String name=names.next();write(new File(new File(next,id),name),android.util.Base64.decode(files.getString(name),android.util.Base64.DEFAULT));}}
            write(marker,manifest.getString("restoreToken").getBytes("UTF-8"));
            if(live().exists()){if(!live().renameTo(previous))throw new IOException("Falha ao proteger os saves atuais.");}else if(!previous.mkdirs())throw new IOException("Falha ao criar proteção.");
            if(!next.renameTo(live()))throw new IOException("Falha ao aplicar os saves.");return true;
        }catch(Exception e){if(marker.exists())rollback();else try{remove(next);}catch(IOException ignored){}return false;}
    }
    boolean rollback(){
        try{if(previous.exists()){remove(live());if(!previous.renameTo(live()))return false;}remove(next);if(marker.exists()&&!marker.delete())return false;return true;}catch(IOException e){return false;}
    }
    boolean finish(){
        // A durable commit token coordinates Android saves with the browser preference journal.
        String token=manifest==null?"":manifest.optString("restoreToken");
        if(token.isEmpty()||!app.getPreferences(android.app.Activity.MODE_PRIVATE).edit().putString("fullBackupCommitted",token).commit())return false;
        try{remove(marker);remove(previous);remove(stage);}catch(IOException ignored){}cartridges=null;return true;
    }
}
