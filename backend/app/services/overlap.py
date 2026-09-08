"""
시간대 겹침 판정 — booking.py(방충돌·사용자충돌) / admin_booking.py / room.py
슬롯 조회에서 각자 복제해 쓰던 부등식을 한 곳으로 모았다.

날짜 조건(같은 날 vs 기간 겹침)은 호출부마다 다르므로 여기서 강제하지
않는다. 여기서 뽑는 건 "그 날짜 비교를 통과한 두 구간이 실제로
시간상 겹치는가"라는, 세 라우터가 완전히 동일하게 쓰던 조건뿐이다.
"""
from sqlalchemy import and_

from app.models.booking import Booking


def time_overlap_clause(start_time, end_time):
    """겹침 필요충분조건: 기존 예약.start < 신규.end AND 기존 예약.end > 신규.start.

    양쪽 부등식이 모두 strict(<, >)라서 끝점이 맞닿는 경우(예: 09:00-11:00과
    11:00-12:00)는 겹침으로 보지 않는다 — 기존 동작 그대로다.
    """
    return and_(Booking.start_time < end_time, Booking.end_time > start_time)
