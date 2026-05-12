package com.example.voiceguard.api;

import java.util.List;
import java.util.Map;

public class AnalysisResponse {
    public int id;
    public String type;
    public String text;
    public boolean is_phishing;
    public String label;
    public int risk_score;
    public String risk_level;
    public List<String> detected_keywords;
    public List<String> tags;
    public String guide;
    public Map<String, Object> detail;
    public String created_at;
}

