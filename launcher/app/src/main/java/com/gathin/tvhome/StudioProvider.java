package com.gathin.tvhome;

import android.content.*;
import android.content.pm.*;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.*;
import android.util.Base64;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

/** No network listener: only authenticated ADB shell/system can configure the launcher. */
public final class StudioProvider extends ContentProvider {
    private void authorize(){int uid=Binder.getCallingUid();if(uid!=2000&&uid!=1000&&uid!=android.os.Process.myUid())throw new SecurityException("Only the authorized ADB shell can use Studio");}
    @Override public boolean onCreate(){return true;}
    @Override public Bundle call(String method,String arg,Bundle extras){
        authorize();Bundle result=new Bundle();
        try{
            Context c=getContext();JSONObject response=new JSONObject();
            switch(method){
                case "read":response=StudioConfig.read(c);response.put("launcherVersion",18);response.put("homeHealth",HomeRedirectService.health(c));response.put("weatherStatus",WeatherInfo.status(c));response.put("home",HomeRedirectService.isEnabled(c)?"this":"default");response.put("inputsDetected",TvInputs.available(c));response.put("dock",StudioConfig.prefs(c).getString("dock","com.amazon.firetv.youtube,com.netflix.ninja,com.amazon.firebat,in.startv.hotstar"));response.put("hidden",new JSONArray(StudioConfig.prefs(c).getStringSet("hidden_v3",new java.util.HashSet<>(java.util.Arrays.asList("com.amazon.firetv.troubleshooting")))));break;
                case "save":if(arg==null||arg.length()>60000)throw new IllegalArgumentException("Configuration too large");StudioConfig.save(c,new JSONObject(new String(Base64.decode(arg,Base64.URL_SAFE),StandardCharsets.UTF_8)));response.put("saved",true);break;
                case "home":if(!"this".equals(arg)&&!"default".equals(arg))throw new IllegalArgumentException("Choose a Home screen");if(!HomeRedirectService.chooseHome(c,"this".equals(arg)))throw new IllegalStateException("Grant WRITE_SECURE_SETTINGS first");response.put("home",arg);break;
                case "repairHome":response.put("previousConnection",HomeRedirectService.health(c).optLong("connectionEpoch"));if(!HomeRedirectService.repairHome(c))throw new IllegalStateException("Select This home first");response.put("requested",true);break;
                case "weatherRetry":WeatherInfo.retry();response.put("requested",true);break;
                case "apps":JSONArray apps=new JSONArray();for(ResolveInfo r:c.getPackageManager().queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),0))if(!r.activityInfo.packageName.equals(c.getPackageName())&&!r.activityInfo.packageName.equals("com.amazon.tv.launcher"))apps.put(new JSONObject().put("package",r.activityInfo.packageName).put("name",r.loadLabel(c.getPackageManager()).toString()));response.put("apps",apps);break;
                case "appsSave":JSONObject changes=new JSONObject(new String(Base64.decode(arg,Base64.URL_SAFE),StandardCharsets.UTF_8));JSONArray dock=changes.getJSONArray("dock"),hidden=changes.getJSONArray("hidden");if(dock.length()>4||hidden.length()>300)throw new IllegalArgumentException("Too many apps");java.util.ArrayList<String> pins=new java.util.ArrayList<>();java.util.Set<String> hide=new java.util.HashSet<>();for(int i=0;i<dock.length();i++){String p=dock.getString(i);validPackage(p);if(!pins.contains(p))pins.add(p);}for(int i=0;i<hidden.length();i++){String p=hidden.getString(i);validPackage(p);hide.add(p);}StudioConfig.prefs(c).edit().putString("dock",String.join(",",pins)).putStringSet("hidden_v3",hide).putLong("ui_revision",StudioConfig.prefs(c).getLong("ui_revision",0)+1).commit();HomeRedirectService.refreshHome();response.put("saved",true);break;
                case "commitImage":File target=StudioConfig.image(c,arg),stage=new File(target.getPath()+".upload");if(stage.length()<1||stage.length()>3*1024*1024)throw new IllegalArgumentException("Wallpaper must be under 3 MB");BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeFile(stage.getPath(),o);if(o.outWidth<1||o.outHeight<1||o.outWidth>4096||o.outHeight>4096||(long)o.outWidth*o.outHeight>8500000)throw new IllegalArgumentException("Wallpaper dimensions too large");target.delete();if(!stage.renameTo(target))throw new IOException("Failed to commit image");response.put("image",arg);break;
                default:throw new IllegalArgumentException("Unknown Studio action");
            }
            result.putString("payload",Base64.encodeToString(response.toString().getBytes(StandardCharsets.UTF_8),Base64.URL_SAFE|Base64.NO_WRAP));
        }catch(Exception e){result.putString("error",e.getMessage()==null?e.getClass().getSimpleName():e.getMessage());}
        return result;
    }
    private void validPackage(String p){if(!p.matches("[a-zA-Z][a-zA-Z0-9_.]{1,180}")||p.equals(getContext().getPackageName())||p.equals("com.amazon.tv.launcher"))throw new IllegalArgumentException("Invalid app");}
    @Override public ParcelFileDescriptor openFile(Uri uri,String mode)throws FileNotFoundException{
        authorize();if(!mode.equals("w")||uri.getPathSegments().size()!=2||!"wallpaper".equals(uri.getPathSegments().get(0)))throw new FileNotFoundException("Unsupported path");
        File target=StudioConfig.image(getContext(),uri.getLastPathSegment());long total=0;File[] files=target.getParentFile().listFiles();if(files!=null)for(File f:files)total+=f.length();if(total>64L*1024*1024)throw new FileNotFoundException("Wallpaper storage limit reached");
        return ParcelFileDescriptor.open(new File(target.getPath()+".upload"),ParcelFileDescriptor.MODE_CREATE|ParcelFileDescriptor.MODE_TRUNCATE|ParcelFileDescriptor.MODE_WRITE_ONLY);
    }
    @Override public Cursor query(Uri u,String[] p,String s,String[] a,String sort){throw new UnsupportedOperationException();}
    @Override public String getType(Uri u){return "application/octet-stream";}
    @Override public Uri insert(Uri u,ContentValues v){throw new UnsupportedOperationException();}
    @Override public int delete(Uri u,String s,String[] a){throw new UnsupportedOperationException();}
    @Override public int update(Uri u,ContentValues v,String s,String[] a){throw new UnsupportedOperationException();}
}
