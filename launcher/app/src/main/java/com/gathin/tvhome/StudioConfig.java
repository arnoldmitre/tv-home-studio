package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;
import org.json.*;
import java.io.File;
import java.util.*;

/** Small, versioned configuration shared with the phone through a shell-only provider. */
final class StudioConfig {
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("tvhome",Context.MODE_PRIVATE);}
    static JSONObject read(Context c){
        try{JSONObject saved=new JSONObject(prefs(c).getString("studio_config",defaults().toString()));if(saved.optInt("schema")==1&&!prefs(c).contains("studio_config_v1_backup"))prefs(c).edit().putString("studio_config_v1_backup",saved.toString()).apply();return validate(migrate(saved));}
        catch(JSONException|IllegalArgumentException e){return defaults();}
    }
    static final List<String> DESIGNS=Arrays.asList("classic","cinema","compact");
    static final List<String> FLAGS=Arrays.asList("showClock","showCast","showGreeting","showStatus","showWeather","showPower","animations");
    static JSONObject preset(String id)throws JSONException {
        if(!DESIGNS.contains(id))throw new IllegalArgumentException("Unknown design");
        JSONObject t=new JSONObject().put("id",id).put("layout",id).put("name",id.equals("classic")?"Classic":id.equals("cinema")?"Cinema":"Panel").put("wallpapers",new JSONArray()).put("rotateMinutes",0).put("inputs","auto").put("wallpaperSource","custom");
        for(String flag:FLAGS)t.put(flag,true);return t;
    }
    static JSONObject defaults(){try{return new JSONObject().put("schema",2).put("idleScreen",true).put("idleWallpaper","").put("homeName","HOME").put("active","classic").put("weather",new JSONObject().put("mode","off")).put("themes",new JSONArray().put(preset("classic")));}catch(JSONException e){throw new IllegalStateException(e);}}
    static JSONObject migrate(JSONObject old)throws JSONException {
        if(old.optInt("schema")!=1)return old;
        JSONObject result=defaults();result.put("weather",old.optJSONObject("weather"));JSONArray list=old.getJSONArray("themes"),out=new JSONArray();Map<String,JSONObject> mapped=new LinkedHashMap<>();String active="classic";
        for(int i=0;i<list.length();i++){JSONObject previous=list.getJSONObject(i);String layout=previous.optString("layout","classic");JSONObject t=preset(layout);t.put("wallpapers",previous.optJSONArray("wallpapers")).put("rotateMinutes",previous.optInt("rotateMinutes")).put("inputs",old.optString("inputs","auto"));for(String f:FLAGS)t.put(f,old.optBoolean(f,true));boolean selected=previous.optString("id").equals(old.optString("active"));if(!mapped.containsKey(layout)||selected)mapped.put(layout,t);if(selected)active=layout;}
        if(!mapped.containsKey(active))mapped.put(active,preset(active));for(JSONObject t:mapped.values())out.put(t);return result.put("themes",out).put("active",active);
    }
    static JSONObject active(JSONObject config){JSONArray list=config.optJSONArray("themes");for(int i=0;i<list.length();i++){JSONObject t=list.optJSONObject(i);if(t.optString("id").equals(config.optString("active")))return t;}return defaults().optJSONArray("themes").optJSONObject(0);}
    static JSONObject theme(Context c){return active(read(c));}
    static boolean flag(Context c,String name){return theme(c).optBoolean(name,true);}
    static String layout(Context c){return theme(c).optString("layout","classic");}
    static String effective(JSONObject c)throws JSONException{return new JSONObject().put("idleScreen",c.optBoolean("idleScreen",true)).put("idleWallpaper",c.optString("idleWallpaper","")).put("homeName",c.optString("homeName","HOME")).put("theme",active(c)).put("weather",c.getJSONObject("weather")).toString();}
    static boolean id(String s){return s!=null&&s.matches("[a-zA-Z0-9_-]{1,48}");}
    static File image(Context c,String id){if(!id(id))throw new IllegalArgumentException("Invalid image ID");File dir=new File(c.getFilesDir(),"wallpapers");dir.mkdirs();return new File(dir,id+".jpg");}
    static JSONObject validate(JSONObject source)throws JSONException{
        source=migrate(source);
        if(source.toString().length()>40000||source.optInt("schema")!=2)throw new IllegalArgumentException("Unsupported configuration");
        String homeName=source.optString("homeName","HOME").trim();if(homeName.isEmpty()||homeName.length()>24||homeName.matches("(?s).*[\\p{Cntrl}].*"))throw new IllegalArgumentException("Home name must be 1–24 characters on one line");
        JSONObject result=new JSONObject().put("schema",2).put("idleScreen",source.optBoolean("idleScreen",true)).put("homeName",homeName);
        String idleWallpaper=source.optString("idleWallpaper","");
        if(!idleWallpaper.isEmpty()&&!id(idleWallpaper))throw new IllegalArgumentException("Invalid screensaver wallpaper");
        result.put("idleWallpaper",idleWallpaper);
        JSONObject w=source.optJSONObject("weather");if(w==null)w=new JSONObject();String mode=w.optString("mode","off");if(mode.equals("auto"))mode="off";if(!Arrays.asList("off","city").contains(mode))throw new IllegalArgumentException("Invalid weather mode");
        JSONObject weather=new JSONObject().put("mode",mode);
        if(mode.equals("city")){double lat=w.getDouble("latitude"),lon=w.getDouble("longitude");if(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180)throw new IllegalArgumentException("Invalid coordinates");String name=w.getString("name").trim();if(name.isEmpty()||name.length()>60)throw new IllegalArgumentException("Invalid location name");weather.put("latitude",lat).put("longitude",lon).put("name",name);}
        result.put("weather",weather);
        JSONArray ts=source.getJSONArray("themes"),clean=new JSONArray();if(ts.length()<1||ts.length()>3)throw new IllegalArgumentException("Choose a built-in design");Set<String> ids=new HashSet<>();
        for(int i=0;i<ts.length();i++){JSONObject t=ts.getJSONObject(i);String id=t.getString("id"),name=t.getString("name").trim(),layout=t.getString("layout");if(!DESIGNS.contains(id)||!ids.add(id)||!id.equals(layout))throw new IllegalArgumentException("Invalid theme");
            int rotation=t.optInt("rotateMinutes",0);if(rotation!=0&&(rotation<1||rotation>1440))throw new IllegalArgumentException("Rotation must be off or 1–1440 minutes");
            JSONArray images=t.optJSONArray("wallpapers");if(images==null)images=new JSONArray();if(images.length()>8)throw new IllegalArgumentException("Eight wallpapers per theme maximum");for(int j=0;j<images.length();j++)if(!id(images.getString(j)))throw new IllegalArgumentException("Invalid image ID");
            JSONObject normalized=preset(id).put("rotateMinutes",rotation).put("wallpapers",images);for(String flag:FLAGS)normalized.put(flag,t.optBoolean(flag,true));String input=t.optString("inputs","auto");if(!Arrays.asList("auto","show","hide").contains(input))throw new IllegalArgumentException("Invalid Inputs mode");normalized.put("inputs",input);
            String src=t.optString("wallpaperSource","custom");if(!Arrays.asList("custom","bing","nasa").contains(src))src="custom";normalized.put("wallpaperSource",src);clean.put(normalized);}
        String active=source.getString("active");if(!ids.contains(active))throw new IllegalArgumentException("Active theme missing");return result.put("active",active).put("themes",clean);
    }
    static synchronized void save(Context c,JSONObject source)throws JSONException{
        JSONObject clean=validate(source),before=read(c);boolean displayChanged=!effective(before).equals(effective(clean));boolean weatherChanged=!before.optJSONObject("weather").toString().equals(clean.getJSONObject("weather").toString());
        String idleWallpaper=clean.optString("idleWallpaper","");
        if(!idleWallpaper.isEmpty()&&!image(c,idleWallpaper).isFile())throw new IllegalArgumentException("Upload screensaver wallpaper before saving");
        for(int i=0;i<clean.getJSONArray("themes").length();i++){JSONArray images=clean.getJSONArray("themes").getJSONObject(i).getJSONArray("wallpapers");for(int j=0;j<images.length();j++)if(!image(c,images.getString(j)).isFile())throw new IllegalArgumentException("Upload wallpaper before saving theme");}
        if(!prefs(c).edit().putString("studio_config",clean.toString()).putLong("ui_revision",prefs(c).getLong("ui_revision",0)+(displayChanged?1:0)).commit())throw new IllegalStateException("Unable to save configuration");
        if(weatherChanged)WeatherInfo.invalidate();if(displayChanged)HomeRedirectService.refreshHome();
    }
    static void activate(Context c,String id){try{
        if(!DESIGNS.contains(id))throw new IllegalArgumentException("Unknown design");
        JSONObject config=read(c);JSONArray themes=config.getJSONArray("themes");boolean found=false;
        for(int i=0;i<themes.length();i++)if(id.equals(themes.getJSONObject(i).optString("id")))found=true;
        if(!found)themes.put(preset(id));
        save(c,config.put("active",id));
    }catch(Exception e){android.widget.Toast.makeText(c,e.getMessage(),android.widget.Toast.LENGTH_LONG).show();}}
    static String wallpaper(Context c){JSONObject t=theme(c);JSONArray a=t.optJSONArray("wallpapers");if(a==null||a.length()==0)return "";int minutes=t.optInt("rotateMinutes");long position=(minutes>0?System.currentTimeMillis()/(minutes*60000L):0)+prefs(c).getInt("wallpaper_index_"+t.optString("id"),0);return a.optString((int)(position%a.length()));}
    static String idleWallpaper(Context c){String id=read(c).optString("idleWallpaper","");return id.isEmpty()?wallpaper(c):id;}
}
