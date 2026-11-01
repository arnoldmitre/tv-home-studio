package com.gathin.tvhome;

import android.content.*;
import android.content.pm.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.net.*;
import android.os.StatFs;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Native UI for the persistent Home window and the activity fallback. */
public final class HomeSurface extends FrameLayout {
    private static final int WHITE=0xfff4f6fb, MUTED=0xffa8b4c7, ACCENT=0xffa9ead8;
    private final SharedPreferences prefs;
    private final ArrayList<App> apps=new ArrayList<>();
    private final ArrayList<String> pinned=new ArrayList<>();
    private final Set<String> hidden=new HashSet<>();
    private final LinkedHashMap<String,View> targets=new LinkedHashMap<>();
    private final ArrayList<ArrayList<View>> nav=new ArrayList<>();
    private LinearLayout content;
    private ScrollView scroll;
    private TextView hero, subtitle, weather, cinemaGreeting;
    private PopupWindow popup;
    private String selected="", page="home";
    private long builtRevision=-1;
    private boolean rebuilding;
    private boolean surfaceActive=true;
    private HomeIdle idle;
    private String layout="classic", wallpaperKey="";
    private boolean animations;
    private final ArrayList<View> sideNav=new ArrayList<>();
    private final Runnable wallpaperTick=new Runnable(){public void run(){if(!surfaceActive||!isAttachedToWindow()||getWindowVisibility()!=VISIBLE)return;String next=StudioConfig.wallpaper(getContext());if(!next.equals(wallpaperKey)){wallpaperKey=next;setBackground(HomeWallpaper.background(getContext()));}if(weather!=null)WeatherInfo.bind(weather);postDelayed(this,60000);}};
    private static int catalogRevision=0;
    private static int cachedCatalogRevision=-1;
    private static ArrayList<App> cachedApps;
    private static final class App { String pkg,name; ComponentName component; Drawable art; boolean banner; }

