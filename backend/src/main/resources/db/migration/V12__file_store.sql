-- 파일은 DB가 아니라 S3 호환 저장소에 둔다 (ADR 0017이 0015를 대체). DB에는 키만.
-- 이미 DB에 들어 있는 로고는 그대로 읽힌다(bytes). 새로 올리면 storage_key로 간다. 둘 중 하나만 있다.
ALTER TABLE site_logo ADD COLUMN storage_key text;
ALTER TABLE site_logo ALTER COLUMN bytes DROP NOT NULL;
ALTER TABLE site_logo ADD CONSTRAINT ck_site_logo_one_place CHECK ((bytes IS NULL) <> (storage_key IS NULL));
