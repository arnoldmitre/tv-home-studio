package com.gathin.tvhome;
import android.content.*;
import android.os.*;
import android.view.View;
import android.widget.TextView;
import org.json.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.lang.ref.WeakReference;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Foreground-only cached weather for an explicitly selected city. */
public final class WeatherInfo {
    public static final String ATTRIBUTION="Weather: Open-Meteo (CC BY 4.0)";
    private static final Handler MAIN=new Handler(Looper.getMainLooper());
    private static final ArrayList<WeakReference<TextView>> VIEWS=new ArrayList<>();
    private static final AtomicBoolean RUNNING=new AtomicBoolean();
    private static long lastAttempt;
    private static int generation;
    private static String label="Weather loading…";
    private static String configKey="";
    private static long fetched;
    static void invalidate(){MAIN.post(()->{generation++;lastAttempt=0;fetched=0;configKey="";label="Weather loading…";publish();});}
    public static void unbind(TextView view){VIEWS.removeIf(ref->ref.get()==null||ref.get()==view);}
    public static void bind(TextView view){
        unbind(view);JSONObject weather=StudioConfig.read(view.getContext()).optJSONObject("weather");
        if(weather.optString("mode","off").equals("off")||!StudioConfig.flag(view.getContext(),"showWeather")){view.setVisibility(View.GONE);return;}view.setVisibility(View.VISIBLE);VIEWS.add(new WeakReference<>(view));
        String key=weather.toString();if(!key.equals(configKey)){configKey=key;generation++;lastAttempt=0;fetched=0;label="Weather loading…";}
        render(view);long now=System.currentTimeMillis();if((now>=fetched&&now-fetched<3600000)||(now>=lastAttempt&&now-lastAttempt<120000)||!RUNNING.compareAndSet(false,true))return;
        lastAttempt=now;int requestGeneration=generation;Context app=view.getContext().getApplicationContext();
        new Thread(()->{
            android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_BACKGROUND);String next;long time=0;
            SharedPreferences cache=app.getSharedPreferences("studio_weather",Context.MODE_PRIVATE);
            try{
                if(key.equals(cache.getString("key",""))&&now>=cache.getLong("fetched",0)&&now-cache.getLong("fetched",0)<3600000){next=cache.getString("label","Weather unavailable");time=cache.getLong("fetched",0);}
                else{
                    JSONObject place=weather;
                    if(place.optString("mode","").equals("auto")){
                        place=resolveIpLocation(cache,now);
                    }
                    JSONObject data=com.veyra.shared.WeatherApi.current(place,com.veyra.shared.WeatherApi::json);double temperature=data.getDouble("temperature_2m");cache.edit().remove("error").apply();
                    next=place.optString("name","Nearby")+" · "+Math.round(temperature)+"° · "+condition(data.getInt("weather_code"));time=now;cache.edit().putString("key",key).putString("label",next).putLong("fetched",now).apply();
                }
            }catch(Exception e){cache.edit().putString("error",e.getMessage()==null?"Weather request failed":e.getMessage()).apply();long cached=cache.getLong("fetched",0);next=key.equals(cache.getString("key",""))&&now>=cached&&now-cached<86400000?cache.getString("label","")+" · Last known":"Weather unavailable";}
            String result=next;long resultTime=time;MAIN.post(()->{RUNNING.set(false);if(requestGeneration!=generation){for(WeakReference<TextView> ref:new ArrayList<>(VIEWS)){TextView v=ref.get();if(v!=null)bind(v);}return;}label=result;fetched=resultTime;publish();});
        },"home-weather").start();
    }
    static JSONObject status(Context c)throws JSONException{SharedPreferences cache=c.getSharedPreferences("studio_weather",Context.MODE_PRIVATE);return new JSONObject().put("label",cache.getString("label","")).put("fetched",cache.getLong("fetched",0)).put("error",cache.getString("error","")).put("running",RUNNING.get());}
    private static JSONObject resolveIpLocation(SharedPreferences cache, long now) throws Exception {
        String city = cache.getString("auto_city", "");
        double lat = Double.longBitsToDouble(cache.getLong("auto_lat", 0));
        double lon = Double.longBitsToDouble(cache.getLong("auto_lon", 0));
        if (!city.isEmpty() && now >= cache.getLong("auto_loc_fetched", 0) && now - cache.getLong("auto_loc_fetched", 0) < 86400000L) {
            return new JSONObject().put("name", city).put("latitude", lat).put("longitude", lon);
        }
        try {
            JSONObject o = com.veyra.shared.WeatherApi.json("https://ipwho.is/");
            if (o.optBoolean("success", false)) {
                city = o.optString("city", o.optString("region", "Nearby"));
                lat = o.getDouble("latitude");
                lon = o.getDouble("longitude");
            }
        } catch (Exception ignored) {}
        if (city.isEmpty()) {
            JSONObject o = com.veyra.shared.WeatherApi.json("https://ipapi.co/json/");
            city = o.optString("city", o.optString("region", "Nearby"));
            lat = o.getDouble("latitude");
            lon = o.getDouble("longitude");
        }
        cache.edit().putString("auto_city", city).putLong("auto_lat", Double.doubleToLongBits(lat)).putLong("auto_lon", Double.doubleToLongBits(lon)).putLong("auto_loc_fetched", now).apply();
        return new JSONObject().put("name", city).put("latitude", lat).put("longitude", lon);
    }
    static void retry(){MAIN.post(()->{generation++;lastAttempt=0;fetched=0;for(WeakReference<TextView> ref:new ArrayList<>(VIEWS)){TextView v=ref.get();if(v!=null){v.getContext().getSharedPreferences("studio_weather",Context.MODE_PRIVATE).edit().remove("fetched").apply();bind(v);}}});}
    private static void render(TextView v){if(!android.text.TextUtils.equals(v.getText(),label))v.setText(label);v.setContentDescription(label+". Celsius. "+ATTRIBUTION);}
    private static void publish(){for(Iterator<WeakReference<TextView>> it=VIEWS.iterator();it.hasNext();){TextView v=it.next().get();if(v==null)it.remove();else render(v);}}
    private static String condition(int code) {
        switch (code) {
            case 0: return "Clear";
            case 1: return "Mostly clear";
            case 2: return "Partly cloudy";
            case 3: return "Cloudy";
            case 45: case 48: return "Fog";
            case 51: case 53: case 55: return "Drizzle";
            case 56: case 57: return "Freezing drizzle";
            case 61: case 63: return "Rain";
            case 65: return "Heavy rain";
            case 66: case 67: return "Freezing rain";
            case 71: case 73: case 75: case 77: return "Snow";
            case 80: case 81: return "Showers";
            case 82: return "Heavy showers";
            case 85: case 86: return "Snow showers";
            case 95: case 96: case 97: case 99: return "Thunderstorms";
            default: return "Conditions unavailable";
        }
    }

}
