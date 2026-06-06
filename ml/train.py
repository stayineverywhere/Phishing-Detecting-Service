import os
import random
import pandas as pd
import torch

from sklearn.model_selection import train_test_split
from torch.utils.data import Dataset, DataLoader
from transformers import BertTokenizer, BertForSequenceClassification
from torch.optim import AdamW
from tqdm import tqdm


DATASET_PATH = "ml/dataset.csv"
MODEL_SAVE_PATH = "ml/model/kobert_phishing"


replace_dict = {
    "검찰": ["수사기관", "검찰청", "수사팀", "형사부", "사법기관"],
    "경찰": ["경찰청", "수사과", "사이버수사대", "수사관"],
    "수사": ["조사", "내사", "수사 진행", "조사 절차"],
    "담당": ["전담", "배정된", "책임", "관할"],

    "계좌": ["통장", "계정", "금융계좌", "거래계좌"],
    "송금": ["이체", "입금", "이체 처리", "자금 이동"],
    "입금": ["송금", "이체", "납부", "이체 완료"],
    "금액": ["금액대", "금액 정보", "거래 금액"],
    "결제": ["결제 처리", "승인", "거래 승인", "지불"],

    "확인": ["검토", "점검", "확인 요청", "검증"],
    "점검": ["확인", "검토", "조사", "확인 절차"],
    "처리": ["진행", "조치", "진행 중", "처리 진행"],
    "조치": ["처리", "대응", "조정", "조치 진행"],

    "연락": ["통보", "안내", "알림", "연락 요청"],
    "안내": ["공지", "통보", "고지", "알림"],
    "통보": ["안내", "고지", "연락", "전달"],

    "즉시": ["바로", "긴급히", "당장", "즉각"],
    "지금": ["현재", "즉시", "당장", "바로"],
    "긴급": ["중요", "비상", "긴급 상황", "즉시 대응"],
    "바로": ["즉시", "곧바로", "당장", "지금"],

    "피해": ["손해", "피해 발생", "금전 피해", "손실"],
    "환급": ["환불", "반환", "돌려받기", "환급 처리"],
    "보상": ["보상금", "배상", "보상 처리", "보상금 지급"],

    "인증": ["확인", "검증", "본인확인", "인증 절차"],
    "비밀번호": ["비번", "암호", "접속 코드", "보안 코드"],
    "로그인": ["접속", "인증 접속", "로그인 시도", "접속 요청"],

    "필수": ["반드시", "필수사항", "의무", "필수 조건"],
    "필요": ["요구됨", "필수", "요청됨", "필요 사항"],
    "주의": ["경고", "주의사항", "보안 경고", "알림"],
}


def augment_sentence(sentence, n=3):
    aug_list = []

    for _ in range(n):
        new_sentence = str(sentence)

        for k, v_list in replace_dict.items():
            if k in new_sentence and random.random() < 0.7:
                new_sentence = new_sentence.replace(k, random.choice(v_list))

        if new_sentence != sentence:
            aug_list.append(new_sentence)

    return aug_list


def load_and_augment_dataset():
    df = pd.read_csv(DATASET_PATH)

    if "구분" in df.columns:
        df = df.drop(columns=["구분"])

    if "출처" in df.columns:
        df = df.drop(columns=["출처"])

    augmented_data = []

    for _, row in df.iterrows():
        label = int(row["라벨"])
        sentence = str(row["문장"])

        base_row = row.to_dict()
        augmented_data.append(base_row)

        if label == 1:
            aug_sentences = augment_sentence(sentence, n=3)
            aug_sentences = augment_sentence(sentence, n=4)
        else :
            aug_sentences = augment_sentence(sentence, n=5)

        for s in aug_sentences:
            new_row = base_row.copy()
            new_row["문장"] = s
            augmented_data.append(new_row)

    df_aug = pd.DataFrame(augmented_data)

    df_aug = df_aug.drop_duplicates(subset=["문장"])

    texts = (
        df_aug["상황"].astype(str)
        + " "
        + df_aug["문장"].astype(str)
    ).tolist()

    labels = df_aug["라벨"].astype(int).tolist()

    return texts, labels


class ScamDataset(Dataset):
    def __init__(self, texts, labels, tokenizer):
        self.texts = texts
        self.labels = labels
        self.tokenizer = tokenizer

    def __len__(self):
        return len(self.texts)

    def __getitem__(self, idx):
        encoding = self.tokenizer(
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


def train():
    texts, labels = load_and_augment_dataset()

    train_texts, val_texts, train_labels, val_labels = train_test_split(
        texts,
        labels,
        test_size=0.1,
        random_state=42
    )

    tokenizer = BertTokenizer.from_pretrained("monologg/kobert")

    train_loader = DataLoader(
        ScamDataset(train_texts, train_labels, tokenizer),
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

    for epoch in range(10):
        model.train()
        total_loss = 0

        for batch in tqdm(train_loader):
            optimizer.zero_grad()

            input_ids = batch["input_ids"].to(device)
            attention_mask = batch["attention_mask"].to(device)
            labels_batch = batch["labels"].to(device)

            outputs = model(
                input_ids=input_ids,
                attention_mask=attention_mask,
                labels=labels_batch
            )

            loss = outputs.loss

            total_loss += loss.item()

            loss.backward()

            optimizer.step()

        print(f"Epoch {epoch + 1}, Loss: {total_loss}")

    os.makedirs(MODEL_SAVE_PATH, exist_ok=True)

    model.save_pretrained(MODEL_SAVE_PATH)
    tokenizer.save_pretrained(MODEL_SAVE_PATH)

    print("KoBERT 모델 저장 완료")


if __name__ == "__main__":
    train()