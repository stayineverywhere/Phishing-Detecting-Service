from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from backend.app.database import get_db
from backend.app.schemas import StatsResponse
from backend.app.services.analysis_service import (
    get_statistics,
    get_daily_statistics,
    get_risk_level_statistics,
    get_top_keywords
)


router = APIRouter(prefix="/stats", tags=["Stats"])


@router.get("", response_model=StatsResponse)
def get_stats(db: Session = Depends(get_db)):
    return get_statistics(db)


@router.get("/daily")
def get_daily_stats(db: Session = Depends(get_db)):
    return get_daily_statistics(db)


@router.get("/risk-level")
def get_risk_level_stats(db: Session = Depends(get_db)):
    return get_risk_level_statistics(db)


@router.get("/keywords/top")
def get_top_keyword_stats(
    limit: int = 10,
    db: Session = Depends(get_db)
):
    return get_top_keywords(db, limit)