package com.example.voiceguard;

import android.app.*;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.IBinder;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.Locale;

public class CallService extends Service {
    private static final String TAG = "CallService";
    private static final String CHANNEL_ID = "CallServiceChannel";
    public static final String ACTION_STT_RESULT = "com.example.voiceguard.STT_RESULT";
    public static final String EXTRA_STT_TEXT = "stt_text";

    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isListening = false;
    private int consecutiveErrors = 0;

    private AudioManager audioManager;
    private AudioFocusRequest audioFocusRequest;
    private final AudioManager.OnAudioFocusChangeListener audioFocusChangeListener = focusChange -> {};
    private int previousAudioMode = AudioManager.MODE_NORMAL;
    private boolean previousSpeakerphone = false;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "RECORD_AUDIO not granted; stopping CallService");
            stopSelf();
            return;
        }

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            Log.w(TAG, "SpeechRecognizer not available; stopping CallService");
            stopSelf();
            return;
        }

        audioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
        if (audioManager != null) {
            previousAudioMode = audioManager.getMode();
            previousSpeakerphone = audioManager.isSpeakerphoneOn();
            audioManager.setMode(AudioManager.MODE_IN_COMMUNICATION);
            audioManager.setSpeakerphoneOn(true);
            requestAudioFocus();
        }

        Intent openSttIntent = new Intent(this, SttAnalysisActivity.class);
        PendingIntent openSttPendingIntent = PendingIntent.getActivity(
                this,
                0,
                openSttIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("VoiceGuard 실시간 분석 중")
                .setContentText("통화 내용을 분석하여 피싱 여부를 감시하고 있습니다.")
                .setSmallIcon(android.R.drawable.ic_btn_speak_now)
                .setContentIntent(openSttPendingIntent)
                .build();

        startForeground(1, notification);

        try {
            initSpeechRecognizer();
        } catch (Exception e) {
            Log.e(TAG, "initSpeechRecognizer failed", e);
            stopSelf();
        }
    }

    private void requestAudioFocus() {
        if (audioManager == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            AudioAttributes attributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_VOICE_COMMUNICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build();
            audioFocusRequest = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(attributes)
                    .setOnAudioFocusChangeListener(audioFocusChangeListener)
                    .build();
            audioManager.requestAudioFocus(audioFocusRequest);
        } else {
            audioManager.requestAudioFocus(
                    audioFocusChangeListener,
                    AudioManager.STREAM_VOICE_CALL,
                    AudioManager.AUDIOFOCUS_GAIN_TRANSIENT
            );
        }
    }

    private void abandonAudioFocus() {
        if (audioManager == null) {
            return;
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && audioFocusRequest != null) {
            audioManager.abandonAudioFocusRequest(audioFocusRequest);
        } else {
            audioManager.abandonAudioFocus(audioFocusChangeListener);
        }
    }

    private void initSpeechRecognizer() {
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.KOREAN.toString());
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) { Log.d(TAG, "onReadyForSpeech"); }
            @Override public void onBeginningOfSpeech() { Log.d(TAG, "onBeginningOfSpeech"); }
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() { Log.d(TAG, "onEndOfSpeech"); }
            @Override public void onError(int error) {
                Log.e(TAG, "onError: " + error);
                isListening = false;
                // Back off on errors to avoid tight restart loops during calls.
                long delayMs = (error == SpeechRecognizer.ERROR_NO_MATCH
                        || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT) ? 500L : 1500L;
                restartListeningWithDelay(delayMs);
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String text = matches.get(0);
                    broadcastResult(text);
                }
                isListening = false;
                restartListeningWithDelay(300L);
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    broadcastResult(matches.get(0));
                }
            }

            @Override public void onEvent(int eventType, Bundle params) {}
        });

        restartListeningWithDelay(0L);
    }

    private void restartListeningWithDelay(long delayMs) {
        mainHandler.removeCallbacksAndMessages(null);
        mainHandler.postDelayed(() -> {
            if (speechRecognizer == null || isListening) {
                return;
            }
            try {
                isListening = true;
                speechRecognizer.startListening(recognizerIntent);
                consecutiveErrors = 0;
            } catch (Exception e) {
                Log.e(TAG, "startListening failed", e);
                isListening = false;
                consecutiveErrors++;
                long backoff = Math.min(3000L, 500L * (1L + consecutiveErrors));
                restartListeningWithDelay(backoff);
            }
        }, delayMs);
    }

    private void broadcastResult(String text) {
        Intent intent = new Intent(ACTION_STT_RESULT);
        intent.putExtra(EXTRA_STT_TEXT, text);
        sendBroadcast(intent);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (speechRecognizer != null) {
            try {
                speechRecognizer.stopListening();
            } catch (Exception e) {
                Log.w(TAG, "stopListening failed", e);
            }
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
        mainHandler.removeCallbacksAndMessages(null);
        if (audioManager != null) {
            abandonAudioFocus();
            audioManager.setSpeakerphoneOn(previousSpeakerphone);
            audioManager.setMode(previousAudioMode);
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Call Analysis Service Channel",
                    NotificationManager.IMPORTANCE_LOW
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }
}
