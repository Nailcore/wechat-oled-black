package io.github.wxoled;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.webkit.WebView;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

final class ArticlePageBackgroundHook {
    private static boolean attachHooked;
    private static boolean installed;
    private static boolean logged;
    private static boolean matchedLogged;
    private static final String SCRIPT = "(function(){var d=document,h=d.documentElement,b=d.body,s=d.getElementById('wxoled-article-root-black'),old='wxoled-separator-black wxoled-separator-before wxoled-separator-after'.split(' '),mark='wxoled-article-wide-gray-black',z=function(e){if(!e)return;['background','background-color'].forEach(function(k){var v=e.style.getPropertyValue(k).replace(/\\s/g,'').toLowerCase();if(v==='#000'||v==='#000000'||v==='black'||v==='rgb(0,0,0)'||v==='rgba(0,0,0,1)')e.style.removeProperty(k);});},dropOld=function(){var a=d.querySelectorAll('.wxoled-separator-black,.wxoled-separator-before,.wxoled-separator-after');for(var i=0;i<a.length;i++)for(var j=0;j<old.length;j++)a[i].classList.remove(old[j]);};z(h);z(b);dropOld();if(!s){s=d.createElement('style');s.id='wxoled-article-root-black';(d.head||h).appendChild(s);}s.textContent='@media (prefers-color-scheme: dark){html,body,#js_article,.rich_media,.rich_media_wrp,.rich_media_area_primary,.rich_media_area_primary_inner,.rich_media_content,#js_content{background:#000!important;background-color:#000!important;}.wxoled-article-wide-gray-black{background:#000!important;background-color:#000!important;}.wxoled-comment-area-gray,.wxoled-comment-area-gray::before,.wxoled-comment-area-gray::after,.wxoled-comment-seam-gray,.wxoled-comment-seam-gray::before,.wxoled-comment-seam-gray::after,.wxoled-comment-end-gap-gray{background:#191919!important;background-color:#191919!important;border-color:#191919!important;}.wxoled-comment-surface-seam-cover{background:#191919!important;background-color:#191919!important;box-shadow:0 -12px 0 #191919!important;}.wxoled-comment-surface-seam-cover::before,.wxoled-comment-surface-seam-cover::after{background:#191919!important;background-color:#191919!important;border-color:#191919!important;}}';var m=window.matchMedia&&window.matchMedia('(prefers-color-scheme: dark)'),ok=function(x){return x==='rgb(24, 24, 24)'||x==='rgba(24, 24, 24, 1)'||x==='rgb(25, 25, 25)'||x==='rgba(25, 25, 25, 1)'||x==='rgb(30, 30, 30)'||x==='rgba(30, 30, 30, 1)';},scan=function(){dropOld();var root=d.getElementById('js_content')||d.querySelector('.rich_media_content');if(!root)return 0;var all=[root],more=root.querySelectorAll('div,section,article,main');for(var i=0;i<more.length;i++)all.push(more[i]);var dark=!m||m.matches,n=0,rr=root.getBoundingClientRect(),minH=Math.max(96,window.innerHeight*.20);for(var k=0;k<all.length;k++){var e=all[k],q=getComputedStyle(e),r=e.getBoundingClientRect();e.classList.remove(mark);if(dark&&q.display!=='none'&&q.visibility!=='hidden'&&r.width>=rr.width*.80&&r.height>=minH&&ok(q.backgroundColor)){e.classList.add(mark);n++;}}return n;};if(m&&!window.__wxoledArticleContainerMedia){window.__wxoledArticleContainerMedia=1;var changed=function(){scan();};if(m.addEventListener)m.addEventListener('change',changed);else if(m.addListener)m.addListener(changed);}var cover='wxoled-comment-surface-seam-cover',fb=function(){var old=d.querySelectorAll('.wxoled-comment-surface-seam-cover');for(var x=0;x<old.length;x++)old[x].classList.remove(cover);var a=d.querySelectorAll('div,section,main,footer'),best=null,anch=null;for(var i=0;i<a.length;i++){var e=a[i],q=getComputedStyle(e),r=e.getBoundingClientRect(),v=q.backgroundColor,ok=vis(q)&&r.width>=window.innerWidth*.88&&r.height>=120&&r.top>=0&&r.top<=window.innerHeight*.85&&(v==='rgb(24, 24, 24)'||v==='rgba(24, 24, 24, 1)'||v==='rgb(25, 25, 25)'||v==='rgba(25, 25, 25, 1)');if(!ok)continue;var vh=Math.min(r.bottom,window.innerHeight)-r.top;if(vh<100)continue;if(!best||r.top<best.getBoundingClientRect().top)best=e;if(e.matches('#js_cmt_area,#js_cmt_container,#js_comment_area,.discuss_container')||e.querySelector('#js_cmt_area,#js_cmt_container,#js_comment_area,.discuss_container'))anch=e;}(anch||best)&& (anch||best).classList.add(cover);};var area='wxoled-comment-area-gray',seam='wxoled-comment-seam-gray',gap='wxoled-comment-end-gap-gray',m=window.matchMedia&&window.matchMedia('(prefers-color-scheme: dark)'),clear=function(){var a=d.querySelectorAll('.wxoled-comment-area-gray,.wxoled-comment-seam-gray,.wxoled-comment-end-gap-gray');for(var i=0;i<a.length;i++){a[i].classList.remove(area);a[i].classList.remove(seam);a[i].classList.remove(gap);}},vis=function(q){return q.display!=='none'&&q.visibility!=='hidden';},seamok=function(e){var q=getComputedStyle(e),r=e.getBoundingClientRect(),v=q.backgroundColor;return vis(q)&&r.width>=window.innerWidth*.90&&r.height<=64&&(v==='rgb(0, 0, 0)'||v==='rgba(0, 0, 0, 1)'||v==='rgb(30, 30, 30)'||v==='rgba(30, 30, 30, 1)'||v==='transparent'||v==='rgba(0, 0, 0, 0)');},update=function(){var dark=!m||m.matches;clear();if(!dark)return;var anchors=d.querySelectorAll('#js_cmt_area,#js_cmt_container,#js_comment_area,.discuss_container');for(var i=0;i<anchors.length;i++){var a=anchors[i];a.classList.add(area);var e=a.previousElementSibling,n=0;while(e&&n<2){if(seamok(e))e.classList.add(seam);e=e.previousElementSibling;n++;}e=a.parentElement&&a.parentElement.previousElementSibling;if(e&&seamok(e))e.classList.add(seam);}fb();var bottom=Math.max(h.scrollHeight,b&&b.scrollHeight||0),near=window.scrollY+window.innerHeight>=bottom-200;if(!near)return;h.classList.add(gap);if(b)b.classList.add(gap);var off=[4,10,16,22,82,88,94,100,106],x=window.innerWidth/2,black=function(v){return v==='rgb(0, 0, 0)'||v==='rgba(0, 0, 0, 1)';};for(var p=0;p<off.length;p++){var y=window.innerHeight-off[p],es=d.elementsFromPoint(x,y);for(var j=0;j<es.length&&j<6;j++){var z=es[j],q=getComputedStyle(z),r=z.getBoundingClientRect();if(vis(q)&&r.width>=window.innerWidth*.95&&x>=r.left&&x<=r.right&&y>=r.top&&y<=r.bottom&&black(q.backgroundColor))z.classList.add(gap);}}};if(!window.__wxoledCommentSeamsListeners){window.__wxoledCommentSeamsListeners=1;var queued=0,queue=function(){if(queued)return;queued=1;requestAnimationFrame(function(){queued=0;update();});};window.addEventListener('scroll',queue,{passive:true});window.addEventListener('resize',queue);if(m&&m.addEventListener)m.addEventListener('change',queue);else if(m&&m.addListener)m.addListener(queue);}update();(function(){var w=window,oldState=w.__wxoledSeamOverlayState;if(oldState&&oldState.dispose)oldState.dispose();var id='wxoled-comment-seam-overlay',old=d.getElementById(id);if(old&&old.parentNode)old.parentNode.removeChild(old);var media=w.matchMedia&&w.matchMedia('(prefers-color-scheme: dark)'),node=d.createElement('div'),frame=0,observer=null,dead=false;node.id=id;node.style.cssText='position:fixed;left:0;right:0;width:100vw;background:#191919;pointer-events:none;z-index:2147483646;display:none;';(d.documentElement||d.body).appendChild(node);var updateOverlay=function(){if(dead)return;frame=0;if(!media||!media.matches){node.style.display='none';return;}var list=d.querySelectorAll('#js_cmt_area,#js_cmt_container,#js_comment_area,.discuss_container'),best=null,bestRect=null,vw=d.documentElement.clientWidth||w.innerWidth;for(var i=0;i<list.length;i++){var e=list[i],q=w.getComputedStyle(e),r=e.getBoundingClientRect();if(q.display==='none'||q.visibility==='hidden'||r.width<vw*.75||r.height<=0)continue;if(!bestRect||r.top<bestRect.top){best=e;bestRect=r;}}var height=Math.ceil(32/(w.devicePixelRatio||1));if(!best||bestRect.top<=0||bestRect.top>w.innerHeight||bestRect.top-height<0){node.style.display='none';return;}node.style.height=height+'px';node.style.top=(bestRect.top-height)+'px';node.style.display='block';},queueOverlay=function(){if(!dead&&!frame)frame=w.requestAnimationFrame(updateOverlay);},dispose=function(){if(dead)return;dead=true;w.removeEventListener('scroll',queueOverlay);w.removeEventListener('resize',queueOverlay);w.removeEventListener('pagehide',dispose);if(media&&media.removeEventListener)media.removeEventListener('change',queueOverlay);else if(media&&media.removeListener)media.removeListener(queueOverlay);if(observer)observer.disconnect();if(frame)w.cancelAnimationFrame(frame);if(node.parentNode)node.parentNode.removeChild(node);if(w.__wxoledSeamOverlayState===state)delete w.__wxoledSeamOverlayState;},state={dispose:dispose};w.__wxoledSeamOverlayState=state;w.addEventListener('scroll',queueOverlay,{passive:true});w.addEventListener('resize',queueOverlay);w.addEventListener('pagehide',dispose);if(media&&media.addEventListener)media.addEventListener('change',queueOverlay);else if(media&&media.addListener)media.addListener(queueOverlay);updateOverlay();if(w.MutationObserver){observer=new MutationObserver(queueOverlay);observer.observe(d.documentElement,{childList:true,subtree:true});}})();})();";
    private static final Map PENDING = Collections.synchronizedMap(new WeakHashMap());

