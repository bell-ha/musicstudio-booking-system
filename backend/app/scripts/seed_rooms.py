# backend/app/scripts/seed_rooms.py

from sqlalchemy.exc import IntegrityError
from app.database       import SessionLocal
from app.models.user    import User       # 메타데이터 등록용
from app.models.booking import Booking    # 메타데이터 등록용
from app.models.room    import Room

# 최신 raw_names 리스트
raw_names = [
    "124-2","129","125","126","331",
    "127-2","127-3","127-4","127-5","128",
    "222-2","222-3","222-4","222-5","222-6","222-7","222-9","222-10",
    "222-11","222-12","222-13","222-14",
    "221-2","221-3","221-4",
    "130","131","223","225","226","227","228","229","230",
    "325","326","327","329","330",
    "419-01","419-02","419-03","419-04","419-05","419-06",
    "420-01","420-02","420-03","420-04","420-05","420-06",
]

def format_room_name(raw: str) -> int:
    """
    - 하이픈(-) 있으면 prefix+suffix(zfill(2))
      e.g. "124-2" → "12402" → 12402
    - 없으면 suffix "00" 추가
      e.g. "225" → "22500" → 22500
    """
    if "-" in raw:
        prefix, suffix = raw.split("-")
        suffix = suffix.zfill(2)
        return int(f"{prefix}{suffix}")
    return int(f"{raw}00")

def seed_rooms():
    db = SessionLocal()
    try:
        for raw in raw_names:
            num = format_room_name(raw)
            room = Room(
                room_name = str(num),
                state     = True,      # 초기값: 빈방
                equipment = None,
            )
            db.add(room)
        db.commit()
        print("✅ Room seed completed")
    except IntegrityError as e:
        db.rollback()
        print("⚠️ Some rooms may already exist or duplicate keys:", e)
    finally:
        db.close()

if __name__ == "__main__":
    seed_rooms()
