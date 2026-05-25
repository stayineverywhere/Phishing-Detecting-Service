package com.example.voiceguard;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import com.example.voiceguard.api.AnalysisApi;
import com.example.voiceguard.api.AnalysisRequest;
import com.example.voiceguard.api.AnalysisResponse;
import com.example.voiceguard.api.ApiClient;
import java.util.*;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class SttAnalysisActivity extends AppCompatActivity {

    private static final String TAG = "SttAnalysisActivity";
    private static final int MAX_RETRY = 2;
    private static final long RETRY_DELAY_MS = 1200;
    private static final long AUTO_ANALYZE_DEBOUNCE_MS = 1200;
    private static final long AUTO_ANALYZE_MIN_INTERVAL_MS = 2500;
    private static final int AUTO_ANALYZE_MIN_LEN = 6;

    private EditText etSttInput;
    private CardView cardResult;
    private TextView tvRiskPercent, tvRiskLevel, tvKeywords, tvTags, tvGuide;
    private ProgressBar progressRisk;
    private ListView lvHistory;
    private List<String> historyList = new ArrayList<>();
    private ArrayAdapter<String> historyAdapter;
    private final Handler sttHandler = new Handler(Looper.getMainLooper());
    private String lastAnalyzedText = "";
    private long lastAnalyzedAt = 0L;

    private BroadcastReceiver sttReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (CallService.ACTION_STT_RESULT.equals(intent.getAction())) {
                String text = intent.getStringExtra(CallService.EXTRA_STT_TEXT);
                if (text != null) {
                    etSttInput.setText(text);
                    scheduleAutoAnalyze(text);
                }
            }
        }
    };

    private static final String SAMPLE1 =
            "저희 수사과에서 금융사기주범 김명철을 검거했는데 압수수색 당시 현장에서 " +
                    "위조된 신분증 대포통장 그리고 보안카드 등을 대량으로 압수했어요. " +
                    "본인 명의로 압수된 우리은행하고 하나은행 측의 계좌는 동결 처리되어 있는 상태고요. " +
                    "지금 즉시 계좌번호와 비밀번호를 확인해주셔야 합니다.";

    private static final String SAMPLE2 =
            "아빠가 교통사고가 나서 수술을 해야 하는데 혹시 돈 좀 보내줄 수 있을까? " +
                    "지금 당장 200만원이 필요해. 아무에게도 말하지 말고 빨리 이체해줘.";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_stt_analysis);

        etSttInput    = findViewById(R.id.etSttInput);
        cardResult    = findViewById(R.id.cardResult);
        tvRiskPercent = findViewById(R.id.tvRiskPercent);
        tvRiskLevel   = findViewById(R.id.tvRiskLevel);
        tvKeywords    = findViewById(R.id.tvKeywords);
        tvTags        = findViewById(R.id.tvTags);
        tvGuide       = findViewById(R.id.tvGuide);
        progressRisk  = findViewById(R.id.progressRisk);
        lvHistory     = findViewById(R.id.lvHistory);

        historyAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, historyList);
        lvHistory.setAdapter(historyAdapter);

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
            etSttInput.setText("");
            cardResult.setVisibility(View.GONE);
        });
        findViewById(R.id.btnSample1).setOnClickListener(v -> etSttInput.setText(SAMPLE1));
        findViewById(R.id.btnSample2).setOnClickListener(v -> etSttInput.setText(SAMPLE2));
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        String incomingStt = intent.getStringExtra("stt_text");
        if (incomingStt != null && !incomingStt.isEmpty()) {
            etSttInput.setText(incomingStt);
            if (intent.getBooleanExtra("auto_analyze", false)) {
                scheduleAutoAnalyze(incomingStt);
            }
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
                for (AnalysisResponse item : response.body()) {
                    if (item == null || item.type == null || !"stt".equalsIgnoreCase(item.type)) {
                        continue;
                    }

                    String text = item.text == null ? "" : item.text;
                    String summary = item.risk_level + " " + item.risk_score + "% - "
                        + text.substring(0, Math.min(30, text.length())) + "...";
                    historyList.add(summary);
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
}