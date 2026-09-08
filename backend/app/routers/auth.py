# backend/app/routers/auth.py

import os
from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, Depends, HTTPException, status
from fastapi.security import OAuth2PasswordBearer, OAuth2PasswordRequestForm
from sqlalchemy.orm import Session
from jose import jwt

# ⚠️ 이 import가 .env를 읽어들입니다(app/database.py 의 load_dotenv).
#    아래 JWT_SECRET 조회보다 반드시 위에 있어야 합니다.
from app.database import get_db
from app.models.user import User
from app.security import verify_password, get_password_hash, is_hashed

# 서명키는 환경변수에서만 받습니다.
# 예전에는 os.getenv("JWT_SECRET", "devsecret") 로 폴백이 있었는데,
# 그러면 .env를 빠뜨린 채 배포해도 서버가 멀쩡히 뜨고 누구나 아는 키로
# 토큰을 위조할 수 있습니다. 설정이 없으면 기동 자체를 막습니다.
SECRET_KEY = os.getenv("JWT_SECRET")
if not SECRET_KEY:
    raise RuntimeError(
        "JWT_SECRET 환경변수가 설정되지 않았습니다. backend/.env 에 넣어 주세요.\n"
        '    python -c "import secrets; print(secrets.token_urlsafe(48))"'
    )

ALGORITHM = "HS256"
ACCESS_TOKEN_EXPIRE_HOURS = 12   # 학교 실습실 예약이라 하루 안쪽이면 충분합니다

oauth2_scheme = OAuth2PasswordBearer(tokenUrl="auth/login")

router = APIRouter(prefix="/auth", tags=["auth"])


@router.post("/login")
def login(
    form: OAuth2PasswordRequestForm = Depends(),
    db: Session = Depends(get_db)
):
    # 1) 사용자 조회 및 비밀번호 검증
    #    저장값이 bcrypt 해시면 해시로, 아직 평문이면 평문으로 확인합니다.
    user: User = db.query(User).filter(User.login_id == form.username).first()
    if not user or not verify_password(form.password, user.password):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="로그인 정보가 올바르지 않습니다."
        )

    # 2) 평문으로 통과한 계정은 이 자리에서 bcrypt로 바꿔 저장합니다.
    #    학생은 아무것도 안 해도 로그인할 때마다 계정이 하나씩 전환됩니다.
    #    저장에 실패해도 로그인 자체는 막지 않습니다(다음 로그인에 다시 시도).
    if not is_hashed(user.password):
        try:
            user.password = get_password_hash(form.password)
            db.commit()
        except Exception:
            db.rollback()

    # 3) 승인 대기 상태면 접근 차단
    if user.role == "pending":
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="아직 승인 대기 중인 계정입니다. 관리자 승인을 기다려 주세요."
        )

    # 4) JWT 발급
    #    exp가 없어서 한 번 받은 토큰이 영원히 유효했습니다. 만료를 넣습니다.
    #    (기존에 발급된 토큰은 exp가 없어 계속 유효합니다 — 강제 로그아웃이
    #     필요하면 JWT_SECRET을 교체하면 전부 무효화됩니다)
    now = datetime.now(timezone.utc)
    payload = {
        "sub":        user.login_id,
        "user_id":    user.user_id,
        "username":   user.username,
        "student_id": user.student_id,
        "major":      user.major,
        "phone":      user.phone,
        "role":       user.role,
        "iat":        now,
        "exp":        now + timedelta(hours=ACCESS_TOKEN_EXPIRE_HOURS),
    }
    token = jwt.encode(payload, SECRET_KEY, algorithm=ALGORITHM)

    return {
        "access_token": token,
        "token_type":   "bearer",
        "role":         user.role,
    }


def get_current_user(
    token: str = Depends(oauth2_scheme),
    db: Session = Depends(get_db)
) -> User:
    try:
        data = jwt.decode(token, SECRET_KEY, algorithms=[ALGORITHM])
        login_id = data.get("sub")
    except jwt.ExpiredSignatureError:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="로그인이 만료되었습니다. 다시 로그인해 주세요."
        )
    except jwt.JWTError:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="Invalid token"
        )

    user = db.query(User).filter(User.login_id == login_id).first()
    if not user:
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="User not found"
        )
    return user


def get_current_admin(
    current_user: User = Depends(get_current_user)
) -> User:
    """
    현재 토큰의 사용자가 admin인지 체크.
    아니면 403 에러 발생.
    """
    if current_user.role != "admin":
        raise HTTPException(
            status_code=status.HTTP_403_FORBIDDEN,
            detail="관리자 권한이 필요합니다."
        )
    return current_user
