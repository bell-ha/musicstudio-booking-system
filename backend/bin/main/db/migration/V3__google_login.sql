-- 구글 로그인 (ADR 0013). 소셜 전용 계정은 비밀번호가 없고, 검증되지 않은 이메일은 저장하지 않는다.
ALTER TABLE user_account ALTER COLUMN password_hash DROP NOT NULL;
ALTER TABLE user_account ALTER COLUMN email DROP NOT NULL;
ALTER TABLE user_account ADD COLUMN google_subject text UNIQUE;
