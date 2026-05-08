import torch

from transformers import (
    BertTokenizer,
    BertForSequenceClassification
)

from ml.scoring import (
    combine_scores,
    classify,
    risk_level,
    is_phishing_final,
    extract_keywords,
    make_tags,
    make_guide
)


MODEL_PATH = "ml/model/kobert_phishing"


device = torch.device(
    "cuda" if torch.cuda.is_available() else "cpu"
)


tokenizer = BertTokenizer.from_pretrained(MODEL_PATH)

model = BertForSequenceClassification.from_pretrained(MODEL_PATH)

model.to(device)

model.eval()


def ai_score(text):
    encoding = tokenizer(
        text,
        return_tensors="pt",
        truncation=True,
        padding=True,
        max_length=128
    )

    input_ids = encoding["input_ids"].to(device)

    attention_mask = encoding["attention_mask"].to(device)

    with torch.no_grad():
        outputs = model(
            input_ids=input_ids,
            attention_mask=attention_mask
        )

    probs = torch.softmax(outputs.logits, dim=1)

    return float(probs[0][1].item())


def predict_phishing(text):
    ai = ai_score(text)

    prob, detail = combine_scores(text, ai)

    is_phishing, prob = is_phishing_final(
        prob,
        detail["K"],
        detail["P"],
        detail["AI"],
        detail["U"],
        detail["C"]
    )

    label = classify(prob)

    level = risk_level(prob)

    return {
        "text": text,
        "is_phishing": is_phishing,
        "label": label,
        "risk_score": round(prob * 100),
        "risk_level": level,

        "detected_keywords": extract_keywords(text),
        "tags": make_tags(text),
        "guide": make_guide(level),

        "detail": {
            "K": round(detail["K"], 4),
            "P": round(detail["P"], 4),
            "AI": round(detail["AI"], 4),
            "U": round(detail["U"], 4),
            "C": round(detail["C"], 4),
            "z": round(detail["z"], 4)
        }
    }


if __name__ == "__main__":
    test_cases = [
        "배고파",
        "검찰입니다 계좌 확인 필요 송금 하세요",
        "[Web]계좌 사기 수사관 배정됐습니다",
        "엄마 나 친구집 왔어"
    ]

    for t in test_cases:
        result = predict_phishing(t)

        print("문장:", result["text"])
        print(f"키워드(K): {result['detail']['K']:.4f}")
        print(f"패턴(P): {result['detail']['P']:.4f}")
        print(f"AI:       {result['detail']['AI']:.4f}")
        print(f"긴급성(U): {result['detail']['U']:.4f}")
        print(f"맥락(C):   {result['detail']['C']:.4f}")
        print(f"z 값:     {result['detail']['z']:.4f}")
        print(f"최종 확률: {result['risk_score']}%")
        print(f"판정: {result['label']}")
        print("-" * 50)