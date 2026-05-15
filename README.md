# 2026_hnu
## STT 전사문 기반 보이스피싱&스캠 탐지 및 경고 시스템 

# 실행 방법

## 1. 프로젝트 클론

```bash
git clone <레포주소>
cd 2026_HNU
```

---

## 2. 가상환경 생성 및 실행

### Mac / Linux

```bash
python3 -m venv .venv
source .venv/bin/activate
```

### Windows

```powershell
python -m venv .venv
.venv\Scripts\activate
```

---

## 3. 라이브러리 설치

```bash
pip install -r requirements.txt
```

---

## 4. KoBERT 모델 학습

```bash
python ml/train.py
```

학습 완료 시 모델이 자동 저장됩니다.
학습 완료 시 아래 경로에 KoBERT 모델 및 토크나이저 파일이 자동 생성됩니다.

ml/model/kobert_phishing/

생성되는 주요 파일:

config.json
model.safetensors
tokenizer_config.json
vocab.txt
special_tokens_map.json

※ 모델 파일 용량이 크기 때문에 .gitignore 처리되어 있으며, 직접 학습 후 생성해서 사용해주세요.

---

## 5. FastAPI 서버 실행

```bash
uvicorn backend.app.main:app --reload
```

---

## 6. Swagger API 테스트

브라우저에서 접속:

```text
http://127.0.0.1:8000/docs
```

---

## SQLite DB 확인

서버 실행 후 프로젝트 루트에 `analysis.db` 파일이 생성됩니다.

VS Code SQLite Viewer 확장 등을 사용하면 DB 내용을 확인할 수 있습니다.

## 데이터 출처 

```
AI 학습용 보이스 피싱 데이터 출처:
공공데이터 포털(data.go.kr) 이용
경찰청 강원특별자치도경찰청, 경찰청 강원특별자치도경찰청_보이스피싱 공익광고_20240801, 공공누리 제1유형, 2024
경찰청 인천광역시경찰청, 경찰청 인천광역시경찰청_보이스피싱 예방 동영상_20240101, 2024
경찰청 전라북도경찰청, 경찰청 전라북도경찰청_보이스피싱 피해 예방 영상_20240822, 2024
경찰청 경기도북부경찰청, 경찰청 경기도북부경찰청_보이스피싱 근절 홍보 영상_20230630, 2023
경찰청 부산광역시경찰청, 경찰청 부산광역시경찰청_보이스피싱 예방 홍보 영상_20230825, 2023
```

