package com.example.voiceguard.api;

import java.util.List;

public class StatsResponse {
    public int total_count;
    public int risk_detected_count;
    public double average_risk_score;
    public int stt_analysis_count;
    public int message_analysis_count;
    public List<AnalysisResponse> recent_history;
}

