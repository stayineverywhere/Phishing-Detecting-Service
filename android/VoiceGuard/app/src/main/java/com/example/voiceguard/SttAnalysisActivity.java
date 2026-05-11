package com.example.voiceguard;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.*;
import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;
import java.util.*;

public class SttAnalysisActivity extends AppCompatActivity {

    private EditText etSttInput;
    private CardView cardResult;
    private TextView tvRiskPercent, tvRiskLevel, tvKeywords, tvTags, tvGuide;
    private ProgressBar progressRisk;
    private ListView lvHistory;
    private List<String> historyList = new ArrayList<>();
    private ArrayAdapter<String> historyAdapter;
    private SharedPreferences prefs;

    private static final String PREF_NAME = "stt_history";
    private static final String KEY_HISTORY = "history";
    private static final String SEPARATOR = "||";

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

        prefs = getSharedPreferences(PREF_NAME, MODE_PRIVATE);

        etSttInput    = findViewById(R.id.etSttInput);
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