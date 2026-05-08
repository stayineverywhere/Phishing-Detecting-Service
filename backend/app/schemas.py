from pydantic import BaseModel, Field
from typing import List, Dict, Any


class AnalysisRequest(BaseModel):
    text: str = Field(..., min_length=1)


class AnalysisResponse(BaseModel):
    id: int
    type: str
    text: str

    is_phishing: bool
    label: str
    risk_score: int
    risk_level: str

    detected_keywords: List[str]
    tags: List[str]
    guide: str
    detail: Dict[str, Any]

    created_at: str

    class Config:
        from_attributes = True


class DeleteResponse(BaseModel):
    message: str
    deleted_id: int


class StatsResponse(BaseModel):
    total_count: int
    risk_detected_count: int
    average_risk_score: float
    stt_analysis_count: int
    message_analysis_count: int
    recent_history: List[AnalysisResponse]


class HealthResponse(BaseModel):
    status: str
    message: str


class ModelInfoResponse(BaseModel):
    model_name: str
    model_path: str
    description: str