# backend/app/database.py

import os
from dotenv import load_dotenv, find_dotenv
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker, declarative_base

# ─── 1) .env 로드 ────────────────────────────────────────────
# override=False가 중요하다. 이미 환경변수로 주어진 값을 .env가 덮어쓰면,
# 테스트나 스크래치 DB를 가리키도록 환경변수를 심어도 이 모듈을 import하는
# 순간 .env의 운영 접속 정보로 되돌아간다. 컨테이너에서도 env_file보다
# 바깥에서 준 값이 우선이어야 한다.
dotenv_path = find_dotenv(usecwd=True)
if dotenv_path:
    load_dotenv(dotenv_path, override=False)

# ─── 2) 연결 문자열 ──────────────────────────────────────────
# Neon(PostgreSQL). 자격증명은 .env 또는 컨테이너 환경변수로만 주입한다.
DATABASE_URL = os.getenv("DATABASE_URL")
if not DATABASE_URL:
    raise RuntimeError(
        "DATABASE_URL 환경변수가 설정되지 않았습니다. "
        "backend/.env.example을 참고해 .env를 만드세요."
    )

# ─── 3) 엔진·세션·베이스 ─────────────────────────────────────
# Neon 무료 티어는 유휴 시 컴퓨트가 절전되므로 pre_ping으로 죽은 커넥션을 걸러낸다.
engine = create_engine(
    DATABASE_URL,
    pool_pre_ping=True,
    pool_recycle=300,
)
SessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=engine)
Base = declarative_base()

# ─── 4) FastAPI 의존성 주입용 ────────────────────────────────
def get_db():
    db = SessionLocal()
    try:
        yield db
    finally:
        db.close()
