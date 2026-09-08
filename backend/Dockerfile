# 1. Python 경량 이미지
FROM python:3.11-slim

# 2. Python 환경 설정
ENV PYTHONDONTWRITEBYTECODE=1
ENV PYTHONUNBUFFERED=1

# 3. 작업 디렉토리
WORKDIR /app

# 4. 시스템 패키지 (PostgreSQL 클라이언트 라이브러리)
RUN apt-get update && apt-get install -y \
    build-essential \
    libpq-dev \
    && rm -rf /var/lib/apt/lists/*

# 5. 의존성 설치
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# 6. 소스 복사
COPY app /app/app

# 7. 포트
EXPOSE 8000

# 8. 서버 실행
CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000"]
