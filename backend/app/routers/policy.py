# backend/app/routers/policy.py
"""예약 정책 조회·수정. 조회는 로그인 사용자, 수정은 관리자만."""
from fastapi import APIRouter, Depends, HTTPException
from sqlalchemy.orm import Session

from app.database import get_db
from app.models.policy import BookingPolicy, get_policy
from app.schemas.policy import PolicyRead, PolicyUpdate
from app.routers.auth import get_current_user

router = APIRouter(prefix="/policy", tags=["policy"])


@router.get("", response_model=PolicyRead, summary="예약 정책 조회")
def read_policy(db: Session = Depends(get_db), current_user=Depends(get_current_user)):
    return get_policy(db)


@router.patch("", response_model=PolicyRead, summary="예약 정책 수정 (관리자)")
def update_policy(
    body: PolicyUpdate,
    db: Session = Depends(get_db),
    current_user=Depends(get_current_user),
):
    if current_user.role != "admin":
        raise HTTPException(status_code=403, detail="관리자만 정책을 변경할 수 있습니다.")

    policy = get_policy(db)
    changes = body.model_dump(exclude_unset=True)

    open_time  = changes.get("open_time",  policy.open_time)
    close_time = changes.get("close_time", policy.close_time)
    if close_time <= open_time:
        raise HTTPException(status_code=400, detail="종료 시각이 시작 시각보다 빨라요.")

    for field, value in changes.items():
        setattr(policy, field, value)

    db.commit()
    db.refresh(policy)
    return policy
