import json
from datetime import datetime
from sqlalchemy.orm import Session

from backend.app.models import AnalysisHistory
from backend.app.schemas import AnalysisResponse
from ml.predictor import predict_phishing


def db_to_response(item: AnalysisHistory) -> AnalysisResponse:
    return AnalysisResponse(
        id=item.id,
        type=item.type,
        text=item.text,
        is_phishing=item.is_phishing,
        label=item.label,
        risk_score=item.risk_score,
        risk_level=item.risk_level,
        detected_keywords=json.loads(item.detected_keywords or "[]"),
        tags=json.loads(item.tags or "[]"),
        guide=item.guide,
        detail=json.loads(item.detail or "{}"),
        created_at=item.created_at
    )


def analyze_and_save(db: Session, text: str, analysis_type: str) -> AnalysisResponse:
    ml_result = predict_phishing(text)

    history = AnalysisHistory(
        type=analysis_type,
        text=text,

        is_phishing=ml_result["is_phishing"],
        label=ml_result["label"],
        risk_score=ml_result["risk_score"],
        risk_level=ml_result["risk_level"],

        detected_keywords=json.dumps(
            ml_result["detected_keywords"],
            ensure_ascii=False
        ),
        tags=json.dumps(
            ml_result["tags"],
            ensure_ascii=False
        ),
        guide=ml_result["guide"],
        detail=json.dumps(
            ml_result["detail"],
            ensure_ascii=False
        ),

        created_at=datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    )

    db.add(history)
    db.commit()
    db.refresh(history)

    return db_to_response(history)


def get_all_history(db: Session):
    items = (
        db.query(AnalysisHistory)
        .order_by(AnalysisHistory.id.desc())
        .all()
    )

    return [db_to_response(item) for item in items]


def get_history_by_id(db: Session, history_id: int):
    item = (
        db.query(AnalysisHistory)
        .filter(AnalysisHistory.id == history_id)
        .first()
    )

    if item is None:
        return None

    return db_to_response(item)


def delete_history_by_id(db: Session, history_id: int) -> bool:
    item = (
        db.query(AnalysisHistory)
        .filter(AnalysisHistory.id == history_id)
        .first()
    )

    if item is None:
        return False

    db.delete(item)
    db.commit()

    return True


def get_statistics(db: Session):
    items = db.query(AnalysisHistory).all()

    total_count = len(items)

    risk_detected_count = len([
        item for item in items
        if item.risk_level in ["MEDIUM", "HIGH"]
    ])

    stt_analysis_count = len([
        item for item in items
        if item.type == "stt"
    ])

    message_analysis_count = len([
        item for item in items
        if item.type == "message"
    ])

    if total_count == 0:
        average_risk_score = 0
    else:
        average_risk_score = sum(item.risk_score for item in items) / total_count

    recent_items = (
        db.query(AnalysisHistory)
        .order_by(AnalysisHistory.id.desc())
        .limit(5)
        .all()
    )

    return {
        "total_count": total_count,
        "risk_detected_count": risk_detected_count,
        "average_risk_score": round(average_risk_score, 2),
        "stt_analysis_count": stt_analysis_count,
        "message_analysis_count": message_analysis_count,
        "recent_history": [db_to_response(item) for item in recent_items]
    }