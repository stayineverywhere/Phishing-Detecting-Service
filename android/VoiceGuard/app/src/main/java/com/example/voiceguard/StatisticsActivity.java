package com.example.voiceguard;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import com.example.voiceguard.api.AnalysisApi;
import com.example.voiceguard.api.AnalysisResponse;
import com.example.voiceguard.api.ApiClient;
import java.util.List;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class StatisticsActivity extends AppCompatActivity {

    private static final String TAG = "StatisticsActivity";
    private static final int MAX_RETRY = 2;
    private static final long RETRY_DELAY_MS = 1200;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statistics);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        fetchHistoryAndUpdate(0);
    }

    @Override
    protected void onResume() {
        super.onResume();
        fetchHistoryAndUpdate(0);
    }

    private void fetchHistoryAndUpdate(int attempt) {
        AnalysisApi api = ApiClient.getApi();
        api.getHistory().enqueue(new Callback<List<AnalysisResponse>>() {
            @Override
            public void onResponse(Call<List<AnalysisResponse>> call, Response<List<AnalysisResponse>> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    return;
                }

                List<AnalysisResponse> items = response.body();
                int total = items.size();
                int danger = 0;
                int sttCount = 0;
                int riskSum = 0;

                for (AnalysisResponse item : items) {
                    if (item == null) {
                        continue;
                    }
                    if ("stt".equalsIgnoreCase(item.type)) {
                        sttCount++;
                    }
                    if ("HIGH".equalsIgnoreCase(item.risk_level) || "MEDIUM".equalsIgnoreCase(item.risk_level)) {
                        danger++;
                    }
                    riskSum += item.risk_score;
                }

                int avgRisk = total > 0 ? Math.round((float) riskSum / total) : 0;

                ((TextView) findViewById(R.id.tvTotalCount)).setText(String.valueOf(total));
                ((TextView) findViewById(R.id.tvDangerCount)).setText(String.valueOf(danger));
                ((TextView) findViewById(R.id.tvAvgRisk)).setText(avgRisk + "%");
                ((TextView) findViewById(R.id.tvSttCount)).setText(String.valueOf(sttCount));

                updateRecentHistory(items);
            }

            @Override
            public void onFailure(Call<List<AnalysisResponse>> call, Throwable t) {
                Log.e(TAG, "fetchHistoryAndUpdate failed", t);
                if (attempt < MAX_RETRY) {
                    new Handler(Looper.getMainLooper()).postDelayed(
                        () -> fetchHistoryAndUpdate(attempt + 1),
                        RETRY_DELAY_MS
                    );
                }
            }
        });
    }

    private void updateRecentHistory(List<AnalysisResponse> items) {
        TextView tvHistory = findViewById(R.id.tvRecentHistory);
        if (items == null || items.isEmpty()) {
            tvHistory.setText("분석 기록이 없습니다");
            return;
        }

        StringBuilder sb = new StringBuilder();
        int limit = Math.min(10, items.size());
        for (int i = 0; i < limit; i++) {
            AnalysisResponse item = items.get(i);
            if (item == null) {
                continue;
            }
            String prefix = "[기타]";
            if (item.type != null) {
                if ("stt".equalsIgnoreCase(item.type)) {
                    prefix = "[STT]";
                } else if ("message".equalsIgnoreCase(item.type)) {
                    prefix = "[SMS]";
                }
            }

            String text = item.text == null ? "" : item.text;
            String summary = prefix + " " + item.risk_level + " " + item.risk_score + "% - "
                + text.substring(0, Math.min(30, text.length())) + "...";
            sb.append("• ").append(summary).append("\n");
        }

        tvHistory.setText(sb.toString().trim());
    }
}