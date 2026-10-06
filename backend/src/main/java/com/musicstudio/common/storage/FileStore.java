package com.musicstudio.common.storage;

import java.util.Optional;

/**
 * 사용자가 올린 파일(지금은 기관 로고)을 두는 곳 (ADR 0017). DB에는 키만 둔다.
 * 구현은 S3 API 하나다: 로컬·테스트는 MinIO, 운영은 S3.
 */
public interface FileStore {

    void put(String key, byte[] bytes, String contentType);

    Optional<StoredFile> get(String key);

    /** 없어도 오류가 아니다 */
    void delete(String key);

    record StoredFile(byte[] bytes, String contentType) {
    }
}
