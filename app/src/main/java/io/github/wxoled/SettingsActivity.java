package io.github.wxoled;

import android.app.*;
import android.content.*;
import android.graphics.Color;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.lang.ref.WeakReference;
import java.net.*;
import java.util.regex.*;

public class SettingsActivity extends Activity {
    private static final String WECHAT="com.tencent.mm", REPO="https://github.com/gxwangpai007-ops/wechat-oled-black", LATEST=REPO.replace("github.com/","api.github.com/repos/")+"/releases/latest";
    private final Handler main=new Handler(Looper.getMainLooper());
    private Button restart, checkUpdate;
    @Override protected void onCreate(Bundle state) { super.onCreate(state); setContentView(page()); }
    private View page() {
        int p=dp(20); ScrollView scroll=new ScrollView(this); scroll.setFillViewport(true); scroll.setBackgroundColor(Color.BLACK);
        LinearLayout box=new LinearLayout(this); box.setOrientation(LinearLayout.VERTICAL); box.setPadding(p,dp(32),p,dp(28)); scroll.addView(box);
        box.addView(text(getString(R.string.app_name),28,Color.WHITE));
        TextView version=text(getString(R.string.current_version,BuildConfig.VERSION_NAME),14,Color.rgb(160,160,160)); version.setPadding(0,dp(6),0,dp(20)); box.addView(version);
        box.addView(card(R.string.environment_title,R.string.environment_details)); box.addView(card(R.string.compatibility_title,R.string.compatibility_details));
        restart=button(R.string.restart_wechat,new View.OnClickListener(){public void onClick(View v){restartWeChat();}}); box.addView(restart,params(dp(56),dp(18)));
        checkUpdate=button(R.string.check_update,new View.OnClickListener(){public void onClick(View v){checkForUpdates();}}); box.addView(checkUpdate,params(dp(52),dp(10)));
        box.addView(button(R.string.open_releases,link(REPO+"/releases")),params(dp(52),dp(10))); box.addView(button(R.string.open_source,link(REPO)),params(dp(52),dp(10)));
        box.addView(button(R.string.report_issue,link(REPO+"/issues/new/choose")),params(dp(52),dp(10))); box.addView(button(R.string.open_license,link(REPO+"/blob/main/LICENSE")),params(dp(52),dp(10)));
        box.addView(card(R.string.privacy_title,R.string.privacy_details)); box.addView(card(R.string.disclaimer_title,R.string.disclaimer_details)); return scroll;
    }
    private TextView text(String value,int size,int color){ TextView v=new TextView(this); v.setText(value); v.setTextSize(size); v.setTextColor(color); return v; }
    private LinearLayout card(int title,int details){ LinearLayout v=new LinearLayout(this); v.setOrientation(LinearLayout.VERTICAL); v.setBackgroundColor(Color.rgb(25,25,25)); v.setPadding(dp(16),dp(15),dp(16),dp(15)); v.addView(text(getString(title),17,Color.WHITE)); TextView d=text(getString(details),14,Color.rgb(185,185,185)); d.setPadding(0,dp(7),0,0); d.setLineSpacing(0,1.25f); v.addView(d); v.setLayoutParams(params(-2,dp(12))); return v; }
    private Button button(int label,View.OnClickListener listener){ Button b=new Button(this); b.setText(label); b.setAllCaps(false); b.setTextSize(16); b.setTextColor(Color.WHITE); b.setBackgroundColor(Color.rgb(48,48,48)); b.setOnClickListener(listener); return b; }
    private LinearLayout.LayoutParams params(int height,int top){ LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,height); p.topMargin=top; return p; }
    private View.OnClickListener link(final String url){ return new View.OnClickListener(){public void onClick(View v){try{startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));}catch(ActivityNotFoundException e){toast(R.string.no_browser);}catch(RuntimeException e){toast(R.string.open_link_failed);}}}; }
    private void restartWeChat(){ final Intent launch=getPackageManager().getLaunchIntentForPackage(WECHAT); if(launch==null){toast(R.string.wechat_missing);return;} restart.setEnabled(false); ActivityManager m=(ActivityManager)getSystemService(ACTIVITY_SERVICE); if(m!=null)m.killBackgroundProcesses(WECHAT); sendOrderedBroadcast(new Intent("io.github.wxoled.action.RESTART_WECHAT").setPackage(WECHAT).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),null,new BroadcastReceiver(){public void onReceive(Context c,Intent i){Bundle x=getResultExtras(false); final PendingIntent pi=x==null?null:(PendingIntent)x.getParcelable("relaunch"); main.postDelayed(new Runnable(){public void run(){if(isFinishing())return; restart.setEnabled(true); openWeChat(pi,launch);}},getResultCode()==1?900:0);}},main,0,null,null); }
    private void openWeChat(PendingIntent pi,Intent launch){if(pi!=null)try{ActivityOptions o=ActivityOptions.makeBasic();if(Build.VERSION.SDK_INT>=34)o.setPendingIntentBackgroundActivityStartMode(1);pi.send(this,0,null,null,null,null,o.toBundle());return;}catch(PendingIntent.CanceledException ignored){} launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);startActivity(launch);}
    private void checkForUpdates(){checkUpdate.setEnabled(false);toast(R.string.checking_update);final WeakReference<SettingsActivity> ref=new WeakReference<>(this);new Thread(new Runnable(){public void run(){fetch(ref);}},"wxoled-update-check").start();}
    private static void fetch(final WeakReference<SettingsActivity> ref){HttpURLConnection c=null;try{c=(HttpURLConnection)new URL(LATEST).openConnection();c.setConnectTimeout(8000);c.setReadTimeout(8000);c.setRequestProperty("Accept","application/vnd.github+json");c.setRequestProperty("User-Agent","WeChatOLED-Android/1.5.23");if(c.getResponseCode()!=200)throw new IOException();String json=read(c.getInputStream());post(ref,new Release(field(json,"tag_name"),field(json,"html_url"),field(json,"name"),field(json,"body")),null);}catch(Exception e){post(ref,null,e);}finally{if(c!=null)c.disconnect();}}
    private static String read(InputStream in)throws IOException{BufferedReader r=new BufferedReader(new InputStreamReader(in,"UTF-8"));try{StringBuilder b=new StringBuilder();String l;while((l=r.readLine())!=null)b.append(l);return b.toString();}finally{r.close();}}
    private static String field(String json,String key){Matcher m=Pattern.compile("\\\""+key+"\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"").matcher(json);return m.find()?m.group(1).replace("\\\\n","\\n").replace("\\\\\"","\"").replace("\\\\\\\\","\\"):null;}
    private static void post(final WeakReference<SettingsActivity> ref,final Release release,final Exception error){SettingsActivity a=ref.get();if(a==null)return;a.main.post(new Runnable(){public void run(){SettingsActivity x=ref.get();if(x==null||x.isFinishing())return;x.checkUpdate.setEnabled(true);if(error!=null||release==null||release.tag==null){x.toast(R.string.update_failed);return;}int versionOrder=compare(release.tag,BuildConfig.VERSION_NAME);if(versionOrder<0){x.toast(R.string.local_version_newer);return;}if(versionOrder==0){x.toast(R.string.already_latest);return;}x.toast(x.getString(R.string.update_found,release.name==null||release.name.length()==0?release.tag:release.name));if(release.url!=null)x.link(release.url).onClick(x.checkUpdate);}});}
    private static int compare(String a,String b){String[] x=clean(a).split("\\."),y=clean(b).split("\\.");for(int i=0;i<Math.max(x.length,y.length);i++){int p=i<x.length?number(x[i]):0,q=i<y.length?number(y[i]):0;if(p!=q)return p<q?-1:1;}return 0;}
    private static String clean(String s){s=s==null?"":s.trim();if(s.startsWith("v")||s.startsWith("V"))s=s.substring(1);int p=s.indexOf('-');return p<0?s:s.substring(0,p);}
    private static int number(String s){try{return Integer.parseInt(s.replaceAll("[^0-9]",""));}catch(NumberFormatException e){return 0;}}
    private void toast(int id){Toast.makeText(this,id,Toast.LENGTH_LONG).show();} private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();} private int dp(int n){return Math.round(n*getResources().getDisplayMetrics().density);}
    private static final class Release{final String tag,url,name,body;Release(String t,String u,String n,String b){tag=t;url=u;name=n;body=b;}}
}
