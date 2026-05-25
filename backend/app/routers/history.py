
from typing import List, Optional

from fastapi import APIRouter, Depends, HTTPException, Query
from sqlalchemy.orm import Session



from backend.app.database import get_db
from backend.app.schemas import AnalysisResponse, DeleteResponse
from backend.app.services.analysis_service import (
    get_all_history,
    get_history_by_id,
    delete_history_by_id,
    search_history,
    get_recent_history,
    delete_all_history,
    get_history_sorted_by_risk
)




router = APIRouter(prefix="/history", tags=["History"])


@router.get("", response_model=List[AnalysisResponse])
def get_history(db: Session = Depends(get_db)):
    return get_all_history(db)



# 2. 기록 검색/필터링
@router.get("/search", response_model=List[AnalysisResponse])
def search_history_api(
    risk_level: Optional[str] = Query(None),
    analysis_type: Optional[str] = Query(None),
    keyword: Optional[str] = Query(None),
    db: Session = Depends(get_db)
):
    return search_history(
        db=db,
        risk_level=risk_level,
        analysis_type=analysis_type,
        keyword=keyword
    )

# 3. 최근 기록 pagination
@router.get("/recent")
def get_recent_history_api(
    page: int = Query(1, ge=1),
    size: int = Query(10, ge=1, le=100),
    db: Session = Depends(get_db)
):
    return get_recent_history(db, page, size)


# 4. 위험도 순 정렬
@router.get("/sort/risk", response_model=List[AnalysisResponse])
def get_history_sorted_by_risk_api(db: Session = Depends(get_db)):
    return get_history_sorted_by_risk(db)

# 5. 전체 기록 삭제
@router.delete("")
def delete_all_history_api(db: Session = Depends(get_db)):
    return delete_all_history(db)


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