"""
비밀번호 해싱 / 검증

저장은 bcrypt 해시로만 합니다. 다만 이 시스템은 이미 운영 중이고 DB에는
평문 비밀번호가 남아 있으므로, 한 번에 갈아엎지 않고 점진적으로 전환합니다.

  · 회원가입 / 비밀번호 초기화 → 처음부터 bcrypt 해시로 저장
  · 로그인 → 저장값이 bcrypt 해시면 해시 검증,
             아직 평문이면 평문으로 검증한 뒤 즉시 bcrypt로 재해싱해 저장
             (실제 전환은 routers/auth.py 의 login() 에서 일어납니다)

학생은 아무것도 하지 않아도 다음 로그인 때 자기 계정 하나가 전환됩니다.
전환 진행 상황은 아래 쿼리로 확인할 수 있습니다.

    SELECT count(*) FILTER (WHERE password LIKE '$2%') AS 전환됨,
           count(*) FILTER (WHERE password NOT LIKE '$2%') AS 평문남음
    FROM users;

⚠️ 이 모듈은 bcrypt 패키지를 필요로 합니다. 서버를 재시작하기 전에
   반드시 설치되어 있어야 합니다.
"""

import secrets
import string

try:
    import bcrypt
except ImportError as exc:  # 설치 안 된 상태로 기동하면 원인을 바로 알 수 있게
    raise RuntimeError(
        "bcrypt 패키지가 필요합니다. 서버를 재시작하기 전에 설치하세요:\n"
        "    backend/.venv_pg/bin/pip install 'bcrypt>=4.1'"
    ) from exc


# bcrypt 해시는 항상 이 접두어로 시작합니다. 평문과 구분하는 기준입니다.
_BCRYPT_PREFIXES = ("$2a$", "$2b$", "$2x$", "$2y$")

# bcrypt는 72바이트를 넘는 입력을 처리하지 못합니다(라이브러리가 예외를 던짐).
# 한글 비밀번호는 한 글자가 3바이트라 24자만 넘어도 걸리므로 잘라서 넘깁니다.
_MAX_BYTES = 72


def _to_bytes(password: str) -> bytes:
    return password.encode("utf-8")[:_MAX_BYTES]


def is_hashed(stored_password: str) -> bool:
    """DB에 저장된 값이 이미 bcrypt 해시인지."""
    return bool(stored_password) and stored_password.startswith(_BCRYPT_PREFIXES)


def get_password_hash(password: str) -> str:
    """새 비밀번호를 bcrypt 해시로 만듭니다. 결과는 60자입니다."""
    return bcrypt.hashpw(_to_bytes(password), bcrypt.gensalt()).decode("utf-8")


def verify_password(plain_password: str, stored_password: str) -> bool:
    """
    입력한 비밀번호가 저장된 값과 맞는지 확인합니다.
    저장값이 아직 평문이어도 통과시킵니다(전환 기간용).
    """
    if not plain_password or not stored_password:
        return False

    if is_hashed(stored_password):
        try:
            return bcrypt.checkpw(_to_bytes(plain_password), stored_password.encode("utf-8"))
        except ValueError:
            # 해시 모양이지만 실제로는 깨진 값 — 인증 실패로 처리
            return False

    # 아직 전환되지 않은 평문 계정. 타이밍 차이로 정보가 새지 않게 상수 시간 비교.
    return secrets.compare_digest(plain_password, stored_password)


# 관리자가 발급하는 임시 비밀번호에 씁니다.
# 헷갈리는 글자(0/O, 1/l/I)를 빼서 구두로 불러줄 수 있게 했습니다.
_TEMP_ALPHABET = "".join(
    c for c in (string.ascii_letters + string.digits) if c not in "0O1lI"
)


def generate_temp_password(length: int = 10) -> str:
    """임시 비밀번호를 만듭니다. 이 값은 응답으로 한 번만 돌려주고 저장하지 않습니다."""
    return "".join(secrets.choice(_TEMP_ALPHABET) for _ in range(length))
