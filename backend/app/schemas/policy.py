# backend/app/schemas/policy.py
from datetime import time
from typing import Literal, Optional
from pydantic import BaseModel, ConfigDict, Field


class PolicyRead(BaseModel):
    same_day_mode:       Literal["NO_OVERLAP", "SEQUENTIAL", "ONE_PER_DAY"]
    max_minutes:         int
    cancel_deadline_min: int
    open_time:           time
    close_time:          time
    slot_minutes:        int
    week_open_weekday:   int
    week_open_time:      time

    model_config = ConfigDict(from_attributes=True)


class PolicyUpdate(BaseModel):
    """모두 선택 입력. 보낸 항목만 반영한다."""
    same_day_mode:       Optional[Literal["NO_OVERLAP", "SEQUENTIAL", "ONE_PER_DAY"]] = None
    max_minutes:         Optional[int]  = Field(None, ge=30,  le=720)
    cancel_deadline_min: Optional[int]  = Field(None, ge=0,   le=1440)
    open_time:           Optional[time] = None
    close_time:          Optional[time] = None
    slot_minutes:        Optional[int]  = Field(None, ge=10,  le=120)
    week_open_weekday:   Optional[int]  = Field(None, ge=0,   le=6)
    week_open_time:      Optional[time] = None
