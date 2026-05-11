package com.example.voiceguard;

import android.content.Intent;
import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        findViewById(R.id.cardStt).setOnClickListener(v ->
                startActivity(new Intent(this, SttAnalysisActivity.class)));

        findViewById(R.id.cardSms).setOnClickListener(v ->
                startActivity(new Intent(this, SmsAnalysisActivity.class)));

        findViewById(R.id.cardPhishing).setOnClickListener(v ->
                startActivity(new Intent(this, PhishingInfoActivity.class)));

        findViewById(R.id.cardStats).setOnClickListener(v ->
                startActivity(new Intent(this, StatisticsActivity.class)));
    }
}