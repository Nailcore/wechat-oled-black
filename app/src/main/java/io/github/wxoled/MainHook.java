package io.github.wxoled;

import android.app.Activity;
import android.app.Application;
import android.app.Instrumentation;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.NinePatch;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.DrawableContainer;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

public class MainHook implements IXposedHookLoadPackage {
    private static final String TAG = "WeChatOLED: ";
    private static final String WECHAT = "com.tencent.mm";
    private static int hairlineMaxPx;
    private static boolean installed;
    private static volatile boolean night;
    private static final Set<Class<?>> hookedResourceClasses = Collections.synchronizedSet(new HashSet());
    private static final XC_MethodHook MAP_RESULT = new XC_MethodHook() {
        protected void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
            if (!MainHook.night || methodHookParam.hasThrowable()) {
                return;
            }
            Object result = methodHookParam.getResult();
            if (result instanceof Integer) {
                methodHookParam.setResult(Integer.valueOf(Palette.map(((Integer) result).intValue())));
            }
        }
    };
    private static final XC_MethodHook MAP_ARG0 = new XC_MethodHook() {
        protected void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
            if (MainHook.night && (methodHookParam.args[0] instanceof Integer)) {
                methodHookParam.args[0] = Integer.valueOf(Palette.map(((Integer) methodHookParam.args[0]).intValue()));
            }
        }
    };
    private static final Set<String> SHAPE_DRAWS = new HashSet(Arrays.asList("drawRect", "drawRoundRect", "drawPath", "drawOval", "drawCircle", "drawArc", "drawPaint", "drawDoubleRoundRect"));
    private static final XC_MethodHook MAP_PAINT = new XC_MethodHook() {
        protected void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
            Paint paint;
            int color;
            int map;
            if (MainHook.night && (paint = (Paint) methodHookParam.args[methodHookParam.args.length - 1]) != null && paint.getShader() == null && (map = Palette.map((color = paint.getColor()))) != color) {
                paint.setColor(map);
                methodHookParam.setObjectExtra("paintColor", Integer.valueOf(color));
            }
        }

        protected void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
            Object objectExtra = methodHookParam.getObjectExtra("paintColor");
            if (objectExtra != null) {
                ((Paint) methodHookParam.args[methodHookParam.args.length - 1]).setColor(((Integer) objectExtra).intValue());
            }
        }
    };
    private static final XC_MethodHook COLOR_DRAWABLE_DRAW = new XC_MethodHook() {
        protected void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
            if (MainHook.night) {
                ColorDrawable colorDrawable = (ColorDrawable) methodHookParam.thisObject;
                int color = colorDrawable.getColor();
                int map = Palette.map(color);
                if (map != color) {
                    colorDrawable.setColor(map);
                }
                if (Palette.isBoostedWhite(map) && MainHook.isRedundantGroupTopLine(colorDrawable)) {
                    methodHookParam.setResult((Object) null);
                }
            }
        }
    };
    private static final XC_MethodHook MIC_FADE_DRAW = new XC_MethodHook() {
        protected void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
            Bitmap bitmap;
            if (MainHook.night) {
                BitmapDrawable bitmapDrawable = (BitmapDrawable) methodHookParam.thisObject;
                if (bitmapDrawable.getCallback() instanceof View) {
                    Integer num = (Integer) MainHook.fadeTint.get(bitmapDrawable);
                    if ((num != null || bitmapDrawable.getColorFilter() == null) && (bitmap = bitmapDrawable.getBitmap()) != null && !bitmap.isRecycled() && bitmap.getWidth() <= 512 && bitmap.getHeight() <= 512) {
                        View view = (View) bitmapDrawable.getCallback();
                        if (MainHook.inChatFooter(view) && MainHook.isGreyFade(bitmap)) {
                            int iActualFieldColor = MainHook.actualFieldColor(view);
                            if (num == null || num.intValue() != iActualFieldColor) {
                                bitmapDrawable.setColorFilter(new PorterDuffColorFilter(iActualFieldColor, PorterDuff.Mode.SRC_IN));
                                MainHook.fadeTint.put(bitmapDrawable, Integer.valueOf(iActualFieldColor));
                            }
                        }
                    }
                }
            }
        }
    };
    private static final Map<BitmapDrawable, Integer> fadeTint = Collections.synchronizedMap(new WeakHashMap());
    private static final Map<Bitmap, Boolean> greyFadeCache = Collections.synchronizedMap(new WeakHashMap());

    interface HookAction {
        void run() throws Throwable;
    }

    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam loadPackageParam) {
        if (WECHAT.equals(loadPackageParam.packageName) && WECHAT.equals(loadPackageParam.processName) && !installed) {
            installed = true;
            hairlineMaxPx = Math.max(2, Math.round(Resources.getSystem().getDisplayMetrics().density));
            night = (Resources.getSystem().getConfiguration().uiMode & 48) == 32;
            hookNightState();
            hookColorSources();
            hookDrawables();
            hookWindow();
            ArticlePageBackgroundHook.install(loadPackageParam.classLoader);
            XposedBridge.log("WeChatOLED: hooks installed in " + loadPackageParam.processName);
        }
    }

    public static void updateNight(Resources resources) {
        if (resources == null) {
            return;
        }
        night = (resources.getConfiguration().uiMode & 48) == 32;
        hookResourcesSubclass(resources.getClass());
    }

    private static void hookNightState() {
        safe("callApplicationOnCreate", new HookAction() {
            @Override // io.github.wxoled.MainHook.HookAction
            public final void run() {
                XposedHelpers.findAndHookMethod(Instrumentation.class, "callApplicationOnCreate", new Object[]{Application.class, new XC_MethodHook() {
                    protected void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                        MainHook.updateNight(((Application) methodHookParam.args[0]).getResources());
                    }

                    protected void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                        MainHook.registerRestartReceiver((Application) methodHookParam.args[0]);
                    }
                }});
            }
        });
        final XC_MethodHook xC_MethodHook = new XC_MethodHook() {
            protected void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                MainHook.updateNight(((Activity) methodHookParam.args[0]).getResources());
            }
        };
        safe("callActivityOnCreate", new HookAction() {
            @Override // io.github.wxoled.MainHook.HookAction
            public final void run() {
                XposedBridge.hookAllMethods(Instrumentation.class, "callActivityOnCreate", xC_MethodHook);
            }
        });
        safe("callActivityOnResume", new HookAction() {
            @Override // io.github.wxoled.MainHook.HookAction
            public final void run() {
                XposedBridge.hookAllMethods(Instrumentation.class, "callActivityOnResume", xC_MethodHook);
            }
        });
        safe("onConfigurationChanged", new HookAction() {
            @Override // io.github.wxoled.MainHook.HookAction
            public final void run() {
                XposedHelpers.findAndHookMethod(Activity.class, "onConfigurationChanged", new Object[]{Configuration.class, new XC_MethodHook() {
                    protected void afterHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                        MainHook.updateNight(((Activity) methodHookParam.thisObject).getResources());
                    }
                }});
            }
        });
    }

    private static void hookResourcesSubclass(Class<?> cls) {
        while (cls != null && cls != Resources.class) {
            if (hookedResourceClasses.add(cls)) {
                for (final Method method : cls.getDeclaredMethods()) {
                    if (method.getName().equals("getColor") && method.getReturnType() == Integer.TYPE && !Modifier.isAbstract(method.getModifiers())) {
                        safe(cls.getName() + ".getColor", new HookAction() {
                            @Override // io.github.wxoled.MainHook.HookAction
                            public final void run() {
                                XposedBridge.hookMethod(method, MainHook.MAP_RESULT);
                            }
                        });
                    }
                }
            }
            cls = cls.getSuperclass();
        }
    }

    private static void hookColorSources() {
        safe("Resources.getColor", new HookAction() {
            @Override // io.github.wxoled.MainHook.HookAction
            public final void run() {
                XposedBridge.hookAllMethods(Resources.class, "getColor", MainHook.MAP_RESULT);
            }
        });
        safe("TypedArray.getColor", new HookAction() {
            @Override // io.github.wxoled.MainHook.HookAction
            public final void run() {
                XposedHelpers.findAndHookMethod(TypedArray.class, "getColor", new Object[]{Integer.TYPE, Integer.TYPE, MainHook.MAP_RESULT});
            }
        });
        safe("ColorStateList.getColorForState", new HookAction() {
            @Override // io.github.wxoled.MainHook.HookAction
            public final void run() {
                XposedHelpers.findAndHookMethod(ColorStateList.class, "getColorForState", new Object[]{int[].class, Integer.TYPE, MainHook.MAP_RESULT});
            }
        });
        safe("ColorStateList.getDefaultColor", new HookAction() {
            @Override // io.github.wxoled.MainHook.HookAction
            public final void run() {
                XposedHelpers.findAndHookMethod(ColorStateList.class, "getDefaultColor", new Object[]{MainHook.MAP_RESULT});
            }
        });
    }

    private static void hookDrawables() {
        safe("ColorDrawable.setColor", new HookAction() {
            @Override // io.github.wxoled.MainHook.HookAction
            public final void run() {
                XposedHelpers.findAndHookMethod(ColorDrawable.class, "setColor", new Object[]{Integer.TYPE, MainHook.MAP_ARG0});
            }
        });
        safe("ColorDrawable.draw", new HookAction() {
            @Override // io.github.wxoled.MainHook.HookAction
            public final void run() {
                XposedHelpers.findAndHookMethod(ColorDrawable.class, "draw", new Object[]{Canvas.class, MainHook.COLOR_DRAWABLE_DRAW});
            }
        });
        safe("BitmapDrawable.draw", new HookAction() {
            @Override // io.github.wxoled.MainHook.HookAction
            public final void run() {
                XposedHelpers.findAndHookMethod(BitmapDrawable.class, "draw", new Object[]{Canvas.class, MainHook.MIC_FADE_DRAW});
            }
        });
        String[] strArr = {"android.graphics.BaseCanvas", "android.graphics.BaseRecordingCanvas"};
        for (int i = 0; i < 2; i++) {
            final String str = strArr[i];
            safe(str + ".draw*", new HookAction() {
                @Override // io.github.wxoled.MainHook.HookAction
                public final void run() throws Throwable {
                    MainHook.lambda$hookDrawables$12(str);
                }
            });
        }
        safe("GradientDrawable.setColor", new HookAction() {
            @Override // io.github.wxoled.MainHook.HookAction
            public final void run() {
                XposedHelpers.findAndHookMethod(GradientDrawable.class, "setColor", new Object[]{Integer.TYPE, MainHook.MAP_ARG0});
            }
        });
        safe("GradientDrawable.setColors", new HookAction() {
            @Override // io.github.wxoled.MainHook.HookAction
            public final void run() {
                XposedBridge.hookAllMethods(GradientDrawable.class, "setColors", new XC_MethodHook() {
                    protected void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                        if (MainHook.night && (methodHookParam.args[0] instanceof int[])) {
                            int[] iArr = (int[]) methodHookParam.args[0];
                            int[] iArr2 = new int[iArr.length];
                            for (int i2 = 0; i2 < iArr.length; i2++) {
                                iArr2[i2] = Palette.map(iArr[i2]);
                            }
                            methodHookParam.args[0] = iArr2;
                        }
                    }
                });
            }
        });
        safe("NinePatch.<init>", new HookAction() {
            @Override // io.github.wxoled.MainHook.HookAction
            public final void run() {
                XposedBridge.hookAllConstructors(NinePatch.class, new XC_MethodHook() {
                    protected void beforeHookedMethod(XC_MethodHook.MethodHookParam methodHookParam) {
                        Bitmap bitmapRecolor;
                        if (!MainHook.night || methodHookParam.args.length == 0 || !(methodHookParam.args[0] instanceof Bitmap) || (bitmapRecolor = MainHook.recolor((Bitmap) methodHookParam.args[0])) == null) {
                            return;
                        }
                        methodHookParam.args[0] = bitmapRecolor;
                    }
                });
            }
        });
        String[] strArr2 = {"android.graphics.Canvas", "android.graphics.BaseCanvas", "android.graphics.BaseRecordingCanvas"};
        for (int i2 = 0; i2 < 3; i2++) {
            final String str2 = strArr2[i2];
            safe(str2 + ".drawColor", new HookAction() {
                @Override // io.github.wxoled.MainHook.HookAction
                public final void run() throws Throwable {
                    MainHook.lambda$hookDrawables$16(str2);
                }
            });
        }
    }

    static void lambda$hookDrawables$12(String str) throws Throwable {
        Class clsFindClassIfExists = XposedHelpers.findClassIfExists(str, (ClassLoader) null);
        if (clsFindClassIfExists == null) {
            return;
        }
        for (Method method : clsFindClassIfExists.getDeclaredMethods()) {
            if (!Modifier.isAbstract(method.getModifiers()) && SHAPE_DRAWS.contains(method.getName())) {
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length > 0 && parameterTypes[parameterTypes.length - 1] == Paint.class) {
                    XposedBridge.hookMethod(method, MAP_PAINT);
                }
            }
        }
    }

    static void lambda$hookDrawables$16(String str) throws Throwable {
        Class clsFindClassIfExists = XposedHelpers.findClassIfExists(str, (ClassLoader) null);
        if (clsFindClassIfExists == null) {
            return;
        }
        for (Method method : clsFindClassIfExists.getDeclaredMethods()) {
            Class<?>[] parameterTypes = method.getParameterTypes();
            if (method.getName().equals("drawColor") && parameterTypes.length > 0 && parameterTypes[0] == Integer.TYPE && !Modifier.isAbstract(method.getModifiers())) {
                XposedBridge.hookMethod(method, MAP_ARG0);
            }
        }
    }

    public static boolean isRedundantGroupTopLine(ColorDrawable colorDrawable) {
        if (!(colorDrawable.getCallback() instanceof View)) {
            return false;
        }
        View view = (View) colorDrawable.getCallback();
        if (view.getHeight() > hairlineMaxPx || !(view.getParent() instanceof ViewGroup)) {
            return false;
        }
        ViewGroup viewGroup = (ViewGroup) view.getParent();
        return viewGroup.getChildAt(0) == view && view.getWidth() >= viewGroup.getWidth() - 1 && (viewGroup.getParent() instanceof AbsListView) && ((AbsListView) viewGroup.getParent()).getPositionForView(viewGroup) > 1;
    }

    public static int actualFieldColor(View view) {
        int[] iArr = new int[2];
        view.getLocationOnScreen(iArr);
        int[] iArr2 = {0, 0};
        composite(view.getRootView(), view, iArr[0] + (view.getWidth() / 2), iArr[1] + (view.getHeight() / 2), iArr2);
        int i = iArr2[0];
        return (i >>> 24) == 0 ? Palette.INPUT_FIELD : i | (-16777216);
    }

    private static void composite(View view, View view2, int i, int i2, int[] iArr) {
        if (iArr[1] != 0) {
            return;
        }
        if (view == view2) {
            iArr[1] = 1;
            return;
        }
        if (view.getVisibility() != 0 || view.getAlpha() <= 0.0f) {
            return;
        }
        int[] iArr2 = new int[2];
        view.getLocationOnScreen(iArr2);
        int i3 = iArr2[0];
        if (i < i3 || i2 < iArr2[1] || i >= i3 + view.getWidth() || i2 >= iArr2[1] + view.getHeight()) {
            return;
        }
        int iSolidColor = solidColor(view.getBackground());
        int i4 = iSolidColor >>> 24;
        if (i4 != 0) {
            iArr[0] = over((iSolidColor & 16777215) | (Math.round(i4 * view.getAlpha()) << 24), iArr[0]);
        }
        if (view instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) view;
            for (int i5 = 0; i5 < viewGroup.getChildCount() && iArr[1] == 0; i5++) {
                composite(viewGroup.getChildAt(i5), view2, i, i2, iArr);
            }
        }
    }

    private static int solidColor(Drawable drawable) {
        if (drawable == null) {
            return 0;
        }
        if (drawable instanceof DrawableContainer) {
            drawable = drawable.getCurrent();
        }
        if (drawable instanceof ColorDrawable) {
            return Palette.map(((ColorDrawable) drawable).getColor());
        }
        if (drawable instanceof GradientDrawable) {
            Object objectField = XposedHelpers.getObjectField(drawable, "mFillPaint");
            if (objectField instanceof Paint) {
                Paint paint = (Paint) objectField;
                if (paint.getShader() == null) {
                    int map = Palette.map(paint.getColor());
                    return ((((map >>> 24) * drawable.getAlpha()) / 255) << 24) | (map & 16777215);
                }
            }
        }
        return 0;
    }

    private static int over(int i, int i2) {
        int i3 = i >>> 24;
        int i4 = i2 >>> 24;
        int i5 = (((255 - i3) * i4) / 255) + i3;
        if (i5 == 0) {
            return 0;
        }
        return mix(i & 255, i3, i2 & 255, i4, i5) | (i5 << 24) | (mix((i >> 16) & 255, i3, (i2 >> 16) & 255, i4, i5) << 16) | (mix((i >> 8) & 255, i3, (i2 >> 8) & 255, i4, i5) << 8);
    }

    private static int mix(int i, int i2, int i3, int i4, int i5) {
        return (((i * i2) + (((i3 * i4) * (255 - i2)) / 255)) + (i5 / 2)) / i5;
    }

    public static boolean isGreyFade(Bitmap bitmap) {
        Boolean bool = greyFadeCache.get(bitmap);
        if (bool != null) {
            return bool.booleanValue();
        }
        boolean z = false;
        try {
            Bitmap bitmapCopy = bitmap.getConfig() == Bitmap.Config.HARDWARE ? bitmap.copy(Bitmap.Config.ARGB_8888, false) : bitmap;
            if (bitmapCopy != null) {
                int pixel = bitmapCopy.getPixel(bitmapCopy.getWidth() / 2, bitmapCopy.getHeight() / 2);
                int i = (pixel >> 16) & 255;
                int i2 = (pixel >> 8) & 255;
                int i3 = pixel & 255;
                int iMax = Math.max(i, Math.max(i2, i3));
                int iMin = Math.min(i, Math.min(i2, i3));
                if ((pixel >>> 24) >= 192 && iMax - iMin <= 6 && iMin >= 24 && iMax <= 64) {
                    z = true;
                }
                if (bitmapCopy != bitmap) {
                    bitmapCopy.recycle();
                }
            }
        } catch (RuntimeException unused) {
        }
        greyFadeCache.put(bitmap, Boolean.valueOf(z));
        return z;
    }

    public static boolean inChatFooter(View view) {
        while (true) {
            Object parent = view.getParent();
            if (!(parent instanceof View)) {
                return false;
            }
            if (parent.getClass().getName().contains("ChatFooter")) {
                return true;
            }
            view = (View) parent;
        }
    }

    public static Bitmap recolor(Bitmap bitmap) {
        if (bitmap.isRecycled() || bitmap.getConfig() == Bitmap.Config.HARDWARE) {
            return null;
        }
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        if (width <= 0 || height <= 0 || ((long) width) * ((long) height) > 1000000) {
            return null;
        }
        int i = width * height;
        int[] iArr = new int[i];
        bitmap.getPixels(iArr, 0, width, 0, 0, width, height);
        if (!Palette.mapPixels(iArr, i <= 64)) {
            return null;
        }
        Bitmap bitmapCreateBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        bitmapCreateBitmap.setDensity(bitmap.getDensity());
        bitmapCreateBitmap.setPixels(iArr, 0, width, 0, 0, width, height);
        return bitmapCreateBitmap;
    }

    private static void hookWindow() {
        final Class clsFindClassIfExists = XposedHelpers.findClassIfExists("com.android.internal.policy.PhoneWindow", (ClassLoader) null);
        if (clsFindClassIfExists == null) {
            return;
        }
        String[] strArr = {"setStatusBarColor", "setNavigationBarColor", "setNavigationBarDividerColor"};
        for (int i = 0; i < 3; i++) {
            final String str = strArr[i];
            safe("PhoneWindow." + str, new HookAction() {
                @Override // io.github.wxoled.MainHook.HookAction
                public final void run() {
                    XposedHelpers.findAndHookMethod(clsFindClassIfExists, str, new Object[]{Integer.TYPE, MainHook.MAP_ARG0});
                }
            });
        }
    }

    static class AnonymousClass11 extends BroadcastReceiver {
        AnonymousClass11() {
        }

        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            setResultCode(1);
            Intent launchIntentForPackage = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
            if (launchIntentForPackage != null) {
                Bundle bundle = new Bundle();
                bundle.putParcelable("relaunch", PendingIntent.getActivity(context, 0, launchIntentForPackage, 201326592));
                setResultExtras(bundle);
            }
            new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                    Process.killProcess(Process.myPid());
                }
            }, 300L);
        }
    }

    public static void registerRestartReceiver(final Application application) {
        safe("restartReceiver", new HookAction() {
            @Override // io.github.wxoled.MainHook.HookAction
            public final void run() throws Throwable {
                MainHook.lambda$registerRestartReceiver$18(application);
            }
        });
    }

    static void lambda$registerRestartReceiver$18(Application application) throws Throwable {
        AnonymousClass11 anonymousClass11 = new AnonymousClass11();
        IntentFilter intentFilter = new IntentFilter("io.github.wxoled.action.RESTART_WECHAT");
        if (Build.VERSION.SDK_INT >= 33) {
            application.registerReceiver(anonymousClass11, intentFilter, "io.github.wxoled.permission.RESTART_WECHAT", null, 2);
        } else {
            application.registerReceiver(anonymousClass11, intentFilter, "io.github.wxoled.permission.RESTART_WECHAT", null);
        }
    }

    private static void safe(String str, HookAction hookAction) {
        try {
            hookAction.run();
        } catch (Throwable th) {
            XposedBridge.log("WeChatOLED: hook " + str + " failed: " + th);
        }
    }
}
