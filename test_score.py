import os
import sys

# Add the project root to sys.path
sys.path.append(os.getcwd())

from ml.predictor import predict_phishing

text = "엄마~!! 저 집에 도착했어요~!! 안녕히 주무세요~!!"
result = predict_phishing(text)

print(f"Sentence: {result['text']}")
print(f"Risk Score: {result['risk_score']}%")
print(f"Risk Level: {result['risk_level']}")
print(f"Label: {result['label']}")
print(f"Detail: {result['detail']}")
print(f"Detected Keywords: {result['detected_keywords']}")
print(f"Tags: {result['tags']}")
