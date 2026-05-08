from sqlalchemy import Column, Integer, String, Text, Boolean
from backend.app.database import Base


class AnalysisHistory(Base):
    __tablename__ = "analysis_history"

    id = Column(Integer, primary_key=True, index=True)

    type = Column(String, nullable=False)
    text = Column(Text, nullable=False)

    is_phishing = Column(Boolean, nullable=False)
    label = Column(String, nullable=False)
    risk_score = Column(Integer, nullable=False)
    risk_level = Column(String, nullable=False)

    detected_keywords = Column(Text, nullable=True)
    tags = Column(Text, nullable=True)
    guide = Column(Text, nullable=False)
    detail = Column(Text, nullable=True)

    created_at = Column(String, nullable=False)