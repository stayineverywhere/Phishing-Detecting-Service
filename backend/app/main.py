from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from backend.app.database import Base, engine
from backend.app.routers import analysis, history, stats
from backend.app.schemas import HealthResponse, ModelInfoResponse


Base.metadata.create_all(bind=engine)


app = FastAPI(
    title="Voice Phishing Detection API",
    description="KoBERT 기반 보이스피싱 분석 API",
    version="1.0.0"
)


app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)


app.include_router(analysis.router)
app.include_router(history.router)
app.include_router(stats.router)


@app.get("/", response_model=HealthResponse)
def root():
    return {
        "status": "success",
        "message": "Voice Phishing Detection API is running"
    }


@app.get("/health", response_model=HealthResponse)
def health_check():
    return {
        "status": "success",
        "message": "server is healthy"
    }


@app.get("/model/info", response_model=ModelInfoResponse)
def model_info():
    return {
        "model_name": "KoBERT Voice Phishing Detector",
        "model_path": "ml/model/kobert_phishing",
        "description": "KoBERT AI 점수와 키워드, 패턴, 긴급성, 맥락 점수를 결합해 보이스피싱 위험도를 계산합니다."
    }