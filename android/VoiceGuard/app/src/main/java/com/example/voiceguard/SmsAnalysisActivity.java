package com.example.voiceguard;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import java.util.*;

public class SmsAnalysisActivity extends AppCompatActivity {

    private EditText etSmsInput;
    private CardView cardResult;
    private TextView tvRiskPercent, tvRiskLevel, tvKeywords, tvTags, tvGuide;
    private ProgressBar progressRisk;
    private ListView lvHistory;
    private List<String> historyList = new ArrayList<>();
    private ArrayAdapter<String> historyAdapter;
    private SharedPreferences prefs;

    private static final String PREF_NAME = "sms_history";
    private static final String KEY_HISTORY = "history";
    private static final String SEPARATOR = "||";

    private static final String SAMPLE_SMS =
            "[국민은행] 고객님 계좌가 이상거래로 탐지되었습니다. " +
                    "즉시 아래 링크에서 본인인증을 완료해주세요. http://kb-check.xyz " +
                    "24시간 내 미완료 시 계좌가 동결됩니다.";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_sms_analysis);

        prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);

        etSmsInput    = findViewById(R.id.etSmsInput);
        cardResult    = findViewById(R.id.cardResult);
        tvRiskPercent = findViewById(R.id.tvRiskPercent);
        tvRiskLevel   = findViewById(R.id.tvRiskLevel);
        tvKeywords    = findViewById(R.id.tvKeywords);
        tvTags        = findViewById(R.id.tvTags);
        tvGuide       = findViewById(R.id.tvGuide);
        progressRisk  = findViewById(R.id.progressRisk);
        lvHistory     = findViewById(R.id.lvHistory);

        loadHistory();

        historyAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, historyList);
        lvHistory.setAdapter(historyAdapter);

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
        PhishingAnalyzer.AnalysisResult result = PhishingAnalyzer.analyze(text);

        cardResult.setVisibility(View.VISIBLE);
        tvRiskPercent.setText(result.riskPercent + "%");
        tvRiskLevel.setText(result.riskLevel);
        progressRisk.setProgress(result.riskPercent);

        int color;
        if (result.riskPercent <= 29)      color = Color.parseColor("#4CAF50");
        else if (result.riskPercent <= 59) color = Color.parseColor("#FF9800");
        else if (result.riskPercent <= 79) color = Color.parseColor("#FF5722");
        else                               color = Color.parseColor("#F44336");

        tvRiskPercent.setTextColor(color);
        tvRiskLevel.setTextColor(color);

        tvKeywords.setText(result.detectedKeywords.isEmpty() ? "감지된 위험 키워드 없음"
                : String.join("  ", result.detectedKeywords));
        tvTags.setText(result.scamTags.isEmpty() ? "해당 없음"
                : String.join("  ", result.scamTags));

        if (!result.actionGuide.isEmpty()) {
            tvGuide.setVisibility(View.VISIBLE);
            tvGuide.setText(result.actionGuide);
        } else {
            tvGuide.setVisibility(View.GONE);
        }

        String summary = result.riskLevel + " " + result.riskPercent + "% - "
                + text.substring(0, Math.min(30, text.length())) + "...";
        historyList.add(0, summary);
        historyAdapter.notifyDataSetChanged();
        saveHistory();
    }

    private void loadHistory() {
        String saved = prefs.getString(KEY_HISTORY, "");
        if (!saved.isEmpty()) {
            String[] items = saved.split("\\|\\|");
            for (String item : items) {
                if (!item.isEmpty()) historyList.add(item);
            }
        }
    }

    private void saveHistory() {
        StringBuilder sb = new StringBuilder();
        for (String item : historyList) {
            sb.append(item).append(SEPARATOR);
        }
        prefs.edit().putString(KEY_HISTORY, sb.toString()).apply();
    }
}