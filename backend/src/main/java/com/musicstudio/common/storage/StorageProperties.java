package com.musicstudio.common.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * endpoint가 비어 있으면 AWS S3(지역 기본 엔드포인트)를 쓰고, 키가 비어 있으면 기본 자격 증명(EC2 역할)을 쓴다.
 * createBucket: 로컬 MinIO처럼 버킷을 앱이 만들어도 되는 곳에서만 켠다(운영 버킷은 Terraform이 만든다).
 */
@ConfigurationProperties("app.storage")
public record StorageProperties(String endpoint, String region, String bucket, String accessKey, String secretKey,
                                boolean createBucket) {
}
