from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from backend.app.database import get_db
from backend.app.schemas import AnalysisRequest, AnalysisResponse
from backend.app.services.analysis_service import analyze_and_save


router = APIRouter(prefix="/analysis", tags=["Analysis"])


@router.post("/stt", response_model=AnalysisResponse)
def analyze_stt(
    request: AnalysisRequest,
    db: Session = Depends(get_db)
):
    try:
        return analyze_and_save(
            db=db,
            text=request.text,
            analysis_type="stt"
        )
    except Exception as e:
        raise HTTPException(
            status_code=500,
            detail=f"STT 분석 중 오류가 발생했습니다: {str(e)}"
        )


@router.post("/message", response_model=AnalysisResponse)
def analyze_message(
    request: AnalysisRequest,
    db: Session = Depends(get_db)
):
    try:
        return analyze_and_save(
            db=db,
            text=request.text,
            analysis_type="message"
        )
    except Exception as e:
        raise HTTPException(
            status_code=500,
            detail=f"문자/이메일 분석 중 오류가 발생했습니다: {str(e)}"
        )