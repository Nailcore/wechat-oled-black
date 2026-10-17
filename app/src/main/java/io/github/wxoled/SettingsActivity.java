package io.github.wxoled;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

public class SettingsActivity extends Activity {
    private static final long RELAUNCH_DELAY_MS = 900;
    private static final String WECHAT = "com.tencent.mm";
    private final Handler main = new Handler(Looper.getMainLooper());
    private Button restart;

    @Override // android.app.Activity
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        int iDp = dp(24);
        LinearLayout linearLayout = new LinearLayout(this);
        linearLayout.setOrientation(1);
        linearLayout.setPadding(iDp, iDp * 3, iDp, iDp);
        linearLayout.setBackgroundColor(-16777216);
        TextView textView = new TextView(this);
        textView.setText(R.string.app_name);
        textView.setTextColor(-1);
        textView.setTextSize(2, 26.0f);
        linearLayout.addView(textView);
        TextView textView2 = new TextView(this);
        textView2.setText(R.string.settings_hint);
        textView2.setTextColor(-6645094);
        textView2.setTextSize(2, 15.0f);
        textView2.setLineSpacing(0.0f, 1.3f);
        textView2.setPadding(0, dp(12), 0, dp(32));
        linearLayout.addView(textView2);
        Button button = new Button(this);
        this.restart = button;
        button.setText(R.string.restart_wechat);
        this.restart.setAllCaps(false);
        this.restart.setTextSize(2, 17.0f);
        this.restart.setOnClickListener(new View.OnClickListener() {
            @Override // android.view.View.OnClickListener
            public final void onClick(View view) {
                SettingsActivity.this.restartWeChat();
            }
        });
        linearLayout.addView(this.restart, new LinearLayout.LayoutParams(-1, dp(56)));
        setContentView(linearLayout);
    }

    void m0lambda$onCreate$0$iogithubwxoledSettingsActivity(View view) {
        restartWeChat();
    }

    private void restartWeChat() {
        Intent launchIntentForPackage = getPackageManager().getLaunchIntentForPackage(WECHAT);
        if (launchIntentForPackage == null) {
            Toast.makeText(this, R.string.wechat_missing, 1).show();
            return;
        }
        this.restart.setEnabled(false);
        ((ActivityManager) getSystemService("activity")).killBackgroundProcesses(WECHAT);
        sendOrderedBroadcast(new Intent("io.github.wxoled.action.RESTART_WECHAT").setPackage(WECHAT).addFlags(268435456), null, new AnonymousClass1(launchIntentForPackage), this.main, 0, null, null);
    }

    class AnonymousClass1 extends BroadcastReceiver {
        final Intent val$launch;

        AnonymousClass1(Intent intent) {
            this.val$launch = intent;
        }

        @Override // android.content.BroadcastReceiver
        public void onReceive(Context context, Intent intent) {
            boolean z = getResultCode() == 1;
            Bundle resultExtras = getResultExtras(false);
            final PendingIntent pendingIntent = resultExtras == null ? null : (PendingIntent) resultExtras.getParcelable("relaunch");
            Handler handler = SettingsActivity.this.main;
            final Intent intent2 = this.val$launch;
            handler.postDelayed(new Runnable() {
                @Override // java.lang.Runnable
                public final void run() {
                    AnonymousClass1.this.m1lambda$onReceive$0$iogithubwxoledSettingsActivity$1(pendingIntent, intent2);
                }
            }, z ? SettingsActivity.RELAUNCH_DELAY_MS : 0L);
        }

        void m1lambda$onReceive$0$iogithubwxoledSettingsActivity$1(PendingIntent pendingIntent, Intent intent) {
            SettingsActivity.this.restart.setEnabled(true);
            SettingsActivity.this.openWeChat(pendingIntent, intent);
        }
    }

    public void openWeChat(PendingIntent pendingIntent, Intent intent) {
        if (pendingIntent != null) {
            try {
                ActivityOptions activityOptionsMakeBasic = ActivityOptions.makeBasic();
                if (Build.VERSION.SDK_INT >= 34) {
                    activityOptionsMakeBasic.setPendingIntentBackgroundActivityStartMode(1);
                }
                pendingIntent.send(this, 0, null, null, null, null, activityOptionsMakeBasic.toBundle());
                return;
            } catch (PendingIntent.CanceledException unused) {
            }
        }
        intent.addFlags(268435456);
        startActivity(intent);
    }

    private int dp(int i) {
        return Math.round(i * getResources().getDisplayMetrics().density);
    }
}
