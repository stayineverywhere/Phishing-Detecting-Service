import torch

from transformers import BertTokenizer, BertForSequenceClassification
from ml.scoring import (
    combine_scores,
    classify,
    risk_level,
    extract_keywords,
    make_tags,
    make_guide
)


MODEL_PATH = "ml/model/kobert_phishing"


device = torch.device("cuda" if torch.cuda.is_available() else "cpu")

tokenizer = BertTokenizer.from_pretrained(MODEL_PATH)
model = BertForSequenceClassification.from_pretrained(MODEL_PATH)
model.to(device)
model.eval()


def ai_score(text: str) -> float:
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


def predict_phishing(text: str):
    ai = ai_score(text)

    prob, detail = combine_scores(text, ai)

    level = risk_level(prob)
    label = classify(prob)

    return {
        "is_phishing": level == "HIGH",
        "label": label,
        "risk_score": round(prob * 100),
        "risk_level": level,
        "detected_keywords": extract_keywords(text),
        "tags": make_tags(text),
        "guide": make_guide(level),
        "detail": {
            "keyword_score": round(detail["K"], 4),
            "pattern_score": round(detail["P"], 4),
            "ai_score": round(detail["AI"], 4),
            "urgency_score": round(detail["U"], 4),
            "context_score": round(detail["C"], 4),
            "z": round(detail["z"], 4)
        }
    }