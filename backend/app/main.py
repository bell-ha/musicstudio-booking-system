# backend/app/main.py

from pathlib import Path

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from fastapi.staticfiles import StaticFiles

from app.database import Base, engine
from app.routers.user import router as user_router
from app.routers.health import router as health_router
from app.routers.auth import router as auth_router
from app.routers.room import router as room_router
from app.routers.booking import router as booking_router
from app.routers.admin_booking import router as admin_booking_router
from app.routers.notice import router as notice_router
from app.routers.cell import router as cell_router
from app.routers.policy import router as policy_router


# 1) 테이블 자동생성 (개발/테스트 용)
Base.metadata.create_all(bind=engine)

# 2) FastAPI 앱 생성
app = FastAPI(title="NM Study Room Booking")

# 3) CORS 설정 (개발 중에만 "*"로 허용)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# 4) API 라우터 등록
#    사용자 및 헬스 체크, 인증
app.include_router(user_router)                     # /users
app.include_router(health_router)                   # /health
app.include_router(auth_router)                     # /auth

#    연습실/예약/관리자 블록을 /api/ 아래로 통일
app.include_router(room_router,    prefix="/api", tags=["rooms"])            # /api/rooms
app.include_router(booking_router, prefix="/api", tags=["bookings"])         # /api/bookings
app.include_router(admin_booking_router, prefix="/api", tags=["admin_bookings"])  # /api/admin/bookings
app.include_router(notice_router)
app.include_router(cell_router, prefix="/api", tags=["cells"])
app.include_router(policy_router, prefix="/api", tags=["policy"])



# 5) 프론트엔드 정적 파일 서빙
frontend_dir = Path(__file__).parent.parent.parent / "frontend"
app.mount(
    "/",
    StaticFiles(directory=frontend_dir, html=True),
    name="frontend"
)

# 6) uvicorn 직접 실행 시
if __name__ == "__main__":
    import uvicorn
    uvicorn.run("app.main:app", host="127.0.0.1", port=8000, reload=True)