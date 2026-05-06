import os
import pandas as pd
import torch

from sklearn.model_selection import train_test_split
from torch.utils.data import Dataset, DataLoader
from transformers import BertTokenizer, BertForSequenceClassification
from torch.optim import AdamW
from tqdm import tqdm


DATASET_PATH = "ml/dataset.csv"
MODEL_SAVE_PATH = "ml/model/kobert_phishing"


df = pd.read_csv(DATASET_PATH)

# 필요 없는 컬럼 제거
if "구분" in df.columns:
    df = df.drop(columns=["구분"])

if "출처" in df.columns:
    df = df.drop(columns=["출처"])

texts = (df["상황"].astype(str) + " " + df["문장"].astype(str)).tolist()
labels = df["라벨"].astype(int).tolist()


train_texts, val_texts, train_labels, val_labels = train_test_split(
    texts,
    labels,
    test_size=0.1,
    random_state=42,
    stratify=labels
)


tokenizer = BertTokenizer.from_pretrained("monologg/kobert")


class ScamDataset(Dataset):
    def __init__(self, texts, labels):
        self.texts = texts
        self.labels = labels

    def __len__(self):
        return len(self.texts)

    def __getitem__(self, idx):
        encoding = tokenizer(
            self.texts[idx],
            padding="max_length",
            truncation=True,
            max_length=128,
            return_tensors="pt"
        )

        return {
            "input_ids": encoding["input_ids"].squeeze(),
            "attention_mask": encoding["attention_mask"].squeeze(),
            "labels": torch.tensor(self.labels[idx], dtype=torch.long)
        }


train_loader = DataLoader(
    ScamDataset(train_texts, train_labels),
    batch_size=4,
    shuffle=True
)

model = BertForSequenceClassification.from_pretrained(
    "monologg/kobert",
    num_labels=2
)

device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
model.to(device)

optimizer = AdamW(model.parameters(), lr=2e-5)


EPOCHS = 3

for epoch in range(EPOCHS):
    model.train()
    total_loss = 0

    for batch in tqdm(train_loader):
        optimizer.zero_grad()

        input_ids = batch["input_ids"].to(device)
        attention_mask = batch["attention_mask"].to(device)
        labels = batch["labels"].to(device)

        outputs = model(
            input_ids=input_ids,
            attention_mask=attention_mask,
            labels=labels
        )

        loss = outputs.loss
        total_loss += loss.item()

        loss.backward()
        optimizer.step()

    print(f"Epoch {epoch + 1}, Loss: {total_loss:.4f}")


os.makedirs(MODEL_SAVE_PATH, exist_ok=True)

model.save_pretrained(MODEL_SAVE_PATH)
tokenizer.save_pretrained(MODEL_SAVE_PATH)

print("KoBERT 모델 저장 완료")