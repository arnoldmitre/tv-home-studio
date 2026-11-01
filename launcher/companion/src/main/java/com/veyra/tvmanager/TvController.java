package com.veyra.tvmanager;

import android.content.Context;
import android.util.AtomicFile;
import android.util.Base64;
import com.cgutman.adblib.AdbCrypto;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.*;

/** Explicit, reversible operations. No arbitrary command endpoint is exposed to the UI. */
final class TvController implements Closeable {
    static final String HOME="com.gathin.tvhome", PROVIDER="content://com.gathin.tvhome.studio", SERVICE=HOME+"/"+HOME+".HomeRedirectService";
    static final LinkedHashMap<String,String> CLEAN=new LinkedHashMap<>();
    static final String[] UPDATES={"com.amazon.tv.forcedotaupdater.v2","com.amazon.device.software.ota","com.amazon.device.software.ota.override"};
    static {CLEAN.put("com.amazon.wirelessmetrics.service","Wireless usage metrics");CLEAN.put("com.amazon.tv.fw.metrics","TV usage metrics");CLEAN.put("com.amazon.dp.logger","Diagnostic logger");CLEAN.put("com.amazon.hybridadidservice","Advertising identifier");CLEAN.put("com.amazon.neo.minerva","Minerva metrics");CLEAN.put("com.amazon.neo.minerva.logcat","Minerva log collector");CLEAN.put("com.amazon.perfcollection","Performance collection");CLEAN.put("com.amazon.perfc","Performance reporting");CLEAN.put("com.amazon.kso.blackbird","Promotional content");CLEAN.put("com.amazon.sneakpeek","Content previews");CLEAN.put("com.amazon.tv.easyupgrade","Upgrade promotion");CLEAN.put("com.amazon.shoptv.client","Amazon shopping");CLEAN.put("com.amazon.shoptv.firetv.client","Fire TV shopping");}
    private final Context context;
    private AdbClient adb;
    private String identity="", address="", model="";
    private JSONObject ledger;
    private AtomicFile ledgerFile;
    private final Set<String> packages=new HashSet<>(), thirdParty=new HashSet<>();
    interface Progress {void show(String message);}
    private final Progress progress;
    TvController(Context context,Progress progress){this.context=context.getApplicationContext();this.progress=progress;}
    static String quote(String value){return "'"+value.replace("'","'\\''")+"'";}
    static void packageName(String value){if(!value.matches("[a-zA-Z][a-zA-Z0-9_.]{1,180}"))throw new IllegalArgumentException("Invalid package name");}
    private static String base64(String value){return Base64.encodeToString(value.getBytes(StandardCharsets.UTF_8),Base64.URL_SAFE|Base64.NO_WRAP);}
    private String command(String cmd)throws Exception{
        if(adb==null||!adb.isOpen())throw new IOException("Connect to your TV first");
        String result=adb.shell(cmd+"; printf '\\n__TVSTUDIO_RC:%s\\n' $?");int index=result.lastIndexOf("__TVSTUDIO_RC:");if(index<0)throw new IOException("TV did not confirm the command result");String code=result.substring(index+14).trim();String output=result.substring(0,index).trim();if(!code.equals("0"))throw new IOException(output.isEmpty()?"Command refused ("+code+")":output);return output;
    }
    JSONObject connect(String host,int port)throws Exception{
        close();if(!host.matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}"))throw new IllegalArgumentException("Enter the TV IPv4 address");for(String part:host.split("\\."))if(Integer.parseInt(part)>255)throw new IllegalArgumentException("Invalid IP address");if(port<1||port>65535)throw new IllegalArgumentException("Invalid port");
        File priv=new File(context.getFilesDir(),"adb-private"),pub=new File(context.getFilesDir(),"adb-public");AdbCrypto crypto;
        if(priv.isFile()&&pub.isFile())crypto=AdbCrypto.loadAdbKeyPair(data->Base64.encodeToString(data,Base64.NO_WRAP),priv,pub);
        else{crypto=AdbCrypto.generateAdbKeyPair(data->Base64.encodeToString(data,Base64.NO_WRAP));crypto.saveAdbKeyPair(priv,pub);}
        progress.show("Connecting… accept the authorization prompt on your TV.");adb=new AdbClient(host,port,crypto);address=host+":"+port;
        String manufacturer=command("getprop ro.product.manufacturer");model=command("getprop ro.product.model");int sdk=Integer.parseInt(command("getprop ro.build.version.sdk"));loadPackages();
        if(!packages.contains("com.amazon.tv.launcher")||sdk<25){close();throw new IOException("This build supports Android-based Fire TV devices on Android 7.1 or newer. Other TVs and Vega OS are not supported.");}
        identity=deviceIdentity();String key=hex(MessageDigest.getInstance("SHA-256").digest(identity.getBytes(StandardCharsets.UTF_8)));ledgerFile=new AtomicFile(new File(context.getFilesDir(),"restore-"+key+".json"));ledger=null;
        if(ledgerFile.getBaseFile().exists()){try(InputStream in=ledgerFile.openRead()){ledger=new JSONObject(read(in,2*1024*1024));}if(!identity.equals(ledger.getString("identity")))throw new IOException("Restore point belongs to a different TV");}
        context.getSharedPreferences("studio",0).edit().putString("lastHost",host).putInt("lastPort",port).apply();return status();
    }
    private String deviceIdentity()throws Exception{String serial=command("getprop ro.serialno");if(serial.isEmpty())serial=command("getprop ro.boot.serialno");if(serial.isEmpty())serial=command("settings get secure android_id");if(serial.isEmpty()||serial.equals("null"))throw new IOException("Cannot identify TV safely");return model+":"+serial;}
    private void verify()throws Exception{if(identity.isEmpty()||!identity.equals(deviceIdentity())){close();throw new IOException("TV identity changed; reconnect before making changes");}}
    private void loadPackages()throws Exception{packages.clear();thirdParty.clear();for(String line:command("pm list packages --user 0").split("\\r?\\n"))if(line.startsWith("package:"))packages.add(line.substring(8).trim());for(String line:command("pm list packages -3 --user 0").split("\\r?\\n"))if(line.startsWith("package:"))thirdParty.add(line.substring(8).trim());}
    JSONObject status()throws Exception{
        verify();JSONObject info=new JSONObject().put("model",model).put("address",address).put("android",command("getprop ro.build.version.release")).put("build",command("getprop ro.build.display.id")).put("installed",packages.contains(HOME)).put("restorePoint",ledger!=null);
        JSONArray cleanup=new JSONArray();for(Map.Entry<String,String> item:CLEAN.entrySet())if(packages.contains(item.getKey()))cleanup.put(new JSONObject().put("package",item.getKey()).put("name",item.getValue()).put("state",packageState(item.getKey())));info.put("cleanup",cleanup);
        JSONArray updates=new JSONArray();for(String pkg:UPDATES)if(packages.contains(pkg))updates.put(new JSONObject().put("package",pkg).put("state",packageState(pkg)).put("background",opMode(pkg)));info.put("updates",updates);
        if(packages.contains(HOME))try{JSONObject config=provider("read",null);info.put("config",config);info.put("needsUpgrade",config.optInt("launcherVersion")<18);info.put("apps",provider("apps",null).getJSONArray("apps"));}catch(Exception e){info.put("needsUpgrade",true);}
        info.put("history",ledger==null?new JSONArray():ledger.optJSONArray("history"));return info;
    }
    private void ensureLedger()throws Exception{
        verify();if(ledger!=null){saveLedger();return;}
        ledger=new JSONObject().put("schema",1).put("identity",identity).put("model",model).put("created",System.currentTimeMillis()).put("packages",new JSONObject()).put("appops",new JSONObject()).put("history",new JSONArray());
        ledger.put("homeWasEnabled",servicePresent(command("settings get secure enabled_accessibility_services")));
        if(packages.contains(HOME)){String dump=command("dumpsys package "+HOME);ledger.put("readLogsGranted",dump.contains("android.permission.READ_LOGS: granted=true")).put("secureGranted",dump.contains("android.permission.WRITE_SECURE_SETTINGS: granted=true"));try{ledger.put("config",provider("read",null));}catch(Exception ignored){}}
        try{saveLedger();}catch(Exception e){ledger=null;throw e;}
    }
    private void saveLedger()throws Exception{FileOutputStream out=null;try{out=ledgerFile.startWrite();out.write(ledger.toString(2).getBytes(StandardCharsets.UTF_8));ledgerFile.finishWrite(out);}catch(Exception e){if(out!=null)ledgerFile.failWrite(out);throw e;}}
    private void record(String text)throws Exception{JSONArray history=ledger.getJSONArray("history");history.put(new JSONObject().put("time",System.currentTimeMillis()).put("message",text));while(history.length()>150)history.remove(0);saveLedger();}
    static boolean servicePresent(String list){for(String entry:list.split(":"))if(entry.equals(SERVICE)||entry.equals(HOME+"/.HomeRedirectService"))return true;return false;}
    static int parseState(String dump)throws IOException{Matcher m=Pattern.compile("User 0:[^\\r\\n]*\\benabled=(\\d+)").matcher(dump);if(!m.find())throw new IOException("Cannot read original app state");int state=Integer.parseInt(m.group(1));if(state<0||state>4)throw new IOException("Unknown app state");return state;}
    private int packageState(String pkg)throws Exception{packageName(pkg);return parseState(command("dumpsys package "+pkg));}
    static String modeForState(int state){switch(state){case 0:return "default-state";case 1:return "enable";case 2:return "disable";case 3:return "disable-user";case 4:return "disable-until-used";default:throw new IllegalArgumentException("Unknown app state");}}
    private boolean allowed(String pkg){return CLEAN.containsKey(pkg)||Arrays.asList(UPDATES).contains(pkg)||(thirdParty.contains(pkg)&&!pkg.equals(HOME));}
    private void capturePackage(String pkg)throws Exception{packageName(pkg);if(!packages.contains(pkg)||!allowed(pkg))throw new IOException("This package is protected or unavailable");ensureLedger();JSONObject original=ledger.getJSONObject("packages");if(!original.has(pkg)){original.put(pkg,packageState(pkg));saveLedger();}}
    private void setPackage(String pkg,int state)throws Exception{capturePackage(pkg);String result=command("pm "+modeForState(state)+" --user 0 "+pkg);if(packageState(pkg)!=state)throw new IOException("Fire OS protected "+pkg+": "+result);record(pkg+" → "+modeForState(state));}
    JSONObject cleanup(JSONArray selected)throws Exception{ensureLedger();JSONArray results=new JSONArray();for(int i=0;i<selected.length();i++){String pkg=selected.getString(i);if(!CLEAN.containsKey(pkg))throw new IllegalArgumentException("Not in the optional cleanup list");progress.show("Disabling "+CLEAN.get(pkg));try{setPackage(pkg,3);results.put(pkg+": disabled");}catch(Exception e){results.put(pkg+": "+e.getMessage());}}return new JSONObject().put("results",results).put("state",status());}
    JSONObject packageAction(String pkg,String action)throws Exception{if(!Arrays.asList("disable","enable","restore").contains(action))throw new IllegalArgumentException("Invalid app action");ensureLedger();int state=action.equals("disable")?3:1;if(action.equals("restore")){if(!ledger.getJSONObject("packages").has(pkg))throw new IOException("No saved state for this app");state=ledger.getJSONObject("packages").getInt(pkg);}setPackage(pkg,state);return status();}
    private String opMode(String pkg)throws Exception{String output=command("cmd appops get "+pkg+" RUN_ANY_IN_BACKGROUND");Matcher m=Pattern.compile("RUN_ANY_IN_BACKGROUND: (allow|ignore|deny|default|foreground)").matcher(output);if(m.find())return m.group(1);if(output.contains("No operations")||output.contains("No op"))return "default";throw new IOException("Cannot read update background policy");}
    JSONObject updates(boolean restrict)throws Exception{
        ensureLedger();JSONArray results=new JSONArray();for(String pkg:UPDATES)if(packages.contains(pkg)){
            progress.show((restrict?"Restricting ":"Resuming ")+pkg);
            try{setPackage(pkg,restrict?3:1);results.put(pkg+": "+(restrict?"disabled":"enabled"));}catch(Exception e){results.put(pkg+": package change refused — "+e.getMessage());}
            if(!pkg.equals(UPDATES[0]))try{JSONObject saved=ledger.getJSONObject("appops");if(!saved.has(pkg)){saved.put(pkg,opMode(pkg));saveLedger();}String mode=restrict?"ignore":"allow";command("cmd appops set "+pkg+" RUN_ANY_IN_BACKGROUND "+mode);if(!mode.equals(opMode(pkg)))throw new IOException("Read-back differed");record(pkg+" background → "+mode);results.put(pkg+": background "+mode);}catch(Exception e){results.put(pkg+": background policy refused — "+e.getMessage());}
        }
        results.put(restrict?"Restrictions are partial when Fire OS protects an updater. Privileged updates can still run; no DNS or router changes were made.":"Known update packages enabled and background restrictions lifted where permitted. This does not force an immediate update.");return new JSONObject().put("results",results).put("state",status());
    }
    JSONObject install()throws Exception{
        ensureLedger();boolean selectedHome=servicePresent(command("settings get secure enabled_accessibility_services"));progress.show("Uploading the bundled TV Home launcher…");String path="/data/local/tmp/tvstudio-home.apk";
        File apk=new File(context.getCacheDir(),"launcher.apk");try(InputStream in=context.getAssets().open("TV-Home.apk");OutputStream out=new FileOutputStream(apk)){copy(in,out,20*1024*1024);}try(InputStream in=new FileInputStream(apk)){adb.push(in,path,apk.length());}
        try{String output=command("pm install -r "+path);if(!output.contains("Success"))throw new IOException(output+". Existing launcher data was preserved; do not uninstall to work around a signing mismatch.");}finally{try{command("rm -f "+path);}catch(Exception ignored){}}
        loadPackages();command("pm grant "+HOME+" android.permission.WRITE_SECURE_SETTINGS");command("pm grant "+HOME+" android.permission.READ_LOGS");try{command("cmd appops set "+HOME+" SYSTEM_ALERT_WINDOW allow");}catch(Exception ignored){try{command("appops set "+HOME+" SYSTEM_ALERT_WINDOW allow");}catch(Exception ignored2){}}if(selectedHome){provider("home","this");JSONObject repair=provider("repairHome",null);awaitHome(repair.optLong("previousConnection"));}record("TV Home 4.8 installed; Home switching permissions granted");return status();
    }
    JSONObject provider(String method,String arg)throws Exception{
        String output=command("content call --uri "+PROVIDER+" --method "+method+(arg==null?"":" --arg "+quote(arg)));
        Matcher m=Pattern.compile("payload=([A-Za-z0-9_=-]+)").matcher(output);if(!m.find())throw new IOException(output.contains("error=")?output:"Install or update TV Home first");return new JSONObject(new String(Base64.decode(m.group(1),Base64.URL_SAFE),StandardCharsets.UTF_8));
    }
    JSONObject home(String choice)throws Exception{if(!choice.equals("this")&&!choice.equals("default"))throw new IllegalArgumentException("Choose a Home screen");ensureLedger();command("pm grant "+HOME+" android.permission.WRITE_SECURE_SETTINGS");if(choice.equals("this"))command("pm grant "+HOME+" android.permission.READ_LOGS");JSONObject r=provider("home",choice);if(choice.equals("this"))awaitHome();record("Selected "+choice+" Home");return r;}
    private void awaitHome()throws Exception{awaitHome(-1);}
    private void awaitHome(long previous)throws Exception{for(int i=0;i<32;i++){JSONObject health=provider("read",null).optJSONObject("homeHealth");if(health!=null&&health.optBoolean("connected"))return;Thread.sleep(250);}throw new IOException("Launcher installed, but Home switching has not connected yet. Use Repair Home switching or select This home again.");}
    JSONObject repairHome()throws Exception{ensureLedger();command("pm grant "+HOME+" android.permission.WRITE_SECURE_SETTINGS");command("pm grant "+HOME+" android.permission.READ_LOGS");JSONObject repair=provider("repairHome",null);awaitHome(repair.optLong("previousConnection"));record("Home switching reconnected");return status();}
    JSONObject config(JSONObject value)throws Exception{ensureLedger();JSONObject result=provider("save",base64(value.toString()));record("Launcher appearance updated");return result;}
    JSONObject apps(JSONObject value)throws Exception{ensureLedger();JSONObject result=provider("appsSave",base64(value.toString()));record("Favorites and hidden apps updated");return result;}
    JSONObject wallpaper(File file,String id)throws Exception{if(!id.matches("[a-zA-Z0-9_-]{1,48}")||file.length()>3*1024*1024)throw new IOException("Invalid wallpaper");ensureLedger();String path="/data/local/tmp/tvstudio-"+id+".jpg";try(InputStream in=new FileInputStream(file)){adb.push(in,path,file.length());}try{command("content write --uri "+PROVIDER+"/wallpaper/"+id+" < "+path);return provider("commitImage",id);}finally{try{command("rm -f "+path);}catch(Exception ignored){}}}
    JSONObject weatherRetry()throws Exception{verify();return provider("weatherRetry",null);}
    JSONObject restore()throws Exception{
        verify();if(ledger==null)throw new IOException("No restore point for this TV");JSONArray results=new JSONArray();JSONObject original=ledger.getJSONObject("packages");for(Iterator<String> it=original.keys();it.hasNext();){String pkg=it.next();try{setPackage(pkg,original.getInt(pkg));results.put(pkg+": restored");}catch(Exception e){results.put(pkg+": "+e.getMessage());}}
        JSONObject ops=ledger.getJSONObject("appops");for(Iterator<String> it=ops.keys();it.hasNext();){String pkg=it.next();try{if(!Arrays.asList(UPDATES).contains(pkg))throw new IOException("Invalid restore entry");String mode=ops.getString(pkg);if(!Arrays.asList("allow","ignore","deny","default","foreground").contains(mode))throw new IOException("Invalid app-op");command("cmd appops set "+pkg+" RUN_ANY_IN_BACKGROUND "+mode);if(!mode.equals(opMode(pkg)))throw new IOException("Read-back differed");results.put(pkg+": background restored");}catch(Exception e){results.put(pkg+": "+e.getMessage());}}
        if(packages.contains(HOME)){try{if(ledger.has("config")){provider("save",base64(ledger.getJSONObject("config").toString()));JSONObject c=ledger.getJSONObject("config");provider("appsSave",base64(new JSONObject().put("dock",new JSONArray(c.optString("dock","").isEmpty()?Collections.emptyList():Arrays.asList(c.optString("dock","").split(",")))).put("hidden",c.optJSONArray("hidden")).toString()));}provider("home",ledger.optBoolean("homeWasEnabled")?"this":"default");results.put("Home preference restored");}catch(Exception e){results.put("Home: "+e.getMessage());}}
        if(packages.contains(HOME))for(String[] permission:new String[][]{{"readLogsGranted","READ_LOGS"},{"secureGranted","WRITE_SECURE_SETTINGS"}})if(!ledger.optBoolean(permission[0]))try{command("pm revoke "+HOME+" android.permission."+permission[1]);results.put(permission[1]+": restored to ungranted");}catch(Exception e){results.put(permission[1]+": "+e.getMessage());}
        record("Restore requested; individual results reported");return new JSONObject().put("results",results).put("state",status());
    }
    JSONObject importRestore(String text)throws Exception{
        verify();if(text.length()>2*1024*1024)throw new IOException("Restore file too large");JSONObject incoming=new JSONObject(text);
        if(incoming.optInt("schema")!=1||!identity.equals(incoming.optString("identity")))throw new IOException("This restore point belongs to a different TV");
        JSONObject pkgs=incoming.getJSONObject("packages");for(Iterator<String> it=pkgs.keys();it.hasNext();){String pkg=it.next();packageName(pkg);if(!allowed(pkg))throw new IOException("Restore file includes a protected package");modeForState(pkgs.getInt(pkg));}
        JSONObject ops=incoming.getJSONObject("appops");for(Iterator<String> it=ops.keys();it.hasNext();){String pkg=it.next();if(!Arrays.asList(UPDATES).contains(pkg)||!Arrays.asList("allow","ignore","deny","default","foreground").contains(ops.getString(pkg)))throw new IOException("Invalid update restore entry");}
        if(ledger!=null)throw new IOException("This phone already has a restore point for this TV. It will not be overwritten.");
        incoming.put("history",new JSONArray());ledger=incoming;try{saveLedger();}catch(Exception e){ledger=null;throw e;}return status();
    }
    String export()throws Exception{verify();if(ledger==null)throw new IOException("No restore point yet");return ledger.toString(2);}
    private static String hex(byte[] b){StringBuilder s=new StringBuilder();for(byte v:b)s.append(String.format(Locale.ROOT,"%02x",v&255));return s.toString();}
    static String read(InputStream in,int maximum)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();copy(in,out,maximum);return out.toString("UTF-8");}
    static void copy(InputStream in,OutputStream out,int maximum)throws IOException{byte[] b=new byte[8192];int n,total=0;while((n=in.read(b))!=-1){total+=n;if(total>maximum)throw new IOException("File too large");out.write(b,0,n);}}
    boolean connected(){return adb!=null&&adb.isOpen();}
    @Override public void close(){if(adb!=null)adb.close();adb=null;identity="";ledger=null;ledgerFile=null;packages.clear();thirdParty.clear();}
}
