package com.example.voiceguard;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import com.example.voiceguard.api.AnalysisApi;
import com.example.voiceguard.api.AnalysisRequest;
import com.example.voiceguard.api.AnalysisResponse;
import com.example.voiceguard.api.ApiClient;
import java.text.SimpleDateFormat;
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
    private List<AnalysisResponse> historyList = new ArrayList<>();
    private List<String> historySummaries = new ArrayList<>();
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

        historyAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, historySummaries);
        lvHistory.setAdapter(historyAdapter);

        lvHistory.setOnItemClickListener((parent, view, position, id) -> {
            if (position < historyList.size()) {
                showDetailDialog(historyList.get(position));
            }
        });

        // Check for SMS body from Intent and perform automatic analysis
        handleIntent(getIntent());

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

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        handleIntent(intent);
    }

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        String incomingSms = intent.getStringExtra("sms_body");
        if (incomingSms != null && !incomingSms.isEmpty()) {
            etSmsInput.setText(incomingSms);
            Toast.makeText(this, "새 문자를 분석합니다...", Toast.LENGTH_SHORT).show();
            performAnalysis(incomingSms); // 자동으로 분석 함수 호출
        }
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
                historySummaries.clear();
                for (AnalysisResponse item : response.body()) {
                    if (item == null || item.type == null || !"message".equalsIgnoreCase(item.type)) {
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

        tvTitle.setText(getString(R.string.menu_sms_title));
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
