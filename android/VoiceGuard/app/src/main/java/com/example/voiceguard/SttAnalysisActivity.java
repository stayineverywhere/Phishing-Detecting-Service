package com.example.voiceguard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.example.voiceguard.api.AnalysisApi;
import com.example.voiceguard.api.AnalysisRequest;
import com.example.voiceguard.api.AnalysisResponse;
import com.example.voiceguard.api.ApiClient;
import java.text.SimpleDateFormat;
import java.util.*;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SttAnalysisActivity extends AppCompatActivity {

    private static final String TAG = "SttAnalysisActivity";
    private static final int REQ_RECORD_AUDIO = 2001;
    private static final int MAX_RETRY = 2;
    private static final long RETRY_DELAY_MS = 1200;
    private static final long AUTO_ANALYZE_DEBOUNCE_MS = 1200;
    private static final long AUTO_ANALYZE_MIN_INTERVAL_MS = 2500;
    private static final int AUTO_ANALYZE_MIN_LEN = 6;

    private EditText etSttInput;
    private ImageButton btnMicStt;
    private CardView cardResult;
    private TextView tvRiskPercent, tvRiskLevel, tvKeywords, tvTags, tvGuide;
    private ProgressBar progressRisk;
    private ListView lvHistory;
    private List<AnalysisResponse> historyList = new ArrayList<>();
    private ArrayAdapter<String> historyAdapter;
    private List<String> historySummaries = new ArrayList<>();
    private final Handler sttHandler = new Handler(Looper.getMainLooper());
    private String lastAnalyzedText = "";
    private long lastAnalyzedAt = 0L;
    private String textBeforeMic = "";
    private SpeechRecognizer speechRecognizer;
    private Intent recognizerIntent;
    private boolean isMicListening = false;
    private boolean pendingMicStart = false;
    private String micStableText = "";
    private String micPartialText = "";
    private final Handler micHandler = new Handler(Looper.getMainLooper());
    private boolean isRestartScheduled = false;

    private BroadcastReceiver sttReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (CallService.ACTION_STT_RESULT.equals(intent.getAction())) {
                String text = intent.getStringExtra(CallService.EXTRA_STT_TEXT);
                if (text != null) {
                    updateInputText(text, true);
                }
            }
        }
    };

    private static final String SAMPLE1 =
            "저희 수사과에서 금융사기주범 OOO을 검거했는데 압수수색 당시 현장에서 " +
                    "위조된 신분증 대포통장 그리고 보안카드 등을 대량으로 압수했어요. " +
                    "본인 명의로 압수된 OO은행하고 XX은행 측의 계좌는 동결 처리되어 있는 상태고요. " +
                    "지금 즉시 계좌번호와 비밀번호를 확인해주셔야 합니다.";

    private static final String SAMPLE2 =
            "아빠가 교통사고가 나서 수술을 해야 하는데 혹시 돈 좀 보내줄 수 있을까? " +
                    "지금 당장 200만원이 필요해. 아무에게도 말하지 말고 빨리 이체해줘.";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stt_analysis);

        etSttInput    = findViewById(R.id.etSttInput);
        btnMicStt     = findViewById(R.id.btnMicStt);
        cardResult    = findViewById(R.id.cardResult);
        tvRiskPercent = findViewById(R.id.tvRiskPercent);
        tvRiskLevel   = findViewById(R.id.tvRiskLevel);
        tvKeywords    = findViewById(R.id.tvKeywords);
        tvTags        = findViewById(R.id.tvTags);
        tvGuide       = findViewById(R.id.tvGuide);
        progressRisk  = findViewById(R.id.progressRisk);
        lvHistory     = findViewById(R.id.lvHistory);

        btnMicStt.setOnClickListener(v -> toggleMic());
        initSpeechRecognizer();

        historyAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, historySummaries);
        lvHistory.setAdapter(historyAdapter);

        lvHistory.setOnItemClickListener((parent, view, position, id) -> {
            if (position < historyList.size()) {
                showDetailDialog(historyList.get(position));
            }
        });

        // Check for STT text from Intent
        handleIntent(getIntent());

        fetchHistory(0);

        // Register receiver for real-time STT
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(sttReceiver, new IntentFilter(CallService.ACTION_STT_RESULT), Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(sttReceiver, new IntentFilter(CallService.ACTION_STT_RESULT));
        }

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnAnalyzeStt).setOnClickListener(v -> {
            String input = etSttInput.getText().toString().trim();
            if (input.isEmpty()) {
                Toast.makeText(this, "전사문을 입력해주세요", Toast.LENGTH_SHORT).show();
                return;
            }
            performAnalysis(input);
        });
        findViewById(R.id.btnClearStt).setOnClickListener(v -> {
            updateInputText("", false);
            cardResult.setVisibility(View.GONE);
        });
        findViewById(R.id.btnSample1).setOnClickListener(v -> updateInputText(SAMPLE1, false));
        findViewById(R.id.btnSample2).setOnClickListener(v -> updateInputText(SAMPLE2, false));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        String incomingStt = intent.getStringExtra("stt_text");
        if (incomingStt != null && !incomingStt.isEmpty()) {
            updateInputText(incomingStt, intent.getBooleanExtra("auto_analyze", false));
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(sttReceiver);
        } catch (Exception e) {
            // Ignore if not registered
        }
        sttHandler.removeCallbacksAndMessages(null);
        micHandler.removeCallbacksAndMessages(null);
        if (speechRecognizer != null) {
            speechRecognizer.destroy();
            speechRecognizer = null;
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_RECORD_AUDIO) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted && pendingMicStart) {
                pendingMicStart = false;
                startMicListening();
            } else {
                pendingMicStart = false;
                Toast.makeText(this, "마이크 권한이 필요합니다", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void scheduleAutoAnalyze(String text) {
        if (text == null) {
            return;
        }
        String trimmed = text.trim();
        if (trimmed.length() < AUTO_ANALYZE_MIN_LEN) {
            return;
        }
        sttHandler.removeCallbacksAndMessages(null);
        sttHandler.postDelayed(() -> {
            String current = etSttInput.getText().toString().trim();
            if (current.length() < AUTO_ANALYZE_MIN_LEN) {
                return;
            }
            long now = System.currentTimeMillis();
            if (current.equals(lastAnalyzedText) && now - lastAnalyzedAt < AUTO_ANALYZE_MIN_INTERVAL_MS) {
                return;
            }
            lastAnalyzedText = current;
            lastAnalyzedAt = now;
            performAnalysis(current);
        }, AUTO_ANALYZE_DEBOUNCE_MS);
    }

    private void updateInputText(String text, boolean autoAnalyze) {
        etSttInput.setText(text);
        etSttInput.setSelection(etSttInput.getText().length());
        if (autoAnalyze) {
            scheduleAutoAnalyze(text);
        }
    }

    private void initSpeechRecognizer() {
        if (speechRecognizer != null) {
            return;
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            btnMicStt.setEnabled(false);
            Toast.makeText(this, "음성 인식을 사용할 수 없습니다", Toast.LENGTH_SHORT).show();
            return;
        }
        speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this);
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ko-KR");
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 3000);

        speechRecognizer.setRecognitionListener(new RecognitionListener() {
            @Override public void onReadyForSpeech(Bundle params) {
                Log.d(TAG, "onReadyForSpeech");
            }
            @Override public void onBeginningOfSpeech() {
                Log.d(TAG, "onBeginningOfSpeech");
            }
            @Override public void onRmsChanged(float rmsdB) {}
            @Override public void onBufferReceived(byte[] buffer) {}
            @Override public void onEndOfSpeech() {
                Log.d(TAG, "onEndOfSpeech");
                scheduleRestartIfNeeded(500);
            }

            @Override
            public void onError(int error) {
                Log.e(TAG, "SpeechRecognizer Error: " + error);
                if (error == SpeechRecognizer.ERROR_NO_MATCH) {
                    // Ignore no match to avoid annoying toasts
                    Log.d(TAG, "No speech match found.");
                } else if (error != SpeechRecognizer.ERROR_CLIENT) {
                    Toast.makeText(SttAnalysisActivity.this, "음성 인식 오류: " + error, Toast.LENGTH_SHORT).show();
                }
                if (isMicListening && error != SpeechRecognizer.ERROR_CLIENT) {
                    scheduleRestartIfNeeded(700);
                }
            }

            @Override
            public void onResults(Bundle results) {
                ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    String recognizedText = matches.get(0);
                    micStableText = (micStableText + recognizedText).trim() + " ";
                    micPartialText = "";
                    updateInputText(micStableText.trim(), true);
                }
                if (isMicListening) {
                    scheduleRestartIfNeeded(250);
                }
            }

            @Override
            public void onPartialResults(Bundle partialResults) {
                ArrayList<String> matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
                if (matches != null && !matches.isEmpty()) {
                    micPartialText = matches.get(0);
                    updateInputText((micStableText + micPartialText).trim(), false);
                }
            }

            @Override public void onEvent(int eventType, Bundle params) {}
        });
    }

    private void toggleMic() {
        if (isMicListening) {
            stopMicListening(true);
            return;
        }
        if (!hasAudioPermission()) {
            pendingMicStart = true;
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.RECORD_AUDIO},
                    REQ_RECORD_AUDIO);
            return;
        }
        startMicListening();
    }

    private boolean hasAudioPermission() {
        return ContextCompat.checkSelfPermission(this, android.Manifest.permission.RECORD_AUDIO)
                == PackageManager.PERMISSION_GRANTED;
    }

    private void startMicListening() {
        initSpeechRecognizer();
        if (speechRecognizer == null) {
            return;
        }
        try {
            String currentText = etSttInput.getText().toString().trim();
            micStableText = currentText.isEmpty() ? "" : currentText + " ";
            micPartialText = "";
            isMicListening = true;
            isRestartScheduled = false;
            updateMicButton();
            speechRecognizer.startListening(recognizerIntent);
        } catch (Exception e) {
            Log.e(TAG, "startMicListening failed", e);
            isMicListening = false;
            updateMicButton();
            Toast.makeText(this, "음성 입력을 시작할 수 없습니다", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopMicListening(boolean analyzeCurrent) {
        isMicListening = false;
        isRestartScheduled = false;
        micHandler.removeCallbacksAndMessages(null);
        if (speechRecognizer != null) {
            try {
                speechRecognizer.stopListening();
                speechRecognizer.cancel();
            } catch (Exception e) {
                Log.w(TAG, "stopMicListening failed", e);
            }
        }
        updateMicButton();
        if (analyzeCurrent) {
            String current = etSttInput.getText().toString().trim();
            if (!current.isEmpty()) {
                scheduleAutoAnalyze(current);
            }
        }
    }

    private void scheduleRestartIfNeeded(long delayMs) {
        if (!isMicListening || speechRecognizer == null || isRestartScheduled) {
            return;
        }
        isRestartScheduled = true;
        micHandler.postDelayed(() -> {
            isRestartScheduled = false;
            if (isMicListening && speechRecognizer != null) {
                try {
                    speechRecognizer.startListening(recognizerIntent);
                } catch (Exception e) {
                    Log.w(TAG, "restartMicListening failed", e);
                }
            }
        }, delayMs);
    }

    private void restartMicListening() {
        scheduleRestartIfNeeded(250);
    }

    private void updateMicButton() {
        if (isMicListening) {
            btnMicStt.setColorFilter(ContextCompat.getColor(this, R.color.risk_danger));
            btnMicStt.setAlpha(0.8f);
        } else {
            btnMicStt.setColorFilter(ContextCompat.getColor(this, R.color.primary));
            btnMicStt.setAlpha(1.0f);
        }
        btnMicStt.setContentDescription(isMicListening ? "음성 입력 중지" : "음성 입력 시작");
    }

    private void performAnalysis(String text) {
        performAnalysis(text, 0);
    }

    private void performAnalysis(String text, int attempt) {
        AnalysisApi api = ApiClient.getApi();
        api.analyzeStt(new AnalysisRequest(text)).enqueue(new Callback<AnalysisResponse>() {
            @Override
            public void onResponse(Call<AnalysisResponse> call, Response<AnalysisResponse> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    Toast.makeText(SttAnalysisActivity.this, "서버 응답 오류", Toast.LENGTH_SHORT).show();
                    return;
                }

                AnalysisResponse result = response.body();

                cardResult.setVisibility(View.VISIBLE);
                tvRiskPercent.setText(result.risk_score + "%");
                tvRiskLevel.setText(result.risk_level);
                progressRisk.setProgress(result.risk_score);

                int color;
                if (result.risk_score <= 29)      color = Color.parseColor("#4CAF50");
                else if (result.risk_score <= 59) color = Color.parseColor("#FF9800");
                else if (result.risk_score <= 79) color = Color.parseColor("#FF5722");
                else                               color = Color.parseColor("#F44336");

                tvRiskPercent.setTextColor(color);
                tvRiskLevel.setTextColor(color);

                if (result.detected_keywords == null || result.detected_keywords.isEmpty()) {
                    tvKeywords.setText("감지된 위험 키워드 없음");
                } else {
                    tvKeywords.setText(String.join("  ", result.detected_keywords));
                }

                if (result.tags == null || result.tags.isEmpty()) {
                    tvTags.setText("해당 없음");
                } else {
                    tvTags.setText(String.join("  ", result.tags));
                }

                tvGuide.setText(result.guide == null ? "" : result.guide);

                fetchHistory(0);
            }

            @Override
            public void onFailure(Call<AnalysisResponse> call, Throwable t) {
                Log.e(TAG, "analyzeStt failed", t);
                if (attempt < MAX_RETRY) {
                    new Handler(Looper.getMainLooper()).postDelayed(
                        () -> performAnalysis(text, attempt + 1),
                        RETRY_DELAY_MS
                    );
                    return;
                }
                Toast.makeText(SttAnalysisActivity.this, "서버 연결 실패", Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void fetchHistory(int attempt) {
        AnalysisApi api = ApiClient.getApi();
        api.getHistory().enqueue(new Callback<List<AnalysisResponse>>() {
            @Override
            public void onResponse(Call<List<AnalysisResponse>> call, Response<List<AnalysisResponse>> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    return;
                }

                historyList.clear();
                historySummaries.clear();
                for (AnalysisResponse item : response.body()) {
                    if (item == null || item.type == null || !"stt".equalsIgnoreCase(item.type)) {
                        continue;
                    }

                    historyList.add(item);
                    String text = item.text == null ? "" : item.text;
                    String summary = item.risk_level + " " + item.risk_score + "% - "
                        + text.substring(0, Math.min(30, text.length())) + "...";
                    historySummaries.add(summary);
                }

                historyAdapter.notifyDataSetChanged();
            }

            @Override
            public void onFailure(Call<List<AnalysisResponse>> call, Throwable t) {
                Log.e(TAG, "fetchHistory failed", t);
                if (attempt < MAX_RETRY) {
                    new Handler(Looper.getMainLooper()).postDelayed(
                        () -> fetchHistory(attempt + 1),
                        RETRY_DELAY_MS
                    );
                }
            }
        });
    }

    private void showDetailDialog(AnalysisResponse item) {
        View dialogView = getLayoutInflater().inflate(R.layout.dialog_analysis_detail, null);
        AlertDialog dialog = new AlertDialog.Builder(this, R.style.TransparentDialog)
                .setView(dialogView)
                .create();

        TextView tvTitle = dialogView.findViewById(R.id.tvDialogTitle);
        TextView tvRisk = dialogView.findViewById(R.id.tvDialogRisk);
        TextView tvDateTime = dialogView.findViewById(R.id.tvDialogDateTime);
        TextView tvKeywords = dialogView.findViewById(R.id.tvDialogKeywords);
        TextView tvText = dialogView.findViewById(R.id.tvDialogText);
        View layoutKeywords = dialogView.findViewById(R.id.layoutKeywords);
        Button btnClose = dialogView.findViewById(R.id.btnDialogClose);

        tvTitle.setText(getString(R.string.menu_stt_title));
        tvRisk.setText(item.risk_level + " (" + item.risk_score + "%)");
        
        // Color for risk
        int color;
        if (item.risk_score <= 29)      color = Color.parseColor("#34A853"); // risk_safe
        else if (item.risk_score <= 59) color = Color.parseColor("#FBBC04"); // risk_warning
        else if (item.risk_score <= 79) color = Color.parseColor("#F06292"); // risk_suspect
        else                               color = Color.parseColor("#EA4335"); // risk_danger
        tvRisk.setTextColor(color);

        // Format Date
        String formattedDate = item.created_at;
        try {
            // Typical ISO format from Django/Server: 2024-03-20T14:30:00Z or 2024-03-20T14:30:00.000Z
            SimpleDateFormat inputFormat = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault());
            SimpleDateFormat outputFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
            Date date = inputFormat.parse(item.created_at);
            if (date != null) {
                formattedDate = outputFormat.format(date);
            }
        } catch (Exception e) {
            Log.e(TAG, "Date parse error", e);
        }
        tvDateTime.setText(formattedDate);

        if (item.detected_keywords == null || item.detected_keywords.isEmpty()) {
            layoutKeywords.setVisibility(View.GONE);
        } else {
            layoutKeywords.setVisibility(View.VISIBLE);
            tvKeywords.setText(String.join(", ", item.detected_keywords));
        }

        tvText.setText(item.text);

        btnClose.setOnClickListener(v -> dialog.dismiss());
        dialog.show();
    }
}

