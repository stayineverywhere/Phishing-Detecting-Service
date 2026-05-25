package com.example.voiceguard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import android.content.pm.PackageManager;
import android.telephony.TelephonyManager;
import android.util.Log;
import androidx.core.content.ContextCompat;

public class CallReceiver extends BroadcastReceiver {
    private static final String TAG = "CallReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(TelephonyManager.ACTION_PHONE_STATE_CHANGED)) {
            String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
            Log.d(TAG, "Phone State Changed: " + state);

            if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)) {
                if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.RECORD_AUDIO)
                        != PackageManager.PERMISSION_GRANTED) {
                    Log.w(TAG, "RECORD_AUDIO not granted, skip CallService start");
                    return;
                }
                // Call started (or accepted)
                Intent serviceIntent = new Intent(context, CallService.class);
                context.startForegroundService(serviceIntent);

                Intent activityIntent = new Intent(context, SttAnalysisActivity.class);
                activityIntent.putExtra("auto_analyze", true);
                activityIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
                context.startActivity(activityIntent);
            } else if (TelephonyManager.EXTRA_STATE_IDLE.equals(state)) {
                // Call ended
                Intent serviceIntent = new Intent(context, CallService.class);
                context.stopService(serviceIntent);
            }
        }
    }
}
