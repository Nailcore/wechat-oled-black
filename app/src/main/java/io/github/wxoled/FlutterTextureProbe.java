package io.github.wxoled;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.res.Configuration;
import android.graphics.RenderEffect;
import android.graphics.RuntimeShader;
import android.os.Build;
import android.view.TextureView;
import android.view.View;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * Stage-one probe for WeChat's BizFlutter article surface.
 *
 * <p>This version deliberately uses a pass-through shader.  It validates that
 * the exact BizFlutter activity can stay on a TextureView and accept a
 * View-level RenderEffect without changing any pixels.  Every lookup and hook
 * fails open, and the existing MainHook remains a separate Xposed entry.</p>
 */
public final class FlutterTextureProbe implements IXposedHookLoadPackage {
    private static final String WECHAT = "com.tencent.mm";
    private static final String TARGET_ACTIVITY =
            "com.tencent.mm.plugin.brandservice.ui.flutter.BizFlutterTLFlutterViewActivity";
    private static final String ENGINE = "com.tencent.mm.flutter.ui.FlutterViewEngine";
    private static final String ACTIVITY_BASE =
            "com.tencent.mm.plugin.flutter.ui.MMFlutterViewActivity";
    private static final String RENDER_MODE = "io.flutter.embedding.android.RenderMode";
    private static final String TAG = "WeChatOLED-TextureProbe";
    private static final String PASSTHROUGH_SHADER =
            "uniform shader input; half4 main(float2 p) { return input.eval(p); }";
    private static final long SUPPORTED_WECHAT_VERSION = 3141L; // WeChat 8.0.76
    private static final String SUPPORTED_WECHAT_NAME = "8.0.76";