    public HomeSurface(Context context) {
        super(context); prefs=context.getSharedPreferences("tvhome",Context.MODE_PRIVATE);
        setFocusable(true); setFocusableInTouchMode(true); setClipChildren(false);setOnFocusChangeListener((v,f)->{if(f)heading(null);}); refresh();idle=new HomeIdle(this);
    }
    private int dp(float v){return Math.round(v*getResources().getDisplayMetrics().density);}
    private TextView text(String value,float size,int color){TextView t=new TextView(getContext());t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));t.setIncludeFontPadding(false);return t;}
    private GradientDrawable shape(int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));return d;}
    private StateListDrawable tileBackground(){StateListDrawable s=new StateListDrawable();int radius=layout.equals("classic")?14:8;GradientDrawable f=shape(layout.equals("cinema")?0xff393247:layout.equals("compact")?0xff243c63:0xff203645,radius);f.setStroke(dp(2),accent());s.addState(new int[]{android.R.attr.state_focused},f);s.addState(new int[]{},shape(layout.equals("cinema")?0xdd242131:layout.equals("compact")?0xdd18263c:0xdd192331,radius));return s;}
    private int accent(){return layout.equals("cinema")?0xffd7c6ff:layout.equals("compact")?0xffa8c6ff:ACCENT;}
    private void motion(View v,boolean focus){v.animate().cancel();float scale=focus?(layout.equals("compact")?1.025f:layout.equals("cinema")?1.045f:1f):1f;float y=focus&&!layout.equals("compact")?-dp(layout.equals("cinema")?2:2):0;if(animations)v.animate().scaleX(scale).scaleY(scale).translationY(y).setInterpolator(new android.view.animation.DecelerateInterpolator()).setDuration(layout.equals("cinema")?190:130).start();else{v.setScaleX(1);v.setScaleY(1);v.setTranslationY(0);}}
    private LinearLayout vertical(){LinearLayout l=new LinearLayout(getContext());l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(getContext());l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);l.setClipChildren(false);return l;}
    private LinearLayout.LayoutParams lp(int w,int h){return new LinearLayout.LayoutParams(w<0?w:dp(w),h<0?h:dp(h));}
    private LinearLayout.LayoutParams spaced(int w,int h,int m){LinearLayout.LayoutParams p=lp(w,h);p.leftMargin=dp(m);return p;}

    private void loadApps(){
        apps.clear();pinned.clear();hidden.clear();
        String dock=prefs.getString("dock","com.amazon.firetv.youtube,com.netflix.ninja,com.amazon.firebat,in.startv.hotstar");
        for(String p:dock.split(","))if(!p.isEmpty())pinned.add(p);
        hidden.addAll(prefs.getStringSet("hidden_v3",new HashSet<>(Arrays.asList("com.amazon.firetv.troubleshooting"))));
        if(cachedApps!=null&&cachedCatalogRevision==catalogRevision){copyCatalog();return;}
        PackageManager pm=getContext().getPackageManager();
        List<ResolveInfo> found=pm.queryIntentActivities(new Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER),0);
        HashSet<String> seen=new HashSet<>();
        for(ResolveInfo info:found){
            String pkg=info.activityInfo.packageName;
            if(pkg.equals(getContext().getPackageName())||!seen.add(pkg)||pkg.equals("com.amazon.tv.launcher"))continue;
            App a=new App();a.pkg=pkg;a.name=info.loadLabel(pm).toString();a.component=new ComponentName(pkg,info.activityInfo.name);
            Drawable banner=info.activityInfo.loadBanner(pm);a.banner=banner!=null;a.art=banner!=null?banner:info.loadIcon(pm);apps.add(a);
        }
        Collections.sort(apps,(a,b)->a.name.compareToIgnoreCase(b.name));
        cachedApps=new ArrayList<>(apps);cachedCatalogRevision=catalogRevision;
    }
    private void copyCatalog(){for(App source:cachedApps){App a=new App();a.pkg=source.pkg;a.name=source.name;a.component=source.component;a.banner=source.banner;a.art=source.art.getConstantState()==null?source.art:source.art.getConstantState().newDrawable(getResources());apps.add(a);}}
    public void refresh(){long revision=prefs.getLong("ui_revision",0)+catalogRevision;if(builtRevision==revision){if(surfaceActive&&isAttachedToWindow()&&weather!=null)WeatherInfo.bind(weather);return;}builtRevision=revision;loadApps();build();}
    private void changed(){prefs.edit().putLong("ui_revision",System.currentTimeMillis()).apply();builtRevision=-1;refresh();}
    private void saveDock(){prefs.edit().putString("dock",String.join(",",pinned)).apply();changed();}

    private void build(){
        if(idle!=null)idle.stop();cinemaGreeting=null;rebuilding=true;if(weather!=null)WeatherInfo.unbind(weather);dismissMenu();removeAllViews();targets.clear();nav.clear();
        layout=StudioConfig.layout(getContext());animations=StudioConfig.flag(getContext(),"animations");sideNav.clear();wallpaperKey=StudioConfig.wallpaper(getContext());setBackground(HomeWallpaper.background(getContext()));
        if(!layout.equals("classic")){buildAlternative();return;}
        scroll=new ScrollView(getContext());scroll.setFillViewport(true);scroll.setClipToPadding(false);scroll.setVerticalScrollBarEnabled(false);
        content=vertical();content.setPadding(dp(46),dp(20),dp(46),dp(10));content.setClipChildren(false);content.setClipToPadding(false);scroll.addView(content,new ScrollView.LayoutParams(-1,-2));addView(scroll,new LayoutParams(-1,-1));
        LinearLayout top=row();content.addView(top,lp(-1,38));TextView logo=brand(16);logo.setLetterSpacing(.22f);logo.setTypeface(null,Typeface.BOLD);top.addView(logo,new LinearLayout.LayoutParams(0,-2,1));
        weather=text("Weather",11,MUTED);weather.setSingleLine();weather.setMaxWidth(dp(230));weather.setEllipsize(android.text.TextUtils.TruncateAt.END);LinearLayout.LayoutParams wp=lp(-2,-2);wp.rightMargin=dp(18);top.addView(weather,wp);
        TextClock clock=new TextClock(getContext());clock.setTextSize(20);clock.setTextColor(WHITE);clock.setFormat12Hour("h:mm a");clock.setFormat24Hour("HH:mm");LinearLayout.LayoutParams cp=lp(-2,-2);cp.rightMargin=dp(22);top.addView(clock,cp);clock.setVisibility(StudioConfig.flag(getContext(),"showClock")?VISIBLE:GONE);
        ArrayList<View> topNav=new ArrayList<>();nav.add(topNav);
        top.addView(utility("Apps","all",()->{page="all";build();},topNav),lp(60,38));
        if(StudioConfig.flag(getContext(),"showCast"))top.addView(utility("Cast","cast",()->launch(new Intent().setComponent(new ComponentName("com.amazon.cast.sink","com.amazon.cast.sink.DisplayMirroringSinkActivity"))),topNav),spaced(52,38,4));
        top.addView(utility("✎","customize",this::customize,topNav),spaced(38,38,4));
        top.addView(utility("⚙","settings",()->launch(new Intent("amazon.intent.action.MAINSETTINGS")
            .addCategory("amazon.intent.category.MAINSETTINGS")
            .setComponent(new ComponentName("com.amazon.tv.launcher","com.amazon.tv.launcher.ui.MainSettingsActivity"))),topNav),spaced(38,38,4));
        LinearLayout heading=vertical();LinearLayout.LayoutParams hp=lp(-1,layout.equals("cinema")&&page.equals("home")?166:layout.equals("compact")?86:104);hp.topMargin=dp(layout.equals("compact")?18:24);content.addView(heading,hp);
        int hour=Calendar.getInstance().get(Calendar.HOUR_OF_DAY);TextView eyebrow=text(page.equals("home")?(hour<12?"GOOD MORNING":hour<17?"GOOD AFTERNOON":"GOOD EVENING"):page.equals("hidden")?"YOUR LIBRARY":"YOUR APPS",10,ACCENT);eyebrow.setLetterSpacing(.20f);heading.addView(eyebrow);eyebrow.setVisibility(StudioConfig.flag(getContext(),"showGreeting")?VISIBLE:GONE);
        hero=text(page.equals("home")?"What would you like to watch?":page.equals("hidden")?"Hidden apps":"All your apps, one place.",29,WHITE);hero.setSingleLine();hero.setEllipsize(android.text.TextUtils.TruncateAt.END);LinearLayout.LayoutParams title=lp(-1,-2);title.topMargin=dp(12);heading.addView(hero,title);
        subtitle=text("Select to open",12,MUTED);subtitle.setVisibility(INVISIBLE);LinearLayout.LayoutParams sub=lp(-1,-2);sub.topMargin=dp(10);heading.addView(subtitle,sub);
        ArrayList<App> favorites=new ArrayList<>(),rest=new ArrayList<>();
        for(String p:pinned)for(App a:apps)if(a.pkg.equals(p)&&!hidden.contains(p)&&favorites.size()<4)favorites.add(a);
        for(App a:apps)if(page.equals("hidden")?hidden.contains(a.pkg):!hidden.contains(a.pkg)&&(!page.equals("home")||!favorites.contains(a)))rest.add(a);
        boolean cinema=layout.equals("cinema")&&page.equals("home"), compact=layout.equals("compact")&&page.equals("home");
        if(compact){ArrayList<App> all=new ArrayList<>(favorites);all.addAll(rest);section("YOUR APPS",all.size()+" apps");appRows(all,6,false);}
        else{
            if(page.equals("home")&&!favorites.isEmpty()){section("FAVORITES",favorites.size()+" pinned");appRows(favorites,4,true);}
            if(!cinema){section(page.equals("hidden")?"HIDDEN APPS":page.equals("home")?"MORE APPS":"APP LIBRARY",rest.size()+" apps");appRows(rest,6,false);}
        }
        if(rest.isEmpty()&&!cinema&&!compact)content.addView(text(page.equals("hidden")?"No hidden apps. Use an app’s Menu to hide it.":"Your apps are all in Favorites.",13,MUTED),lp(-1,44));
        LinearLayout footer=row();LinearLayout.LayoutParams fp=lp(-1,38);fp.topMargin=dp(4);content.addView(footer,fp);footer.addView(text(StudioConfig.flag(getContext(),"showStatus")?networkStatus()+"  ·  "+storageStatus():"",11,MUTED),new LinearLayout.LayoutParams(0,-2,1));
        ArrayList<View> powerNav=new ArrayList<>();nav.add(powerNav);if(TvInputs.visible(getContext()))footer.addView(button("Inputs","inputs",this::inputsMenu,powerNav),spaced(92,34,12));if(StudioConfig.flag(getContext(),"showPower"))footer.addView(button("Power","power",this::powerMenu,powerNav),spaced(92,34,20));
        wireNavigation();rebuilding=false;if(surfaceActive&&isAttachedToWindow())WeatherInfo.bind(weather);post(()->{restoreSelection(selected,0);updateIdle();});
    }
    private TextView brand(int size){String name=StudioConfig.read(getContext()).optString("homeName","HOME");TextView label=text(name,name.length()>12?13:size,accent());label.setSingleLine();label.setEllipsize(android.text.TextUtils.TruncateAt.END);label.setContentDescription(name);return label;}
    private TextView utility(String label,String key,Runnable action,List<View> group){TextView b=button(label,key,action,group);StateListDrawable bg=new StateListDrawable();GradientDrawable focus=shape(0xff243a49,12);focus.setStroke(dp(1),accent());bg.addState(new int[]{android.R.attr.state_focused},focus);bg.addState(new int[]{},new ColorDrawable(Color.TRANSPARENT));b.setBackground(bg);b.setContentDescription(key.equals("customize")?"Customize":key.equals("settings")?"Settings":label);b.setSingleLine();b.setEllipsize(android.text.TextUtils.TruncateAt.END);b.setTextSize(label.length()==1?21:12);b.setOnFocusChangeListener((v,f)->{if(f){selected=key;heading(null);if(subtitle!=null&&label.length()==1){subtitle.setText(b.getContentDescription());subtitle.setVisibility(VISIBLE);}}});return b;}
    private void clockAndWeather(LinearLayout parent,boolean vertical){weather=text("Weather",vertical?12:11,MUTED);weather.setMaxLines(vertical?2:1);weather.setEllipsize(android.text.TextUtils.TruncateAt.END);parent.addView(weather,lp(vertical?-1:220,vertical?42:30));TextClock clock=new TextClock(getContext());clock.setTextSize(vertical?28:20);clock.setTextColor(WHITE);clock.setFormat12Hour("h:mm a");clock.setFormat24Hour("HH:mm");clock.setVisibility(StudioConfig.flag(getContext(),"showClock")?VISIBLE:GONE);parent.addView(clock,lp(-2,vertical?48:30));}
    private void headingBlock(LinearLayout parent,int size){TextView eyebrow=text(page.equals("home")?(layout.equals("cinema")?"TONIGHT, YOUR WAY":"YOUR SPACE"):"YOUR LIBRARY",10,accent());eyebrow.setLetterSpacing(.22f);eyebrow.setVisibility(StudioConfig.flag(getContext(),"showGreeting")?VISIBLE:GONE);parent.addView(eyebrow,lp(-1,22));hero=text(page.equals("home")?"What would you like to watch?":page.equals("hidden")?"Hidden apps":"All your apps, one place.",size,WHITE);hero.setMaxLines(layout.equals("cinema")?3:1);hero.setEllipsize(android.text.TextUtils.TruncateAt.END);parent.addView(hero,lp(-1,-2));subtitle=text("Select to open",12,MUTED);subtitle.setVisibility(INVISIBLE);LinearLayout.LayoutParams sub=lp(-1,24);sub.topMargin=dp(12);parent.addView(subtitle,sub);}
    private void controls(LinearLayout parent,ArrayList<View> group,boolean vertical){String[] keys={"all","cast","customize","settings"};String[] titles={"All apps","Cast","Customize","Settings"};Runnable[] actions={()->{page="all";build();},()->launch(new Intent().setComponent(new ComponentName("com.amazon.cast.sink","com.amazon.cast.sink.DisplayMirroringSinkActivity"))),this::customize,()->launch(new Intent("amazon.intent.action.MAINSETTINGS").addCategory("amazon.intent.category.MAINSETTINGS").setComponent(new ComponentName("com.amazon.tv.launcher","com.amazon.tv.launcher.ui.MainSettingsActivity")))};for(int i=0;i<keys.length;i++){if(i==1&&!StudioConfig.flag(getContext(),"showCast"))continue;TextView b=utility(titles[i],keys[i],actions[i],group);if(vertical){b.setTextSize(14);b.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);b.setPadding(dp(16),0,dp(12),0);StateListDrawable cards=new StateListDrawable();GradientDrawable focused=shape(0xff294664,12);focused.setStroke(dp(1),accent());cards.addState(new int[]{android.R.attr.state_focused},focused);cards.addState(new int[]{},shape(0x55304460,12));cards.setEnterFadeDuration(animations?120:0);cards.setExitFadeDuration(animations?120:0);b.setBackground(cards);View.OnFocusChangeListener delegate=b.getOnFocusChangeListener();b.setOnFocusChangeListener((v,f)->{delegate.onFocusChange(v,f);v.animate().cancel();if(animations)v.animate().translationX(f?dp(3):0).setInterpolator(new android.view.animation.DecelerateInterpolator()).setDuration(140).start();else v.setTranslationX(0);});}LinearLayout.LayoutParams params=lp(vertical?-1:90,vertical?44:36);if(vertical)params.topMargin=dp(8);else params.leftMargin=dp(6);parent.addView(b,params);}}
    private void footer(){LinearLayout footer=row();LinearLayout.LayoutParams fp=lp(-1,40);fp.topMargin=dp(8);content.addView(footer,fp);footer.addView(text(StudioConfig.flag(getContext(),"showStatus")?networkStatus()+"  ·  "+storageStatus():"",11,MUTED),new LinearLayout.LayoutParams(0,-2,1));ArrayList<View> group=new ArrayList<>();nav.add(group);if(TvInputs.visible(getContext()))footer.addView(utility("Inputs","inputs",this::inputsMenu,group),lp(80,36));if(StudioConfig.flag(getContext(),"showPower"))footer.addView(utility("Power","power",this::powerMenu,group),lp(80,36));}
    private void buildAlternative(){
        ArrayList<App> visible=new ArrayList<>();for(String p:pinned)for(App a:apps)if(a.pkg.equals(p)&&!hidden.contains(p)&&page.equals("home"))visible.add(a);for(App a:apps)if((page.equals("hidden")?hidden.contains(a.pkg):!hidden.contains(a.pkg))&&!visible.contains(a))visible.add(a);
        scroll=new ScrollView(getContext());scroll.setFillViewport(true);scroll.setVerticalScrollBarEnabled(false);content=vertical();content.setClipChildren(false);content.setPadding(dp(36),dp(24),dp(36),dp(20));scroll.addView(content,new ScrollView.LayoutParams(-1,-2));
        if(layout.equals("cinema")){
            addView(scroll,new LayoutParams(-1,-1));content.setPadding(dp(36),dp(20),dp(36),dp(12));LinearLayout top=row();content.addView(top,lp(-1,38));TextView brand=brand(16);brand.setLetterSpacing(.18f);top.addView(brand,new LinearLayout.LayoutParams(0,-2,1));ArrayList<View> tools=new ArrayList<>();nav.add(tools);controls(top,tools,false);
            LinearLayout stage=vertical();stage.setGravity(Gravity.CENTER_HORIZONTAL);stage.setPadding(0,dp(18),0,dp(10));content.addView(stage,lp(-1,page.equals("home")?250:140));
            if(page.equals("home")){TextClock clock=new TextClock(getContext());clock.setTextSize(50);clock.setTextColor(WHITE);clock.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));clock.setFormat12Hour("h:mm");clock.setFormat24Hour("HH:mm");clock.setVisibility(StudioConfig.flag(getContext(),"showClock")?VISIBLE:GONE);stage.addView(clock,lp(-2,64));TextClock date=new TextClock(getContext());date.setTextSize(12);date.setTextColor(MUTED);date.setFormat12Hour("EEEE, d MMMM");date.setFormat24Hour("EEEE, d MMMM");date.setVisibility(StudioConfig.flag(getContext(),"showGreeting")?VISIBLE:GONE);stage.addView(date,lp(-2,24));}
            weather=text("Weather",12,MUTED);weather.setGravity(Gravity.CENTER);stage.addView(weather,lp(-1,26));stage.addView(new View(getContext()),new LinearLayout.LayoutParams(1,0,1));
            hero=text(page.equals("home")?"What would you like to watch?":page.equals("hidden")?"Hidden apps":"All your apps, one place.",23,WHITE);hero.setGravity(Gravity.CENTER);hero.setSingleLine();hero.setEllipsize(android.text.TextUtils.TruncateAt.END);int dockWidth=Math.min(888,Math.max(160,visible.size()*112+Math.max(0,visible.size()-1)*8+32));
            LinearLayout caption=vertical();
            if(page.equals("home")){
                // Align the caption to the app artwork area, even when the dock is narrow.
                hero.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
                caption.setPadding(dp(22),0,dp(22),0);
            }
            stage.addView(caption,lp(page.equals("home")?dockWidth:-1,54));
            if(page.equals("home")){
                int hour=Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
                cinemaGreeting=text(hour<12?"GOOD MORNING":hour<17?"GOOD AFTERNOON":"GOOD EVENING",10,accent());
                cinemaGreeting.setLetterSpacing(.20f);cinemaGreeting.setGravity(Gravity.CENTER_VERTICAL);
                cinemaGreeting.setVisibility(StudioConfig.flag(getContext(),"showGreeting")?VISIBLE:INVISIBLE);
                caption.addView(cinemaGreeting,lp(-1,20));
            }
            caption.addView(hero,lp(-1,34));subtitle=text("Select to open",11,MUTED);subtitle.setGravity(page.equals("home")?Gravity.START|Gravity.CENTER_VERTICAL:Gravity.CENTER);subtitle.setVisibility(page.equals("home")?GONE:INVISIBLE);caption.addView(subtitle,lp(-1,20));
            if(page.equals("home")){LinearLayout dockHolder=row();dockHolder.setGravity(Gravity.CENTER);content.addView(dockHolder,lp(-1,136));HorizontalScrollView rail=new HorizontalScrollView(getContext());rail.setHorizontalScrollBarEnabled(false);rail.setClipToPadding(false);rail.setPadding(dp(16),dp(6),dp(16),dp(6));rail.setBackground(shape(0xd9161c2a,24));LinearLayout tiles=row();tiles.setGravity(Gravity.CENTER_VERTICAL);rail.addView(tiles);dockHolder.addView(rail,lp(dockWidth,128));ArrayList<View> railNav=new ArrayList<>();nav.add(railNav);for(int i=0;i<visible.size();i++)tiles.addView(cinemaTile(visible.get(i),railNav),spaced(112,112,i==0?0:8));}else{section("APP LIBRARY",visible.size()+" apps");appRows(visible,5,true);}

        }else{
            LinearLayout shell=row();shell.setGravity(Gravity.TOP);addView(shell,new LayoutParams(-1,-1));LinearLayout sidebar=vertical();sidebar.setPadding(dp(20),dp(30),dp(20),dp(24));sidebar.setClipChildren(false);sidebar.setClipToPadding(false);sidebar.setBackground(new ColorDrawable(0xee101b30));shell.addView(sidebar,lp(190,-1));TextView brand=brand(18);sidebar.addView(brand,lp(-1,54));controls(sidebar,sideNav,true);sidebar.addView(new View(getContext()),new LinearLayout.LayoutParams(1,0,1));LinearLayout info=vertical();info.setPadding(dp(6),dp(16),0,0);clockAndWeather(info,true);sidebar.addView(info,lp(-1,-2));shell.addView(scroll,new LinearLayout.LayoutParams(0,-1,1));content.setPadding(dp(26),dp(32),dp(30),dp(20));headingBlock(content,25);section(page.equals("hidden")?"HIDDEN APPS":"APP LIBRARY",visible.size()+" apps");appRows(visible,4,true);
        }
        if(visible.isEmpty())content.addView(text("No apps here yet. Manage your apps in Studio.",14,MUTED),lp(-1,54));footer();wireNavigation();
        if(!sideNav.isEmpty()){for(int i=0;i<sideNav.size();i++){View v=sideNav.get(i);v.setNextFocusUpId(sideNav.get(Math.max(0,i-1)).getId());v.setNextFocusDownId(sideNav.get(Math.min(sideNav.size()-1,i+1)).getId());v.setNextFocusLeftId(v.getId());v.setNextFocusRightId(nav.isEmpty()?v.getId():nav.get(0).get(0).getId());}for(ArrayList<View> group:nav)group.get(0).setNextFocusLeftId(sideNav.get(0).getId());}
        rebuilding=false;if(surfaceActive&&isAttachedToWindow())WeatherInfo.bind(weather);post(()->{restoreSelection(selected,0);updateIdle();});
    }
    private LinearLayout cinemaTile(App a,List<View> group){LinearLayout wrap=vertical();wrap.setClipChildren(false);wrap.setClipToPadding(false);wrap.setPadding(dp(6),dp(8),dp(6),0);FrameLayout tile=new FrameLayout(getContext());StateListDrawable dockFocus=new StateListDrawable();GradientDrawable ring=shape(0xff393247,16);ring.setStroke(dp(2),accent());dockFocus.addState(new int[]{android.R.attr.state_focused},ring);dockFocus.addState(new int[]{},shape(0x40374458,16));tile.setBackground(dockFocus);tile.setFocusable(true);tile.setClickable(true);tile.setId(View.generateViewId());tile.setContentDescription(a.name);wrap.addView(tile,lp(-1,72));ImageView image=new ImageView(getContext());image.setImageDrawable(a.art);image.setScaleType(ImageView.ScaleType.FIT_CENTER);image.setPadding(dp(10),dp(10),dp(10),dp(10));tile.addView(image,new LayoutParams(-1,-1));TextView label=text(a.name,12,WHITE);label.setGravity(Gravity.CENTER);label.setSingleLine();label.setEllipsize(android.text.TextUtils.TruncateAt.END);LinearLayout.LayoutParams title=lp(-1,22);title.topMargin=dp(6);wrap.addView(label,title);targets.put(a.pkg,tile);group.add(tile);tile.setOnFocusChangeListener((v,f)->{motion(v,f);if(f&&!rebuilding){selected=a.pkg;heading(a.name);}});tile.setOnClickListener(v->launch(new Intent(Intent.ACTION_MAIN).setComponent(a.component).addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)));tile.setOnLongClickListener(v->{appMenu(a);return true;});tile.setOnKeyListener((v,k,e)->{if(k==KeyEvent.KEYCODE_MENU&&e.getAction()==KeyEvent.ACTION_DOWN){appMenu(a);return true;}return false;});return wrap;}
    private void section(String title,String detail){LinearLayout r=row();LinearLayout.LayoutParams p=lp(-1,22);p.topMargin=dp(4);content.addView(r,p);TextView n=text(title,10,MUTED);n.setLetterSpacing(.16f);r.addView(n,new LinearLayout.LayoutParams(0,-2,1));r.addView(text(detail,10,MUTED));}
    private void appRows(List<App> entries,int columns,boolean favorite){
        for(int start=0;start<entries.size();start+=columns){
            LinearLayout r=row();r.setGravity(Gravity.TOP);LinearLayout.LayoutParams rp=lp(-1,(favorite?110:85)+(layout.equals("classic")?0:8));rp.topMargin=dp(5);content.addView(r,rp);ArrayList<View> group=new ArrayList<>();nav.add(group);
            for(int col=0;col<columns;col++){
                int index=start+col;LinearLayout.LayoutParams cell=new LinearLayout.LayoutParams(0,-1,1);if(col>0)cell.leftMargin=dp(12);
                if(index>=entries.size()){r.addView(new View(getContext()),cell);continue;}
                App a=entries.get(index);LinearLayout wrapper=vertical();wrapper.setClipChildren(false);wrapper.setClipToPadding(false);if(!layout.equals("classic"))wrapper.setPadding(dp(6),dp(6),dp(6),0);r.addView(wrapper,cell);
                FrameLayout tile=new FrameLayout(getContext());tile.setPadding(dp(6),dp(6),dp(6),dp(6));tile.setBackground(tileBackground());tile.setFocusable(true);tile.setClickable(true);tile.setId(View.generateViewId());tile.setContentDescription(a.name);wrapper.addView(tile,lp(-1,favorite?78:56));
                ImageView art=new ImageView(getContext());art.setImageDrawable(a.art);art.setScaleType(ImageView.ScaleType.FIT_CENTER);art.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);tile.addView(art,new FrameLayout.LayoutParams(a.banner?-1:dp(favorite?57:38),a.banner?-1:dp(favorite?57:38),Gravity.CENTER));
                TextView label=text(a.name,favorite?13:11,WHITE);label.setSingleLine(true);label.setEllipsize(android.text.TextUtils.TruncateAt.END);LinearLayout.LayoutParams np=lp(-1,19);np.topMargin=dp(6);label.setGravity(Gravity.CENTER);wrapper.addView(label,np);targets.put(a.pkg,tile);group.add(tile);
                tile.setOnFocusChangeListener((v,f)->{motion(v,f);if(f&&!rebuilding){selected=a.pkg;heading(a.name);}});
                tile.setOnClickListener(v->{if(page.equals("hidden"))appMenu(a);else launch(new Intent(Intent.ACTION_MAIN).setComponent(a.component).addFlags(Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED));});tile.setOnLongClickListener(v->{appMenu(a);return true;});tile.setOnKeyListener((v,k,e)->{if(k==KeyEvent.KEYCODE_MENU&&e.getAction()==KeyEvent.ACTION_DOWN){appMenu(a);return true;}return false;});
            }
        }
    }
    private void heading(String name){
        if(cinemaGreeting!=null){
            int hour=Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
            cinemaGreeting.setText(hour<12?"GOOD MORNING":hour<17?"GOOD AFTERNOON":"GOOD EVENING");
            cinemaGreeting.setVisibility(name!=null?GONE:StudioConfig.flag(getContext(),"showGreeting")?VISIBLE:INVISIBLE);
        }
        if(hero!=null&&page.equals("home")){String title=name==null?"What would you like to watch?":name;if(!android.text.TextUtils.equals(hero.getText(),title)){hero.setText(title);hero.animate().cancel();hero.setAlpha(1);}}
        if(subtitle!=null){String hint=page.equals("hidden")?"App options":"Select to open";if(!android.text.TextUtils.equals(subtitle.getText(),hint))subtitle.setText(hint);subtitle.setVisibility(name==null?(cinemaGreeting!=null?GONE:INVISIBLE):VISIBLE);}
    }
    private TextView button(String title,String key,Runnable action,List<View> group){TextView b=text(title,12,WHITE);b.setGravity(Gravity.CENTER);b.setBackground(tileBackground());b.setFocusable(true);b.setClickable(true);b.setId(View.generateViewId());b.setOnClickListener(v->action.run());b.setOnFocusChangeListener((v,f)->{if(f){selected=key;heading(null);}});group.add(b);targets.put(key,b);if(key.equals("settings")){b.setOnLongClickListener(v->{customize();return true;});b.setOnKeyListener((v,k,e)->{if(k==KeyEvent.KEYCODE_MENU&&e.getAction()==KeyEvent.ACTION_DOWN){customize();return true;}return false;});}return b;}
    private void wireNavigation(){nav.removeIf(List::isEmpty);for(int r=0;r<nav.size();r++)for(int c=0;c<nav.get(r).size();c++){View v=nav.get(r).get(c);ArrayList<View> row=nav.get(r);v.setNextFocusLeftId(row.get(Math.max(0,c-1)).getId());v.setNextFocusRightId(row.get(Math.min(row.size()-1,c+1)).getId());v.setNextFocusUpId(r>0?nav.get(r-1).get(Math.min(c,nav.get(r-1).size()-1)).getId():v.getId());v.setNextFocusDownId(r+1<nav.size()?nav.get(r+1).get(Math.min(c,nav.get(r+1).size()-1)).getId():v.getId());}}
    private void appMenu(App app){
        List<String> labels=new ArrayList<>();List<Runnable> actions=new ArrayList<>();boolean isHidden=hidden.contains(app.pkg),isPinned=pinned.contains(app.pkg);
        labels.add(isPinned?"Remove from Favorites":"Add to Favorites");actions.add(()->{if(isPinned)pinned.remove(app.pkg);else if(pinned.size()<4)pinned.add(app.pkg);else{Toast.makeText(getContext(),"Four favorites maximum. Remove one first.",Toast.LENGTH_LONG).show();return;}saveDock();});
        int index=pinned.indexOf(app.pkg);if(index>0){labels.add("Move left");actions.add(()->{Collections.swap(pinned,index,index-1);saveDock();});}if(index>=0&&index<pinned.size()-1){labels.add("Move right");actions.add(()->{Collections.swap(pinned,index,index+1);saveDock();});}
        labels.add(isHidden?"Show on Home":"Hide from Home");actions.add(()->{if(isHidden)hidden.remove(app.pkg);else hidden.add(app.pkg);prefs.edit().putStringSet("hidden_v3",new HashSet<>(hidden)).apply();changed();});labels.add("App settings");actions.add(()->launch(new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,Uri.parse("package:"+app.pkg))));menu(app.name,labels,actions);
    }
    private void idleMenu(){menu("Home idle screen",Arrays.asList("On: wallpaper at 2 min, sleep at 15 min","Off: use Fire OS idle settings"),Arrays.asList(()->setIdle(true),()->setIdle(false)));}
    private void setIdle(boolean enabled){try{StudioConfig.save(getContext(),StudioConfig.read(getContext()).put("idleScreen",enabled));}catch(Exception e){Toast.makeText(getContext(),"Could not save idle setting",Toast.LENGTH_SHORT).show();}}
    private void customize(){
        List<String> names=new ArrayList<>(Arrays.asList("Home screen","Themes","Wallpaper source","Home idle screen","Hidden apps","Network settings"));
        List<Runnable> actions=new ArrayList<>(Arrays.asList(this::homeMenu,this::themeMenu,this::wallpaperSourceMenu,this::idleMenu,()->{page="hidden";selected="all";build();},()->launch(new Intent(Settings.ACTION_WIFI_SETTINGS))));
        if(StudioConfig.theme(getContext()).optJSONArray("wallpapers").length()>1){names.add("Next wallpaper");actions.add(()->{String key="wallpaper_index_"+StudioConfig.theme(getContext()).optString("id");prefs.edit().putInt(key,prefs.getInt(key,0)+1).apply();setBackground(HomeWallpaper.background(getContext()));});}
        menu("Make yourself at home",names,actions);
    }
    private void wallpaperSourceMenu(){
        String cur=StudioConfig.theme(getContext()).optString("wallpaperSource","custom");
        List<String> names=Arrays.asList(
            "Bing daily landscapes"+(cur.equals("bing")?"  ·  Current":""),
            "NASA astronomy (APOD)"+(cur.equals("nasa")?"  ·  Current":""),
            "Custom / phone uploads"+(cur.equals("custom")?"  ·  Current":"")
        );
        List<Runnable> actions=Arrays.asList(
            ()->setWallpaperSource("bing"),
            ()->setWallpaperSource("nasa"),
            ()->setWallpaperSource("custom")
        );
        menu("Wallpaper source",names,actions);
    }
    private void setWallpaperSource(String source){
        try{
            JSONObject config=StudioConfig.read(getContext());
            JSONObject active=StudioConfig.active(config);
            active.put("wallpaperSource",source);
            if(!source.equals("custom")&&active.optInt("rotateMinutes",0)==0){
                active.put("rotateMinutes",30);
            }
            StudioConfig.save(getContext(),config);
            if(source.equals("custom")){
                Toast.makeText(getContext(),"Using custom wallpapers",Toast.LENGTH_SHORT).show();
                return;
            }
            Toast.makeText(getContext(),"Fetching "+source.toUpperCase()+" wallpapers…",Toast.LENGTH_SHORT).show();
            WallpaperSync.sync(getContext(),source,()->{
                post(()->{
                    wallpaperKey=StudioConfig.wallpaper(getContext());
                    setBackground(HomeWallpaper.background(getContext()));
                    Toast.makeText(getContext(),"Wallpapers updated!",Toast.LENGTH_SHORT).show();
                });
            });
        }catch(Exception e){Toast.makeText(getContext(),"Could not update wallpaper source",Toast.LENGTH_SHORT).show();}
    }
    private void themeMenu(){
        List<String> names=new ArrayList<>();List<Runnable> actions=new ArrayList<>();
        String active=StudioConfig.read(getContext()).optString("active");
        for(String id:StudioConfig.DESIGNS){
            String name=id.equals("classic")?"Classic":id.equals("cinema")?"Cinema":"Panel";
            names.add(name+(id.equals(active)?"  ·  Current":""));
            actions.add(()->{StudioConfig.activate(getContext(),id);page="home";selected="";refresh();});
        }
        menu("Choose a theme",names,actions);
    }
    private void homeMenu(){menu("Home screen",Arrays.asList("Default home","This home"),Arrays.asList(()->chooseHome(false),()->chooseHome(true)));}
    private void chooseHome(boolean custom){if(!HomeRedirectService.chooseHome(getContext(),custom))Toast.makeText(getContext(),"Connect TV Home Studio to enable Home switching",Toast.LENGTH_LONG).show();}
    private void inputsMenu(){List<String> names=new ArrayList<>();List<Runnable> actions=new ArrayList<>();for(android.media.tv.TvInputInfo input:TvInputs.list(getContext())){names.add(input.loadLabel(getContext()).toString());actions.add(()->launch(TvInputs.open(input)));}if(names.isEmpty()){Toast.makeText(getContext(),"No TV inputs reported by this device",Toast.LENGTH_LONG).show();return;}menu("Inputs",names,actions);}
    private void powerMenu(){
        menu("Power",Arrays.asList("Sleep","Restart"),Arrays.asList(
            ()->{if(!HomeRedirectService.sleep())Toast.makeText(getContext(),"Sleep is unavailable. Enable this Home in TV Home Studio.",Toast.LENGTH_LONG).show();},
            ()->{if(!HomeRedirectService.requestRestart())Toast.makeText(getContext(),"Restart is unavailable. Enable this Home in TV Home Studio.",Toast.LENGTH_LONG).show();}));
    }
    private void menu(String title,List<String> labels,List<Runnable> actions){
        dismissMenu();LinearLayout panel=vertical();panel.setPadding(dp(24),dp(22),dp(24),dp(22));panel.setBackground(shape(0xff101b29,20));
        TextView titleView=text(title,20,WHITE);titleView.setPadding(0,0,0,dp(20));panel.addView(titleView,lp(-1,-2));
        ScrollView options=new ScrollView(getContext());options.setVerticalScrollBarEnabled(false);LinearLayout rows=vertical();rows.setPadding(dp(2),dp(2),dp(2),dp(2));options.addView(rows);panel.addView(options,new LinearLayout.LayoutParams(-1,0,1));
        for(int i=0;i<labels.size();i++){final Runnable action=actions.get(i);TextView b=text(labels.get(i),15,WHITE);b.setGravity(Gravity.CENTER_VERTICAL);b.setPadding(dp(16),dp(12),dp(16),dp(12));b.setMinHeight(dp(46));b.setBackground(tileBackground());b.setFocusable(true);b.setClickable(true);LinearLayout.LayoutParams item=lp(-1,-2);if(i>0)item.topMargin=dp(10);rows.addView(b,item);b.setOnClickListener(v->{dismissMenu();action.run();});}
        boolean custom=title.equals("Make yourself at home");if(custom){TextView credit=text("Wallpapers and theme options: TV Home Studio on your phone.\n"+WeatherInfo.ATTRIBUTION,10,MUTED);credit.setLineSpacing(dp(3),1);credit.setPadding(0,dp(16),0,0);panel.addView(credit,lp(-1,-2));}
        int width=Math.min(dp(390),getWidth()-dp(48));panel.measure(MeasureSpec.makeMeasureSpec(width,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(0,MeasureSpec.UNSPECIFIED));int height=Math.min(getHeight()-dp(48),dp(94+labels.size()*56+(custom?60:0)));
        popup=new PopupWindow(panel,width,Math.max(dp(190),height),true);popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));popup.setOutsideTouchable(true);popup.setElevation(dp(20));popup.showAtLocation(this,Gravity.CENTER,0,0);rows.getChildAt(0).requestFocus();
    }

    public void dismissMenu(){if(popup!=null){popup.dismiss();popup=null;}}
    public boolean hasMenu(){return popup!=null&&popup.isShowing();}
    public String selection(){return selected;} public String page(){return page;} public int scrollPosition(){return scroll==null?0:scroll.getScrollY();}
    public void restorePage(String value){if(!page.equals(value)){page=value;build();}}
    public void restoreSelection(String key,int y){
        selected=key==null?"":key;
        View target=targets.get(selected);
        if(target!=null)target.requestFocus();else{selected="";requestFocus();}
        String name=null;for(App a:apps)if(a.pkg.equals(selected)){name=a.name;break;}heading(name);
        scroll.scrollTo(0,Math.max(0,y));
    }
    public void goHome(){dismissMenu();selected="";if(!page.equals("home")){page="home";build();}else{requestFocus();scroll.scrollTo(0,0);}}
    @Override public boolean dispatchKeyEvent(KeyEvent e){
        if(idle!=null&&idle.key(e))return true;
        if(e.getKeyCode()==KeyEvent.KEYCODE_BACK){if(e.getAction()==KeyEvent.ACTION_UP)goHome();return true;}
        if(findFocus()==this){
            int key=e.getKeyCode();
            if(key==KeyEvent.KEYCODE_DPAD_UP||key==KeyEvent.KEYCODE_DPAD_DOWN||key==KeyEvent.KEYCODE_DPAD_LEFT||key==KeyEvent.KEYCODE_DPAD_RIGHT||key==KeyEvent.KEYCODE_DPAD_CENTER){
                if(e.getAction()==KeyEvent.ACTION_DOWN){
                    String first="all";
                    if(key!=KeyEvent.KEYCODE_DPAD_UP)for(String p:pinned)if(targets.containsKey(p)){first=p;break;}
                    restoreSelection(first,0);
                }
                return true;
            }
        }
        return super.dispatchKeyEvent(e);
    }
    private void launch(Intent intent){HomeRedirectService.launchFromSurface(this,intent);}
    private String networkStatus(){ConnectivityManager cm=(ConnectivityManager)getContext().getSystemService(Context.CONNECTIVITY_SERVICE);NetworkCapabilities cap=cm.getNetworkCapabilities(cm.getActiveNetwork());return cap==null?"Offline":cap.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)?"Connected":"No internet";}
    private String storageStatus(){StatFs fs=new StatFs(getContext().getFilesDir().getPath());return String.format(Locale.getDefault(),"%.1f GB free",fs.getAvailableBytes()/1073741824.0);}
    void setSurfaceActive(boolean active){
        surfaceActive=active;removeCallbacks(wallpaperTick);
        updateIdle();
        if(!active){if(weather!=null)WeatherInfo.unbind(weather);}
        else if(isAttachedToWindow())post(wallpaperTick);
    }
    private void updateIdle(){if(idle!=null)idle.active(surfaceActive&&isAttachedToWindow()&&getWindowVisibility()==VISIBLE&&getVisibility()==VISIBLE&&hasWindowFocus()&&StudioConfig.read(getContext()).optBoolean("idleScreen",true));}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);updateIdle();}
    @Override protected void onAttachedToWindow(){
        super.onAttachedToWindow();
        updateIdle();
        if(weather!=null)WeatherInfo.bind(weather);
        String src=StudioConfig.theme(getContext()).optString("wallpaperSource","custom");
        if(!src.equals("custom")){
            WallpaperSync.checkDaily(getContext(),src,()->post(()->{
                wallpaperKey=StudioConfig.wallpaper(getContext());
                setBackground(HomeWallpaper.background(getContext()));
            }));
        }
        removeCallbacks(wallpaperTick);
        postDelayed(wallpaperTick,60000);
    }
    @Override protected void onWindowVisibilityChanged(int visibility){super.onWindowVisibilityChanged(visibility);updateIdle();if(wallpaperTick!=null){removeCallbacks(wallpaperTick);if(surfaceActive&&visibility==VISIBLE&&isAttachedToWindow())post(wallpaperTick);}}
    @Override protected void onDetachedFromWindow(){if(idle!=null)idle.stop();removeCallbacks(wallpaperTick);if(weather!=null)WeatherInfo.unbind(weather);super.onDetachedFromWindow();}
    public static void invalidateApps(){catalogRevision++;}
}
