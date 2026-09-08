from sqlalchemy import Column, Integer, UniqueConstraint
from app.database import Base

class Cell(Base):
    __tablename__ = "cells"

    id = Column(Integer, primary_key=True, index=True, autoincrement=True)
    floor = Column(Integer, index=True, nullable=False)
    x = Column(Integer, nullable=False)
    y = Column(Integer, nullable=False)

    __table_args__ = (
        # 같은 층 같은 좌표에 칸이 두 개 있을 수 없다.
        # 제약이 없어 관리자가 배치를 저장할 때마다 중복 행이 쌓일 수 있었다.
        UniqueConstraint("floor", "x", "y", name="uq_cells_floor_x_y"),
    )
