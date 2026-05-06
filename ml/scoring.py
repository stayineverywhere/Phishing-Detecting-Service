import math

from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.metrics.pairwise import cosine_similarity


keywords_list = [
    "검찰", "경찰", "수사", "계좌", "동결", "인증번호",
    "앱 설치", "송금", "입금", "대포통장",
    "[Web]", "엄마", "아빠", "돈 보내",
    "결제되었습니다", "확인", "배송지 누락",
    "파일 설치", "영상 보내"
]


keywords_dict = {
    "검찰": 1,
    "경찰": 1,
    "인증번호": 1,
    "송금": 2,
    "돈 보내": 2,
    "파일 설치": 2,
    "앱 설치": 2
}


phishing_patterns = [
    "계좌가 범죄에 연루되었습니다",
    "귀하의 계좌가 불법 거래에 사용되었습니다",
    "검찰청 수사관입니다",
    "경찰서 사이버수사팀입니다",
    "금융감독원에서 연락드렸습니다",
    "명의도용 사건으로 조사가 필요합니다",
    "대포통장 개설 혐의가 확인되었습니다",
    "본인 확인을 위해 개인정보가 필요합니다",
    "인증번호를 알려주세요",
    "문자로 받은 인증번호를 불러주세요",
    "OTP 번호를 확인해야 합니다",
    "계좌 비밀번호 확인이 필요합니다",
    "보안카드 번호를 입력해주세요",
    "안전계좌로 돈을 이체해야 합니다",
    "피해 방지를 위해 임시 계좌로 송금하세요",
    "지금 바로 돈을 보내야 합니다",
    "환급 처리를 위해 계좌 입금이 필요합니다",
    "수수료를 먼저 입금해야 처리됩니다",
    "엄마 나 휴대폰 고장났어",
    "아빠 나 지금 통화가 안 돼",
    "급하게 돈이 필요해",
    "친구 계좌로 대신 보내줘",
    "나중에 바로 갚을게 지금 먼저 보내줘",
    "아래 링크를 눌러 확인해주세요",
    "보안 앱 설치가 필요합니다",
    "원격제어 앱을 설치해주세요",
    "파일을 다운로드해서 실행해주세요",
    "문자에 있는 주소로 접속해주세요",
    "해외 결제가 승인되었습니다",
    "배송지 정보가 누락되었습니다",
    "환불 신청을 위해 계좌 정보를 입력해주세요",
    "지금 처리하지 않으면 계좌가 정지됩니다",
    "불응 시 법적 조치가 진행됩니다",
    "오늘 안에 처리하지 않으면 불이익이 발생합니다",
    "본인 확인이 지연되면 수사가 진행됩니다"
]


vectorizer = TfidfVectorizer()
pattern_vec = vectorizer.fit_transform(phishing_patterns)


def keyword_score(text: str) -> float:
    score = 0

    for word in keywords_list:
        if word in text:
            score += 1

    for word, weight in keywords_dict.items():
        if word in text:
            score += weight

    max_score = len(keywords_list) + sum(keywords_dict.values())
    return score / max_score


def pattern_score(text: str) -> float:
    text_vec = vectorizer.transform([text])
    similarity = cosine_similarity(text_vec, pattern_vec)
    return float(similarity.max())


def urgency_score(text: str) -> float:
    urgency_words = ["지금", "즉시", "바로", "당장", "급해"]
    count = sum(1 for word in urgency_words if word in text)

    score = count * 15
    return min(score, 100) / 100


def context_score(text: str) -> float:
    score = 0

    if ("검찰" in text or "경찰" in text) and ("송금" in text or "돈 보내" in text or "계좌" in text):
        score += 40

    if "인증번호" in text:
        score += 30

    if "설치" in text:
        score += 30

    return min(score, 100) / 100


def sigmoid(x: float) -> float:
    return 1 / (1 + math.exp(-x))


def combine_scores(text: str, ai_score: float):
    k = keyword_score(text)
    p = pattern_score(text)
    u = urgency_score(text)
    c = context_score(text)

    z = -3 + 5 * k + 5 * p + 10 * ai_score + 4 * u + 5 * c
    prob = sigmoid(z)

    return prob, {
        "K": k,
        "P": p,
        "AI": ai_score,
        "U": u,
        "C": c,
        "z": z
    }


def classify(prob: float) -> str:
    if prob >= 0.75:
        return "피싱 위험"
    elif prob >= 0.45:
        return "의심"
    else:
        return "정상"


def risk_level(prob: float) -> str:
    if prob >= 0.75:
        return "HIGH"
    elif prob >= 0.45:
        return "MEDIUM"
    else:
        return "LOW"


def extract_keywords(text: str):
    return [word for word in keywords_list if word in text]


def make_tags(text: str):
    tags = []

    if any(word in text for word in ["검찰", "경찰", "수사", "금융감독원"]):
        tags.append("기관사칭")

    if any(word in text for word in ["송금", "입금", "돈 보내", "계좌"]):
        tags.append("금전요구")

    if any(word in text for word in ["인증번호", "OTP", "비밀번호"]):
        tags.append("인증정보요구")

    if any(word in text for word in ["앱 설치", "파일 설치", "링크", "주소"]):
        tags.append("설치/링크유도")

    if any(word in text for word in ["지금", "즉시", "바로", "당장", "급해"]):
        tags.append("긴급성유도")

    return tags


def make_guide(level: str):
    if level == "HIGH":
        return "보이스피싱 위험이 높습니다. 통화를 중단하고 송금, 인증번호 전달, 앱 설치를 절대 하지 마세요."
    elif level == "MEDIUM":
        return "의심스러운 내용이 포함되어 있습니다. 공식 기관, 가족, 은행 고객센터에 직접 확인하세요."
    else:
        return "위험도는 낮지만 개인정보, 인증번호, 송금 요구가 있다면 주의하세요."