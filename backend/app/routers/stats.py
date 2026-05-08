from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session

from backend.app.database import get_db
from backend.app.schemas import StatsResponse
from backend.app.services.analysis_service import get_statistics


router = APIRouter(prefix="/stats", tags=["Stats"])


@router.get("", response_model=StatsResponse)
def get_stats(db: Session = Depends(get_db)):
    return get_statistics(db)