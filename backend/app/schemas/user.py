# backend/app/schemas/user.py

from typing import Optional, Literal
from pydantic import BaseModel, constr, ConfigDict

class UserCreate(BaseModel):
    login_id:   constr(min_length=2, max_length=50)  # 아이디
    password:   constr(min_length=8, max_length=72)  # 비밀번호 (bcrypt 입력 상한이 72바이트)
    username:   constr(min_length=1, max_length=100)  # 실명
    student_id: constr(min_length=7, max_length=9)   # 학번
    major:      constr(min_length=1, max_length=100)  # 학과
    phone:      Optional[constr(min_length=9, max_length=20)] = None  # 전화번호

class UserRead(BaseModel):
    # ⚠️ password 필드를 두지 마세요.
    #    예전에는 `password: Optional[str]` 이 있어서 회원가입 응답, 관리자
    #    사용자 목록(GET /users), 승인 응답에 비밀번호가 그대로 실려 나갔습니다.
    #    UserRead는 응답 전용 모델이라 여기에 필드를 추가하면 곧바로 노출됩니다.
    user_id:    int
    login_id:   str
    username:   str
    student_id: str
    major:      str
    phone:      Optional[str]
    role:       Literal["pending", "user", "admin"]

    model_config = ConfigDict(from_attributes=True)


class TempPasswordRead(BaseModel):
    """
    관리자의 비밀번호 초기화 응답.
    temp_password는 이 응답에서 딱 한 번만 나가고 DB에는 해시만 남습니다.
    다시 조회할 방법이 없으므로 관리자가 학생에게 바로 전달해야 합니다.
    """
    user_id:       int
    login_id:      str
    username:      str
    temp_password: str