    private ArticlePageBackgroundHook() {
    }

    static void cancel(Activity activity) {
        Window window;
        View decorView;
        if (activity != null) {
            try {
                Object objRemove = PENDING.remove(activity);
                if (!(objRemove instanceof List) || (window = activity.getWindow()) == null || (decorView = window.getDecorView()) == null) {
                    return;
                }
                for (Object obj : (List) objRemove) {
                    if (obj instanceof Runnable) {
                        decorView.removeCallbacks((Runnable) obj);
                    }
                }
            } catch (Throwable unused) {
            }
        }
    }

    private static int countViews(View view) {
        if (view == null) {
            return 0;
        }
        int iCountViews = 1;
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                iCountViews += countViews(viewGroup.getChildAt(i));
            }
        }
        return iCountViews;
    }

    private static WebView findWebView(View view) {
        if (view == null) {
            return null;
        }
        if (view instanceof WebView) {
            return (WebView) view;
        }
        if (!(view instanceof ViewGroup)) {
            return null;
        }
        ViewGroup viewGroup = (ViewGroup) view;
        for (int i = 0; i < viewGroup.getChildCount(); i++) {
            WebView webViewFindWebView = findWebView(viewGroup.getChildAt(i));
            if (webViewFindWebView != null) {
                return webViewFindWebView;
            }
        }
        return null;
    }

    static void install(ClassLoader classLoader) {
        try {
            if (attachHooked) {
                return;
            }
            attachHooked = true;
            XposedHelpers.findAndHookMethod(Application.class, "attach", new Object[]{Context.class, new XC_MethodHook() {
                protected void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    try {
                        Object obj = methodHookParam.args[0];
                        if (obj instanceof Context) {
                            ArticlePageBackgroundHook.installForContext((Context) obj);
                        }
                    } catch (Throwable unused) {
                    }
                }
            }});
        } catch (Throwable unused) {
        }
    }

    private static void installCleanup() {
        try {
            XposedBridge.hookAllMethods(Instrumentation.class, "callActivityOnDestroy", new XC_MethodHook() {
                protected void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                    if (methodHookParam != null) {
                        try {
                            Object[] objArr = methodHookParam.args;
                            if (objArr == null || objArr.length <= 0 || !(objArr[0] instanceof Activity)) {
                                return;
                            }
                            Activity activity = (Activity) methodHookParam.args[0];
                            if (ArticlePageBackgroundHook.isTarget(activity)) {
                                ArticlePageBackgroundHook.cancel(activity);
                            }
                        } catch (Throwable unused) {
                        }
                    }
                }
            });
        } catch (Throwable unused) {
        }
    }

    static void installForContext(Context context) {
        PackageInfo packageInfo;
        Class clsFindClassIfExists;
        if (context != null) {
            try {
                if (!"com.tencent.mm".equals(context.getPackageName()) || (packageInfo = context.getPackageManager().getPackageInfo(context.getPackageName(), 0)) == null || !"8.0.76".equals(packageInfo.versionName) || installed || (clsFindClassIfExists = XposedHelpers.findClassIfExists("com.tencent.mm.plugin.brandservice.ui.timeline.preload.ui.TmplWebViewMMUI", context.getClassLoader())) == null || XposedHelpers.findAndHookMethod(clsFindClassIfExists, "onCreate", new Object[]{Bundle.class, new XC_MethodHook() {
                    protected void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                        if (methodHookParam != null) {
                            try {
                                if (methodHookParam.thisObject instanceof Activity) {
                                    Activity activity = (Activity) methodHookParam.thisObject;
                                    if (ArticlePageBackgroundHook.isTarget(activity)) {
                                        ArticlePageBackgroundHook.matched(activity);
                                        ArticlePageBackgroundHook.schedule(activity);
                                    }
                                }
                            } catch (Throwable unused) {
                            }
                        }
                    }
                }}) == null) {
                    return;
                }
                installed = true;
                XposedBridge.log("WeChatOLED: article onCreate hook installed count=1");
                installCleanup();
            } catch (Throwable unused) {
            }
        }
    }

    private static boolean invokeJs(View view) {
        if (view == null) {
            return false;
        }
        try {
            if (!(view instanceof WebView)) {
                for (Method method : view.getClass().getMethods()) {
                    if ("evaluateJavascript".equals(method.getName()) && Modifier.isPublic(method.getModifiers())) {
                        Class<?>[] parameterTypes = method.getParameterTypes();
                        int length = parameterTypes.length;
                        if (length != 1) {
                            if (length == 2 && parameterTypes[0] == String.class && !parameterTypes[1].isPrimitive()) {
                                method.invoke(view, SCRIPT, null);
                                XposedBridge.log("WeChatOLED: article eval invoked");
                                return true;
                            }
                        } else if (parameterTypes[0] == String.class) {
                            method.invoke(view, SCRIPT);
                            XposedBridge.log("WeChatOLED: article eval invoked");
                            return true;
                        }
                    }
                }
                return false;
            }
            ((WebView) view).evaluateJavascript(SCRIPT, null);
            XposedBridge.log("WeChatOLED: article eval invoked");
            return true;
        } catch (Throwable unused) {
            return false;
        }
    }

    static boolean isTarget(Activity activity) {
        if (activity == null) {
            return false;
        }
        try {
            return "com.tencent.mm.plugin.brandservice.ui.timeline.preload.ui.TmplWebViewMMUI".equals(activity.getClass().getName());
        } catch (Throwable unused) {
            return false;
        }
    }

    private static void logProbe(int i, int i2) {
        try {
            XposedBridge.log("WeChatOLED: article probe views=" + i + " jsTargets=" + i2);
        } catch (Throwable unused) {
        }
    }

    static void matched(Activity activity) {
        try {
            if (matchedLogged) {
                return;
            }
            matchedLogged = true;
            XposedBridge.log("WeChatOLED: article activity matched");
        } catch (Throwable unused) {
        }
    }

    private static int probe(View view, IdentityHashMap identityHashMap) {
        if (view == null) {
            return 0;
        }
        try {
            int iProbe = 0;
            if (view.isShown() && !identityHashMap.containsKey(view)) {
                identityHashMap.put(view, Boolean.TRUE);
                if (invokeJs(view)) {
                    iProbe = 1;
                }
            }
            if (view instanceof ViewGroup) {
                ViewGroup viewGroup = (ViewGroup) view;
                for (int i = 0; i < viewGroup.getChildCount(); i++) {
                    iProbe += probe(viewGroup.getChildAt(i), identityHashMap);
                }
            }
            return iProbe;
        } catch (Throwable unused) {
            return 0;
        }
    }

    static void run(Activity activity) {
        if (activity != null) {
            try {
                Window window = activity.getWindow();
                if (window != null) {
                    View decorView = window.getDecorView();
                    logProbe(countViews(decorView), probe(decorView, new IdentityHashMap()));
                }
            } catch (Throwable unused) {
            }
        }
    }

    static void schedule(Activity activity) {
        View decorView;
        if (activity != null) {
            try {
                Map map = PENDING;
                if (map.containsKey(activity)) {
                    return;
                }
                ArrayList arrayList = new ArrayList();
                map.put(activity, arrayList);
                Window window = activity.getWindow();
                if (window == null || (decorView = window.getDecorView()) == null) {
                    return;
                }
                long[] jArr = {300, 1000, 2500, 5000};
                for (int i = 0; i < 4; i++) {
                    Runnable runnable = new Runnable() {
                        private final WeakReference<Activity> activityRef = new WeakReference<>(activity);


                        @Override // java.lang.Runnable
                        public void run() {
                            try {
                                if (this.activityRef.get() instanceof Activity) {
                                    ArticlePageBackgroundHook.run(this.activityRef.get());
                                }
                            } catch (Throwable unused) {
                            }
                        }
                    };
                    arrayList.add(runnable);
                    decorView.postDelayed(runnable, jArr[i]);
                }
            } catch (Throwable unused) {
            }
        }
    }
}