    private static volatile boolean installed;
    private static volatile boolean installing;
    private static RenderEffect passThrough;
    private static Object textureMode;
    private static final Map<Object, Boolean> textureEngines =
            Collections.synchronizedMap(new WeakHashMap<>());
    private static final Set<Object> effectedEngines = Collections.synchronizedSet(
            Collections.newSetFromMap(new WeakHashMap<Object, Boolean>()));
    private static final Map<Activity, Boolean> pendingRecreates =
            Collections.synchronizedMap(new WeakHashMap<>());

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) {
        if (!WECHAT.equals(lpparam.packageName) || !WECHAT.equals(lpparam.processName)
                || installed || Build.VERSION.SDK_INT < 33) {
            return;
        }

        final ClassLoader loader = lpparam.classLoader;
        XposedHelpers.findAndHookMethod(Application.class, "attach", Context.class,
                new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        if (!(param.args[0] instanceof Context) || installed || installing) {
                            return;
                        }
                        Context context = (Context) param.args[0];
                        try {
                            PackageInfo info = context.getPackageManager()
                                    .getPackageInfo(WECHAT, 0);
                            if (info.getLongVersionCode() != SUPPORTED_WECHAT_VERSION
                                    || !SUPPORTED_WECHAT_NAME.equals(info.versionName)) {
                                return;
                            }
                            install(loader);
                        } catch (Throwable error) {
                            XposedBridge.log(TAG + ": version check skipped: "
                                    + error.getClass().getSimpleName());
                        }
                    }
                });
    }

    private static void install(ClassLoader loader) {
        if (installed || installing) {
            return;
        }
        installing = true;
        final List<XC_MethodHook.Unhook> hooks = new ArrayList<>();
        try {
            // Build the no-op effect before installing any rendering hooks.  If
            // AGSL is unavailable, the entire probe remains inert.
            RuntimeShader shader = new RuntimeShader(PASSTHROUGH_SHADER);
            passThrough = RenderEffect.createRuntimeShaderEffect(shader, "input");

            Class<?> engine = XposedHelpers.findClassIfExists(ENGINE, loader);
            Class<?> activityBase = XposedHelpers.findClassIfExists(ACTIVITY_BASE, loader);
            Class<?> renderMode = XposedHelpers.findClassIfExists(RENDER_MODE, loader);
            if (engine == null || activityBase == null || renderMode == null) {
                throw new ClassNotFoundException("WeChat Flutter classes unavailable");
            }
            preflight(engine, activityBase);
            textureMode = XposedHelpers.getStaticObjectField(renderMode, "texture");
            if (textureMode == null) {
                throw new NoSuchFieldException("RenderMode.texture unavailable");
            }

            hooks.add(hookRenderMode(engine));
            hooks.add(hookSurfaceSwap(engine));
            hookEffectApplication(engine, activityBase, hooks);
            installed = true;
            XposedBridge.log(TAG + ": installed (pass-through only)");
        } catch (Throwable error) {
            for (int i = hooks.size() - 1; i >= 0; i--) {
                try {
                    hooks.get(i).unhook();
                } catch (Throwable ignored) {
                }
            }
            installed = false;
            textureEngines.clear();
            effectedEngines.clear();
            pendingRecreates.clear();
            textureMode = null;
            passThrough = null;
            // Fail open: MainHook is a separate xposed_init entry and continues
            // to load even when this optional probe cannot be installed.
            XposedBridge.log(TAG + ": skipped: " + error.getClass().getSimpleName());
        } finally {
            installing = false;
        }
    }

    private static void preflight(Class<?> engine, Class<?> activityBase) throws Exception {
        Method renderModeMethod = engine.getDeclaredMethod("d");
        if (!RENDER_MODE.equals(renderModeMethod.getReturnType().getName())) {
            throw new NoSuchMethodException("Unexpected FlutterViewEngine.d return type");
        }
        engine.getDeclaredMethod("j");
        engine.getDeclaredMethod("f");
        if (engine.getDeclaredConstructors().length != 1
                || engine.getDeclaredConstructors()[0].getParameterTypes().length != 1) {
            throw new NoSuchMethodException("Unexpected FlutterViewEngine constructor");
        }
        engine.getDeclaredField("i");
        engine.getDeclaredField("m");
        activityBase.getDeclaredField("f");
        activityBase.getDeclaredMethod("onResume");
        activityBase.getDeclaredMethod("onConfigurationChanged", Configuration.class);
    }

    private static XC_MethodHook.Unhook hookRenderMode(Class<?> engine) {
        return XposedHelpers.findAndHookMethod(engine, "d", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                if (isTextureEngine(param.thisObject)) {
                    param.setResult(textureMode);
                }
            }
        });
    }

    private static XC_MethodHook.Unhook hookSurfaceSwap(Class<?> engine) {
        return XposedHelpers.findAndHookMethod(engine, "j", new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (isTextureEngine(param.thisObject)) {
                    // Keep the target page on the TextureView selected by d().
                    param.setResult(null);
                }
            }
        });
    }

    private static void hookEffectApplication(Class<?> engine, Class<?> activityBase,
            List<XC_MethodHook.Unhook> hooks) {
        Set<XC_MethodHook.Unhook> constructorHooks = XposedBridge.hookAllConstructors(engine,
                new XC_MethodHook() {
            @Override
            protected void beforeHookedMethod(MethodHookParam param) {
                if (param.args.length == 1 && param.args[0] instanceof Activity) {
                    Activity activity = (Activity) param.args[0];
                    if (isExactTarget(activity)) {
                        textureEngines.put(param.thisObject, isNight(activity));
                    }
                }
            }

            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                applyOrClear(param.thisObject);
            }
        });
        if (constructorHooks.isEmpty()) {
            throw new IllegalStateException("FlutterViewEngine constructor hook missing");
        }
        hooks.addAll(constructorHooks);

        // f() is the engine's onCreate path. Reapply after it finishes in case
        // Flutter replaced or reattached its rendering surface.
        hooks.add(XposedHelpers.findAndHookMethod(engine, "f", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                applyOrClear(param.thisObject);
            }
        }));

        hooks.add(XposedHelpers.findAndHookMethod(activityBase, "onResume", new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                reapplyFromActivity(param.thisObject);
            }
        }));

        hooks.add(XposedHelpers.findAndHookMethod(activityBase, "onConfigurationChanged",
                Configuration.class, new XC_MethodHook() {
                    @Override
                    protected void afterHookedMethod(MethodHookParam param) {
                        reapplyFromActivity(param.thisObject);
                    }
                }));
    }

    private static void reapplyFromActivity(Object activity) {
        if (!isExactTarget(activity)) {
            return;
        }
        try {
            Object engine = XposedHelpers.getObjectField(activity, "f");
            if (engine != null) {
                boolean currentNight = isNight((Activity) activity);
                boolean engineNight = isTextureEngine(engine);
                if (currentNight != engineNight) {
                    textureEngines.put(engine, currentNight);
                    applyOrClear(engine);
                    View decor = ((Activity) activity).getWindow().getDecorView();
                    Activity target = (Activity) activity;
                    synchronized (pendingRecreates) {
                        if (!pendingRecreates.containsKey(target)) {
                            pendingRecreates.put(target, Boolean.TRUE);
                            if (!decor.post(new RecreateActivity(target))) {
                                pendingRecreates.remove(target);
                            }
                        }
                    }
                    return;
                }
                applyOrClear(engine);
            }
        } catch (Throwable ignored) {
            // Field names are version-specific.  A miss must not affect WeChat.
        }
    }

    private static void applyOrClear(Object engine) {
        try {
            if (!textureEngines.containsKey(engine)) {
                return;
            }
            Object surface = XposedHelpers.getObjectField(engine, "i");
            if (!(surface instanceof TextureView)) {
                return;
            }
            View view = (View) surface;
            if (isTextureEngine(engine)) {
                view.setRenderEffect(passThrough);
                effectedEngines.add(engine);
            } else if (effectedEngines.remove(engine)) {
                view.setRenderEffect(null);
            }
        } catch (Throwable ignored) {
            // Optional visual probe: original rendering remains untouched.
        }
    }

    private static boolean isTextureEngine(Object engine) {
        return Boolean.TRUE.equals(textureEngines.get(engine));
    }

    private static Activity activityForEngine(Object engine) {
        if (engine == null) {
            return null;
        }
        try {
            Object value = XposedHelpers.getObjectField(engine, "m");
            if (value instanceof Activity) {
                return (Activity) value;
            }
        } catch (Throwable ignored) {
        }
        try {
            Object value = XposedHelpers.getObjectField(engine, "d");
            if (value instanceof Activity) {
                return (Activity) value;
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    private static boolean isExactTarget(Object activity) {
        return activity != null && TARGET_ACTIVITY.equals(activity.getClass().getName());
    }

    private static boolean isNight(Activity activity) {
        try {
            int mode = activity.getResources().getConfiguration().uiMode
                    & Configuration.UI_MODE_NIGHT_MASK;
            return mode == Configuration.UI_MODE_NIGHT_YES;
        } catch (Throwable ignored) {
            return false;
        }
    }

    private static final class RecreateActivity implements Runnable {
        private final Activity activity;

        RecreateActivity(Activity activity) {
            this.activity = activity;
        }

        @Override
        public void run() {
            try {
                if (!activity.isFinishing() && !activity.isDestroyed()) {
                    activity.recreate();
                }
            } catch (Throwable ignored) {
            } finally {
                pendingRecreates.remove(activity);
            }
        }
    }
}
