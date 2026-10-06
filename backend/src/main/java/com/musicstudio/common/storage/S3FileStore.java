package com.musicstudio.common.storage;

import java.net.URI;
import java.util.Optional;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3ClientBuilder;
import software.amazon.awssdk.services.s3.model.NoSuchBucketException;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

/** S3 API로 파일을 둔다. MinIO는 경로 방식(path-style) 주소가 필요하다 */
@Component
@EnableConfigurationProperties(StorageProperties.class)
class S3FileStore implements FileStore {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(S3FileStore.class);

    private final S3Client s3;
    private final String bucket;

    S3FileStore(StorageProperties props) {
        S3ClientBuilder builder = S3Client.builder().region(Region.of(props.region()));
        if (props.endpoint() != null && !props.endpoint().isBlank()) {
            builder.endpointOverride(URI.create(props.endpoint())).forcePathStyle(true);
        }
        builder.credentialsProvider(props.accessKey() == null || props.accessKey().isBlank()
                ? DefaultCredentialsProvider.builder().build()
                : StaticCredentialsProvider.create(AwsBasicCredentials.create(props.accessKey(), props.secretKey())));
        this.s3 = builder.build();
        this.bucket = props.bucket();
        if (props.createBucket()) {
            try {
                s3.headBucket(b -> b.bucket(bucket));
            } catch (NoSuchBucketException e) {
                s3.createBucket(b -> b.bucket(bucket));
            }
        }
    }

    @Override
    public void put(String key, byte[] bytes, String contentType) {
        s3.putObject(b -> b.bucket(bucket).key(key).contentType(contentType), RequestBody.fromBytes(bytes));
    }

    @Override
    public Optional<StoredFile> get(String key) {
        try {
            var response = s3.getObjectAsBytes(b -> b.bucket(bucket).key(key));
            return Optional.of(new StoredFile(response.asByteArray(), response.response().contentType()));
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }

    /** 트랜잭션이 끝난 뒤 정리용으로 불린다. 실패해도 응답을 깨지 않고 남은 파일만 생긴다(로그로 찾는다) */
    @Override
    public void delete(String key) {
        if (key == null) {
            return;
        }
        try {
            s3.deleteObject(b -> b.bucket(bucket).key(key));
        } catch (RuntimeException e) {
            log.warn("저장소 파일을 지우지 못함: {}", key, e);
        }
    }
}
