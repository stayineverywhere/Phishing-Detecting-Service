package com.example.voiceguard;

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

public class SmsAnalysisActivity extends AppCompatActivity {

    private static final String TAG = "SmsAnalysisActivity";
    private static final int MAX_RETRY = 2;
    private static final long RETRY_DELAY_MS = 1200;

    private EditText etSmsInput;
    private CardView cardResult;
    private TextView tvRiskPercent, tvRiskLevel, tvKeywords, tvTags, tvGuide;
    private ProgressBar progressRisk;
    private ListView lvHistory;
    private List<String> historyList = new ArrayList<>();
    private ArrayAdapter<String> historyAdapter;

    private static final String SAMPLE_SMS =
            "[국민은행] 고객님 계좌가 이상거래로 탐지되었습니다. " +
                    "즉시 아래 링크에서 본인인증을 완료해주세요. http://kb-check.xyz " +
                    "24시간 내 미완료 시 계좌가 동결됩니다.";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sms_analysis);

        etSmsInput    = findViewById(R.id.etSmsInput);
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

        fetchHistory(0);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        findViewById(R.id.btnAnalyzeSms).setOnClickListener(v -> {
            String input = etSmsInput.getText().toString().trim();
            if (input.isEmpty()) {
                Toast.makeText(this, "문자 내용을 입력해주세요", Toast.LENGTH_SHORT).show();
                return;
            }
            performAnalysis(input);
        });
        findViewById(R.id.btnClearSms).setOnClickListener(v -> {
            etSmsInput.setText("");
            cardResult.setVisibility(View.GONE);
        });
        findViewById(R.id.btnSampleSms).setOnClickListener(v -> etSmsInput.setText(SAMPLE_SMS));
    }

    private void performAnalysis(String text) {
        performAnalysis(text, 0);
    }

    private void performAnalysis(String text, int attempt) {
        AnalysisApi api = ApiClient.getApi();
        api.analyzeMessage(new AnalysisRequest(text)).enqueue(new Callback<AnalysisResponse>() {
            @Override
            public void onResponse(Call<AnalysisResponse> call, Response<AnalysisResponse> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    Toast.makeText(SmsAnalysisActivity.this, "서버 응답 오류", Toast.LENGTH_SHORT).show();
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
                Log.e(TAG, "analyzeMessage failed", t);
                if (attempt < MAX_RETRY) {
                    new Handler(Looper.getMainLooper()).postDelayed(
                        () -> performAnalysis(text, attempt + 1),
                        RETRY_DELAY_MS
                    );
                    return;
                }
                Toast.makeText(SmsAnalysisActivity.this, "서버 연결 실패", Toast.LENGTH_SHORT).show();
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
                    if (item == null || item.type == null || !"message".equalsIgnoreCase(item.type)) {
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