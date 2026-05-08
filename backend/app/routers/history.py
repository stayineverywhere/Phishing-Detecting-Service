from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session
from typing import List

from backend.app.database import get_db
from backend.app.schemas import AnalysisResponse, DeleteResponse
from backend.app.services.analysis_service import (
    get_all_history,
    get_history_by_id,
    delete_history_by_id
)


router = APIRouter(prefix="/history", tags=["History"])


@router.get("", response_model=List[AnalysisResponse])
def get_history(db: Session = Depends(get_db)):
    return get_all_history(db)


@router.get("/{history_id}", response_model=AnalysisResponse)
def get_history_detail(
    history_id: int,
    db: Session = Depends(get_db)
):
    result = get_history_by_id(db, history_id)

    if result is None:
        raise HTTPException(
            status_code=404,
            detail="분석 기록을 찾을 수 없습니다."
        )

    return result


@router.delete("/{history_id}", response_model=DeleteResponse)
def delete_history(
    history_id: int,
    db: Session = Depends(get_db)
):
    success = delete_history_by_id(db, history_id)

    if not success:
        raise HTTPException(
            status_code=404,
            detail="분석 기록을 찾을 수 없습니다."
        )

    return {
        "message": "분석 기록이 삭제되었습니다.",
        "deleted_id": history_id
    }