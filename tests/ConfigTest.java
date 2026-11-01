package com.gathin.tvhome;
import org.json.*;
public class ConfigTest {
 static int cases;
 static void reject(JSONObject c)throws Exception{boolean rejected=false;try{StudioConfig.validate(c);}catch(IllegalArgumentException|JSONException e){rejected=true;}if(!rejected)throw new AssertionError("Unsafe config accepted: "+c);cases++;}
 public static void main(String[] args)throws Exception{
  JSONObject c=StudioConfig.validate(StudioConfig.defaults());if(c.getJSONArray("themes").length()!=1||!c.getJSONObject("weather").getString("mode").equals("off"))throw new AssertionError("defaults incorrect");cases++;
  reject(StudioConfig.defaults().put("active","missing"));c=StudioConfig.defaults();c.getJSONArray("themes").getJSONObject(0).put("inputs","anything");reject(c);reject(StudioConfig.defaults().put("schema",99));
  c=StudioConfig.defaults();c.getJSONArray("themes").getJSONObject(0).put("id","../escape");reject(c);
  c=StudioConfig.defaults();c.getJSONArray("themes").getJSONObject(0).put("wallpapers",new JSONArray().put("../../secret"));reject(c);
  c=StudioConfig.defaults();c.getJSONArray("themes").getJSONObject(0).put("rotateMinutes",-1);reject(c);
  c=StudioConfig.defaults();c.getJSONArray("themes").getJSONObject(0).put("layout","unsupported");reject(c);
  c=StudioConfig.defaults();c.getJSONArray("themes").put(StudioConfig.preset("classic"));reject(c);
  c=StudioConfig.defaults().put("weather",new JSONObject().put("mode","city").put("name","Somewhere").put("latitude",91).put("longitude",0));reject(c);
  c=StudioConfig.defaults().put("weather",new JSONObject().put("mode","city").put("name","Somewhere").put("latitude",9.1).put("longitude",76.7));StudioConfig.validate(c);cases++;
  c=StudioConfig.defaults();JSONArray images=new JSONArray();for(int i=0;i<9;i++)images.put("image_"+i);c.getJSONArray("themes").getJSONObject(0).put("wallpapers",images);reject(c);
  if(StudioConfig.validate(StudioConfig.defaults().put("unknown","ignored")).has("unknown"))throw new AssertionError("Unknown config leaked");cases++;
  c=StudioConfig.defaults();String effective=StudioConfig.effective(c);c.getJSONArray("themes").put(StudioConfig.preset("cinema").put("showPower",false));c=StudioConfig.validate(c);if(!effective.equals(StudioConfig.effective(c))||!StudioConfig.active(c).getBoolean("showPower"))throw new AssertionError("Inactive theme changed active display");cases++;
  c.put("active","cinema");if(StudioConfig.active(c).getBoolean("showPower"))throw new AssertionError("Per-theme flag lost");cases++;
  JSONObject old=new JSONObject("{\"schema\":1,\"active\":\"custom\",\"inputs\":\"hide\",\"showClock\":false,\"weather\":{\"mode\":\"auto\"},\"themes\":[{\"id\":\"classic\",\"layout\":\"classic\",\"wallpapers\":[]},{\"id\":\"custom\",\"layout\":\"cinema\",\"wallpapers\":[\"image_saved\"],\"rotateMinutes\":15}]}");
  c=StudioConfig.validate(old);if(!c.getString("active").equals("cinema")||StudioConfig.active(c).getBoolean("showClock")||!StudioConfig.active(c).getJSONArray("wallpapers").getString(0).equals("image_saved"))throw new AssertionError("Legacy selection or wallpaper lost");cases++;
  c=StudioConfig.validate(StudioConfig.defaults().put("homeName","Our living room"));if(!c.getString("homeName").equals("Our living room"))throw new AssertionError("Home name lost");cases++;reject(StudioConfig.defaults().put("homeName",""));reject(StudioConfig.defaults().put("homeName","line\nname"));
  c=StudioConfig.validate(StudioConfig.defaults().put("weather",new JSONObject().put("mode","auto")));if(!c.getJSONObject("weather").getString("mode").equals("auto"))throw new AssertionError("Automatic location lost");cases++;
  c=StudioConfig.defaults();c.remove("idleWallpaper");if(!StudioConfig.validate(c).getString("idleWallpaper").isEmpty())throw new AssertionError("Existing installs must follow theme wallpaper");cases++;
  c=StudioConfig.defaults();String themes=c.getJSONArray("themes").toString();String originalEffective=StudioConfig.effective(c);c=StudioConfig.validate(c.put("idleWallpaper","image_idle"));
  if(!c.getString("idleWallpaper").equals("image_idle")||!themes.equals(c.getJSONArray("themes").toString())||originalEffective.equals(StudioConfig.effective(c)))throw new AssertionError("Independent screensaver image lost or changed themes");cases++;
  c=StudioConfig.validate(c.put("idleWallpaper",""));if(!originalEffective.equals(StudioConfig.effective(c)))throw new AssertionError("Use theme wallpaper did not reset");cases++;
  reject(StudioConfig.defaults().put("idleWallpaper","../../secret"));reject(StudioConfig.defaults().put("idleWallpaper","https://example.com/image.jpg"));
  System.out.println("PASS: "+cases+" configuration validation cases: default privacy, theme selection, input mode, schema, path traversal, rotation, layouts, duplicate IDs, geographic range, image-count limit, unknown-field stripping.");
 }
}
