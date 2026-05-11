package com.example.voiceguard;

import android.os.Bundle;
import androidx.appcompat.app.AppCompatActivity;

public class PhishingInfoActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_phishing_info);
        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }
}