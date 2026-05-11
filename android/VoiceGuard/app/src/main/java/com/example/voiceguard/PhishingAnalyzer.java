package com.example.voiceguard;

import java.util.*;

public class PhishingAnalyzer {

    private static final Map<String, Integer> KEYWORD_SCORES = new HashMap<>();
    private static final Map<String, Integer> PATTERN_SCORES = new HashMap<>();
    private static final List<String> URGENCY_WORDS = new ArrayList<>();

    static {
        String[] agency20 = {"검찰", "금융감독원", "경찰", "수사관", "지검", "수사팀",
                "사이버수사대", "중앙지검", "서울지검", "금융기관", "금감원"};
        for (String w : agency20) KEYWORD_SCORES.put(w, 20);

        String[] high25 = {"OTP", "비밀번호", "계좌번호", "대포통장", "동결처리",
                "계좌동결", "지급정지", "압수물품", "명의도용", "개인정보유출"};
        for (String w : high25) KEYWORD_SCORES.put(w, 25);

        String[] mid15 = {"통장", "계좌", "송금", "입금", "이체", "압수",
                "수사", "조사", "피해자", "녹취", "영장"};
        for (String w : mid15) KEYWORD_SCORES.put(w, 15);

        PATTERN_SCORES.put("기관사칭", 20);
        PATTERN_SCORES.put("송금요구", 30);
        PATTERN_SCORES.put("개인정보요구", 25);
        PATTERN_SCORES.put("비밀유지", 20);
        PATTERN_SCORES.put("가족사칭", 25);
        PATTERN_SCORES.put("대출사기", 20);

        String[] urgency = {"지금 바로", "즉시", "당장", "빨리", "서둘러",
                "급하게", "긴급", "지금 당장", "지금 즉시"};
        URGENCY_WORDS.addAll(Arrays.asList(urgency));
    }

    public static class AnalysisResult {
        public int riskPercent;
        public String riskLevel;
        public List<String> detectedKeywords;
        public List<String> scamTags;
        public String actionGuide;
    }

    public static AnalysisResult analyze(String text) {
        AnalysisResult result = new AnalysisResult();
        result.detectedKeywords = new ArrayList<>();
        result.scamTags = new ArrayList<>();

        if (text == null || text.trim().isEmpty()) {
            result.riskPercent = 0;
            result.riskLevel = "✅ 안전";
            result.actionGuide = "";
            return result;
        }

        int K = 0;
        for (Map.Entry<String, Integer> entry : KEYWORD_SCORES.entrySet()) {
            if (text.contains(entry.getKey())) {
                K += entry.getValue();
                result.detectedKeywords.add(entry.getKey());
            }
        }

        int P = 0;
        if (text.contains("검찰") || text.contains("수사관") || text.contains("금융감독원")
                || text.contains("경찰") || text.contains("지검")) {
            P += PATTERN_SCORES.get("기관사칭");
            result.scamTags.add("#기관사칭");
        }
        if ((text.contains("송금") || text.contains("이체") || text.contains("입금"))
                && (text.contains("계좌") || text.contains("통장"))) {
            P += PATTERN_SCORES.get("송금요구");
            result.scamTags.add("#송금요구");
        }
        if (text.contains("계좌번호") || text.contains("비밀번호") || text.contains("OTP")
                || text.contains("보안카드")) {
            P += PATTERN_SCORES.get("개인정보요구");
            result.scamTags.add("#개인정보요구");
        }
        if (text.contains("아무에게도") || text.contains("비밀") || text.contains("제3자")
                || text.contains("말하면 안") || text.contains("말씀드리면 안")) {
            P += PATTERN_SCORES.get("비밀유지");
            result.scamTags.add("#비밀유지강요");
        }
        if ((text.contains("아빠") || text.contains("엄마") || text.contains("아들")
                || text.contains("딸") || text.contains("친구"))
                && (text.contains("사고") || text.contains("수술") || text.contains("돈"))) {
            P += PATTERN_SCORES.get("가족사칭");
            result.scamTags.add("#가족사칭");
        }
        if (text.contains("저금리") || text.contains("대환대출")
                || (text.contains("대출") && text.contains("수수료"))) {
            P += PATTERN_SCORES.get("대출사기");
            result.scamTags.add("#대출사기");
        }

        int U = 0;
        for (String uw : URGENCY_WORDS) {
            if (text.contains(uw)) {
                U += 15;
                if (!result.scamTags.contains("#긴박감조성"))
                    result.scamTags.add("#긴박감조성");
            }
        }

        int C = 0;
        if (result.scamTags.contains("#기관사칭") && result.scamTags.contains("#송금요구")) {
            C += 40;
            result.scamTags.add("#복합패턴감지");
        }
        if (result.scamTags.contains("#개인정보요구") && result.scamTags.contains("#비밀유지강요")) {
            C += 30;
        }

        double z = -3 + (0.04 * K) + (0.035 * P) + (0.03 * U) + (0.05 * C);
        double probability = 1.0 / (1.0 + Math.exp(-z));
        int riskPercent = (int) Math.round(probability * 100);
        riskPercent = Math.max(0, Math.min(100, riskPercent));
        result.riskPercent = riskPercent;

        if (riskPercent <= 29) {
            result.riskLevel = "✅ 안전";
            result.actionGuide = "";
        } else if (riskPercent <= 59) {
            result.riskLevel = "⚠️ 주의";
            result.actionGuide = "일부 위험 표현이 감지되었습니다. 추가 확인이 필요합니다.";
        } else if (riskPercent <= 79) {
            result.riskLevel = "🔶 의심";
            result.actionGuide = "복수의 피싱 패턴이 감지되었습니다. 즉시 응대를 중단하세요.";
        } else {
            result.riskLevel = "🚨 위험";
            result.actionGuide = "즉시 통화를 종료하고 112 또는 금감원(1332)에 신고하세요!";
        }

        return result;
    }
}