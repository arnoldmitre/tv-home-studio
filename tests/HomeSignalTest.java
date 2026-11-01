package com.gathin.tvhome;
public final class HomeSignalTest {
 private static int checks;
 public static void main(String[] args){
  String start="I/ActivityManager(  527): START u0 {act=android.intent.action.MAIN cat=[android.intent.category.HOME] flg=0x10000000 cmp=com.amazon.tv.launcher/.ui.HomeActivity_vNext} from uid 1000";
  check(HomeSignal.kind(start)==HomeSignal.SYSTEM_HOME,"system Home");
  check(HomeSignal.kind(start.replace("uid 1000","uid 0"))==HomeSignal.SYSTEM_HOME,"root Home");
  check(HomeSignal.kind(start.replace("uid 1000","uid 10207"))==HomeSignal.APP_HOME,"observed YouTube Back");
  check(HomeSignal.kind(start.replace("uid 1000","uid 10599"))==HomeSignal.APP_HOME,"future app without allowlist");
  check(HomeSignal.kind(start.replace("uid 1000","uid 10000"))==HomeSignal.APP_HOME,"app UID prefix not system UID");
  check(HomeSignal.matches(start.replace("/.ui.","/com.amazon.tv.launcher.ui.")),"full component");
  for(String invalid:new String[]{start.replace("HomeActivity_vNext","MainSettingsActivity"),start.replace("START u0","START u10"),start.replace("category.HOME","category.HOME_FAKE"),start.replace("action.MAIN ","action.VIEW "),start.replace("uid 1000","uid 1000bad"),start.replace("cmp=com.amazon.tv.launcher/","cmp=other.package/"),""})check(!HomeSignal.matches(invalid),"irrelevant start rejected");
  String resume="I/am_resume_activity(  527): [0,233446223,1045,com.amazon.tv.launcher/.ui.HomeActivity_vNext]";
  check(HomeSignal.kind(resume)==HomeSignal.RESUMED_HOME,"return to existing Home task");
  check(HomeSignal.kind(resume.replace("am_resume_activity","wm_resume_activity"))==HomeSignal.RESUMED_HOME,"newer resume tag");
  check(!HomeSignal.matches(resume.replace("[0,","[10,")),"other-user resume");
  check(!HomeSignal.matches(resume.replace("HomeActivity_vNext","MainSettingsActivity")),"settings resume");
  check(!HomeSignal.matches(resume.replace("am_resume_activity","am_pause_activity")),"pause is not resume");
  String externalResume="I/am_resume_activity(  527): [0,61775138,1173,com.github.damontecres.wholphin/.MainActivity]";
  check(HomeSignal.kind(externalResume)==HomeSignal.EXTERNAL_RESUME,"external app resume");
  check(!HomeSignal.matches(externalResume),"external app is not home");
  String settingsResume="I/am_resume_activity(  527): [0,12345,1175,com.amazon.tv.settings/.MainActivity]";
  check(HomeSignal.kind(settingsResume)==HomeSignal.SETTINGS_RESUME,"settings app resume");
  check(!HomeSignal.matches(settingsResume),"settings app is not home");
  String tvhomeResume="I/am_resume_activity(  527): [0,262508959,1180,com.gathin.tvhome/.MainActivity]";
  check(HomeSignal.kind(tvhomeResume)==HomeSignal.TVHOME_RESUME,"tvhome resume");
  check(!HomeSignal.matches(tvhomeResume),"tvhome resume is not amazon home");
  for(int kind:new int[]{HomeSignal.SYSTEM_HOME,HomeSignal.APP_HOME,HomeSignal.RESUMED_HOME}){
   check(HomeSignal.shouldRedirect(kind,false,false,false),"normal Home return");
   check(!HomeSignal.shouldRedirect(kind,true,false,false),"external launch preserved");
  }
  for(int kind:new int[]{HomeSignal.APP_HOME,HomeSignal.RESUMED_HOME}){
   check(!HomeSignal.shouldRedirect(kind,false,true,false),"settings grace preserved");
   check(!HomeSignal.shouldRedirect(kind,false,false,true),"explicit bypass preserved");
  }
  check(HomeSignal.shouldRedirect(HomeSignal.SYSTEM_HOME,false,true,true),"physical Home overrides bypass");
  check(!HomeSignal.shouldRedirect(HomeSignal.NONE,false,false,false),"no signal");
  System.out.println("PASS: "+checks+" Home-return parser and routing checks.");
 }
 private static void check(boolean okay,String name){checks++;if(!okay)throw new AssertionError(name);}
}
