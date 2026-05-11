package com.example.voiceguard;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import java.util.*;

public class StatisticsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_statistics);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        int sttTotal = 0, sttDanger = 0, sttRiskSum = 0;
        List<String> allHistory = new ArrayList<>();

        SharedPreferences sttPrefs = getSharedPreferences("stt_history", MODE_PRIVATE);
        String sttSaved = sttPrefs.getString("history", "");
        if (!sttSaved.isEmpty()) {
            String[] items = sttSaved.split("\\|\\|");
            for (String item : items) {
                if (!item.isEmpty()) {
                    sttTotal++;
                    allHistory.add("[STT] " + item);
                    if (item.contains("위험") || item.contains("의심")) sttDanger++;
                    try {
                        String[] parts = item.split(" ");
                        for (String p : parts) {
                            if (p.endsWith("%")) {
                                sttRiskSum += Integer.parseInt(p.replace("%", "").trim());
                                break;
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        int smsTotal = 0, smsDanger = 0, smsRiskSum = 0;
        SharedPreferences smsPrefs = getSharedPreferences("sms_history", MODE_PRIVATE);
        String smsSaved = smsPrefs.getString("history", "");
        if (!smsSaved.isEmpty()) {
            String[] items = smsSaved.split("\\|\\|");
            for (String item : items) {
                if (!item.isEmpty()) {
                    smsTotal++;
                    allHistory.add("[SMS] " + item);
                    if (item.contains("위험") || item.contains("의심")) smsDanger++;
                    try {
                        String[] parts = item.split(" ");
                        for (String p : parts) {
                            if (p.endsWith("%")) {
                                smsRiskSum += Integer.parseInt(p.replace("%", "").trim());
                                break;
                            }
                        }
                    } catch (Exception ignored) {}
                }
            }
        }

        int total = sttTotal + smsTotal;
        int danger = sttDanger + smsDanger;
        int avgRisk = total > 0 ? (sttRiskSum + smsRiskSum) / total : 0;

        ((TextView) findViewById(R.id.tvTotalCount)).setText(String.valueOf(total));
        ((TextView) findViewById(R.id.tvDangerCount)).setText(String.valueOf(danger));
        ((TextView) findViewById(R.id.tvAvgRisk)).setText(avgRisk + "%");
        ((TextView) findViewById(R.id.tvSttCount)).setText(String.valueOf(sttTotal));

        TextView tvHistory = findViewById(R.id.tvRecentHistory);
        if (allHistory.isEmpty()) {
            tvHistory.setText("분석 기록이 없습니다");
        } else {
            StringBuilder sb = new StringBuilder();
            int limit = Math.min(10, allHistory.size());
            for (int i = 0; i < limit; i++) {
                sb.append("• ").append(allHistory.get(i)).append("\n");
            }
            tvHistory.setText(sb.toString().trim());
        }
    }
}