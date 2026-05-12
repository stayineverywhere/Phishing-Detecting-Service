# code.md

## 1) 리포지터리 구성 개요

- `README.md`: 실행 순서(의존성 설치, 모델 학습, 서버 실행)와 데이터 출처 안내.
- `requirements.txt`: Python 백엔드/ML 의존성.
- `analysis.db`: SQLite DB(서버 실행 시 생성되는 분석 기록 저장소).
- `data/`: 학습/분석에 쓰이는 원천 데이터(`cases.csv`).
- `ml/`: KoBERT 학습/추론 및 점수 결합 로직.
- `backend/`: FastAPI 서버, 라우터, DB 모델/스키마, 서비스 로직.
- `android/VoiceGuard/`: Android 프런트엔드 앱.
- `src/`: 실험/노트북 추출 코드(`phishing_detector.py`).

---

## 2) 백엔드 구성 및 동작

### 2.1 엔트리포인트와 앱 설정
- `backend/app/main.py`
  - `FastAPI` 앱 생성, CORS 허용.
  - DB 테이블 생성(`Base.metadata.create_all`).
  - 라우터 등록: `analysis`, `history`, `stats`.
  - 기본 헬스 체크(`/`, `/health`) 및 모델 정보(`/model/info`).

### 2.2 DB 연결/모델
- `backend/app/database.py`
  - SQLite `analysis.db` 연결.
  - `SessionLocal` 세션 생성, `get_db()` 디펜던시 제공.
- `backend/app/models.py`
  - `AnalysisHistory` 테이블 정의.
  - 분석 타입, 입력 텍스트, 위험도, 키워드/태그, 가이드, 상세 점수, 생성일 저장.

### 2.3 요청/응답 스키마
- `backend/app/schemas.py`
  - `AnalysisRequest`: 입력 텍스트.
  - `AnalysisResponse`: 분석 결과 구조(위험도, 키워드, 태그, 가이드, 상세 점수 등).
  - `StatsResponse`, `DeleteResponse`, `HealthResponse`, `ModelInfoResponse` 정의.

### 2.4 라우터
- `backend/app/routers/analysis.py`
  - `POST /analysis/stt`: STT 전사문 분석.
  - `POST /analysis/message`: 문자/이메일 분석.
  - 내부에서 `analysis_service.analyze_and_save()` 호출.
- `backend/app/routers/history.py`
  - `GET /history`: 전체 기록 조회.
  - `GET /history/{id}`: 단건 조회.
  - `DELETE /history/{id}`: 삭제.
- `backend/app/routers/stats.py`
  - `GET /stats`: 통계 조회.

### 2.5 서비스 로직
- `backend/app/services/analysis_service.py`
  - `predict_phishing()`로 ML 추론 실행.
  - 결과를 `AnalysisHistory`로 저장 후 응답 변환.
  - 통계 계산 및 최근 기록 조회 제공.

---

## 3) ML 구성 및 동작

### 3.1 추론
- `ml/predictor.py`
  - `MODEL_PATH = ml/model/kobert_phishing`의 KoBERT 모델/토크나이저 로드.
  - `ai_score()`로 모델 확률 산출.
  - `ml/scoring.py`의 규칙 기반 점수(K, P, U, C)와 결합.
  - 최종 위험도/라벨/가이드/태그/상세 점수 반환.

### 3.2 점수 결합 및 규칙
- `ml/scoring.py`
  - 키워드 점수(K), 패턴 유사도(P), 긴급성(U), 맥락(C) 계산.
  - AI 점수(AI)와 결합해 최종 확률 도출.
  - 위험 레벨(HIGH/MEDIUM/LOW), 태그, 가이드 생성.

### 3.3 학습
- `ml/train.py`
  - `ml/dataset.csv` 로드 및 증강.
  - `monologg/kobert`로 분류 모델 학습.
  - 학습 결과를 `ml/model/kobert_phishing/`에 저장.

### 3.4 참고/실험 코드
- `src/phishing_detector.py`
  - Colab 노트북에서 추출된 실험/개발용 코드.
  - 서버와 직접 연결되는 실행 경로는 `ml/`의 코드가 담당.

---

## 4) Android 프런트엔드 구성 및 동작

### 4.1 앱 구조
- `android/VoiceGuard/app/src/main/java/com/example/voiceguard/MainActivity.java`
  - 카드 클릭으로 각 기능 화면 이동.
  - 이동 대상: `SttAnalysisActivity`, `SmsAnalysisActivity`, `PhishingInfoActivity`, `StatisticsActivity`.

### 4.2 네트워크 클라이언트
- `android/VoiceGuard/app/src/main/java/com/example/voiceguard/api/ApiClient.java`
  - Retrofit 설정, Base URL: `http://10.0.2.2:8000/` (에뮬레이터에서 로컬 서버 접근).
- `android/VoiceGuard/app/src/main/java/com/example/voiceguard/api/AnalysisApi.java`
  - `POST /analysis/stt`, `POST /analysis/message`, `GET /history`, `GET /stats` 정의.
- DTO
  - `AnalysisRequest.java`, `AnalysisResponse.java`, `StatsResponse.java`.

### 4.3 STT 분석 화면
- `SttAnalysisActivity.java`
  - 텍스트 입력 → `POST /analysis/stt` 호출.
  - 위험도/키워드/태그/가이드 표시.
  - `GET /history`로 최근 STT 기록 표시.

### 4.4 SMS 분석 화면
- `SmsAnalysisActivity.java`
  - 텍스트 입력 → `POST /analysis/message` 호출.
  - 결과 표시 방식은 STT와 동일.
  - `GET /history`에서 `type=message`만 필터.

### 4.5 통계 화면
- `StatisticsActivity.java`
  - `GET /history`로 전체 기록을 받아 통계 계산.
  - 총 건수/위험 건수/평균 위험도/STT 건수 표시.
  - 최근 기록 요약 표시.

### 4.6 앱 매니페스트
- `android/VoiceGuard/app/src/main/AndroidManifest.xml`
  - 인터넷 권한, HTTP 통신 허용, 액티비티 등록.

---

## 5) 백엔드-프런트엔드 연관 및 상호작용

### 5.1 호출 흐름
1. Android 앱에서 텍스트 입력.
2. Retrofit이 `POST /analysis/stt` 또는 `POST /analysis/message` 요청.
3. FastAPI가 `predict_phishing()`로 ML 추론 수행.
4. 결과를 SQLite에 저장 후 JSON 응답 반환.
5. 앱은 결과를 UI에 표시하고, 필요 시 `GET /history` 또는 `GET /stats`로 기록/통계를 갱신.

### 5.2 데이터 구조 매핑
- 백엔드 `AnalysisResponse` (Pydantic) ↔ Android `AnalysisResponse` (DTO)
  - 필드 이름이 동일하게 매핑되어 Retrofit/Gson이 자동 변환.
- 백엔드 `StatsResponse` ↔ Android `StatsResponse`
  - 통계 필드명이 동일.

### 5.3 연관 관계 요약
- 프런트엔드: 사용자 입력 수집, 결과 시각화, 기록/통계 조회.
- 백엔드: 요청 수신, ML 추론, DB 저장, 응답 제공.
- ML: 모델 추론 및 규칙 기반 점수 결합으로 최종 위험도 산출.
- DB: 분석 결과 기록 보관 및 통계 계산의 데이터 소스.

