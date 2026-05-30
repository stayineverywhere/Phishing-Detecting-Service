package com.example.voiceguard;

import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import com.example.voiceguard.api.AnalysisApi;
import com.example.voiceguard.api.AnalysisResponse;
import com.example.voiceguard.api.ApiClient;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class StatisticsActivity extends AppCompatActivity {

    private static final String TAG = "StatisticsActivity";
    private static final int MAX_RETRY = 2;
    private static final long RETRY_DELAY_MS = 1200;

    private ListView lvRecentHistory;
    private TextView tvEmptyHistory;
    private List<AnalysisResponse> historyList = new ArrayList<>();
    private List<String> historySummaries = new ArrayList<>();
    private ArrayAdapter<String> historyAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statistics);
        
        lvRecentHistory = findViewById(R.id.lvRecentHistory);
        tvEmptyHistory = findViewById(R.id.tvEmptyHistory);
        
        historyAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, historySummaries);
        lvRecentHistory.setAdapter(historyAdapter);
        
        lvRecentHistory.setOnItemClickListener((parent, view, position, id) -> {
            if (position < historyList.size()) {
                showDetailDialog(historyList.get(position));
            }
        });

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
        if (items == null || items.isEmpty()) {
            lvRecentHistory.setVisibility(View.GONE);
            tvEmptyHistory.setVisibility(View.VISIBLE);
            return;
        }

        lvRecentHistory.setVisibility(View.VISIBLE);
        tvEmptyHistory.setVisibility(View.GONE);

        historyList.clear();
        historySummaries.clear();
        
        int limit = Math.min(10, items.size());
        for (int i = 0; i < limit; i++) {
            AnalysisResponse item = items.get(i);
            if (item == null) {
                continue;
            }
            
            historyList.add(item);
            
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
            historySummaries.add(summary);
        }

        historyAdapter.notifyDataSetChanged();
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

        String title = "[기타 분석]";
        if (item.type != null) {
            if ("stt".equalsIgnoreCase(item.type)) {
                title = getString(R.string.menu_stt_title);
            } else if ("message".equalsIgnoreCase(item.type)) {
                title = getString(R.string.menu_sms_title);
            }
        }
        tvTitle.setText(title);
        tvRisk.setText(item.risk_level + " (" + item.risk_score + "%)");

        // Color for risk
        int color;
        if (item.risk_score <= 29)      color = Color.parseColor("#34A853");
        else if (item.risk_score <= 59) color = Color.parseColor("#FBBC04");
        else if (item.risk_score <= 79) color = Color.parseColor("#F06292");
        else                               color = Color.parseColor("#EA4335");
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