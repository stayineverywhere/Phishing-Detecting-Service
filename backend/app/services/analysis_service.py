import json
from datetime import datetime
from sqlalchemy.orm import Session

from collections import Counter, defaultdict

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
// 검색 기능 추가
def search_history(
    db: Session,
    risk_level: str = None,
    analysis_type: str = None,
    keyword: str = None
):
    query = db.query(AnalysisHistory)

    if risk_level:
        query = query.filter(AnalysisHistory.risk_level == risk_level)

    if analysis_type:
        query = query.filter(AnalysisHistory.type == analysis_type)

    if keyword:
        query = query.filter(AnalysisHistory.text.contains(keyword))

    items = query.order_by(AnalysisHistory.id.desc()).all()

    return [db_to_response(item) for item in items]


def get_recent_history(db: Session, page: int = 1, size: int = 10):
    offset = (page - 1) * size

    items = (
        db.query(AnalysisHistory)
        .order_by(AnalysisHistory.id.desc())
        .offset(offset)
        .limit(size)
        .all()
    )

    total = db.query(AnalysisHistory).count()

    return {
        "page": page,
        "size": size,
        "total": total,
        "items": [db_to_response(item) for item in items]
    }


def delete_all_history(db: Session):
    deleted_count = db.query(AnalysisHistory).delete()
    db.commit()

    return {
        "message": "전체 분석 기록이 삭제되었습니다.",
        "deleted_count": deleted_count
    }


def get_history_sorted_by_risk(db: Session):
    items = (
        db.query(AnalysisHistory)
        .order_by(AnalysisHistory.risk_score.desc())
        .all()
    )

    return [db_to_response(item) for item in items]


def get_daily_statistics(db: Session):
    items = db.query(AnalysisHistory).all()

    daily_count = defaultdict(int)

    for item in items:
        date = item.created_at[:10]
        daily_count[date] += 1

    return [
        {
            "date": date,
            "count": count
        }
        for date, count in sorted(daily_count.items())
    ]


def get_risk_level_statistics(db: Session):
    items = db.query(AnalysisHistory).all()

    result = {
        "HIGH": 0,
        "MEDIUM": 0,
        "LOW": 0
    }

    total = len(items)

    for item in items:
        if item.risk_level in result:
            result[item.risk_level] += 1

    return {
        "total": total,
        "counts": result,
        "ratios": {
            level: round((count / total) * 100, 2) if total > 0 else 0
            for level, count in result.items()
        }
    }


def get_top_keywords(db: Session, limit: int = 10):
    items = db.query(AnalysisHistory).all()

    counter = Counter()

    for item in items:
        keywords = json.loads(item.detected_keywords or "[]")
        counter.update(keywords)

    return [
        {
            "keyword": keyword,
            "count": count
        }
        for keyword, count in counter.most_common(limit)
    ]    