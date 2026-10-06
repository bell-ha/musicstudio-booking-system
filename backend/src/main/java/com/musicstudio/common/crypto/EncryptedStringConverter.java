package com.musicstudio.common.crypto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * 연락처 같은 개인정보를 DB에 AES-256-GCM으로 저장한다 (NFR-04).
 * 저장 형식: [12바이트 IV][암호문 + 16바이트 태그]. IV는 행마다 새로 만든다.
 * 키(APP_CRYPTO_KEY, base64 32바이트)가 없으면 서버가 뜨지 않는다. 키 교체와 KMS는 운영 전에 정한다.
 */
@Component
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, byte[]> {

    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final SecretKeySpec key;

    EncryptedStringConverter(@Value("${app.crypto.key:}") String base64Key) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64Key);
        } catch (IllegalArgumentException e) {
            bytes = new byte[0];
        }
        if (bytes.length != 32) {
            throw new IllegalStateException("app.crypto.key(APP_CRYPTO_KEY)는 base64로 인코딩한 32바이트여야 합니다");
        }
        this.key = new SecretKeySpec(bytes, "AES");
    }

    @Override
    public byte[] convertToDatabaseColumn(String plain) {
        if (plain == null) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] encrypted = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            return ByteBuffer.allocate(IV_BYTES + encrypted.length).put(iv).put(encrypted).array();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("암호화 실패", e);
        }
    }

    @Override
    public String convertToEntityAttribute(byte[] stored) {
        if (stored == null) {
            return null;
        }
        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, stored, 0, IV_BYTES));
            return new String(cipher.doFinal(stored, IV_BYTES, stored.length - IV_BYTES), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("복호화 실패", e);
        }
    }

    /** 화면과 로그용: 01012345678 → 010-****-5678 */
    public static String mask(String phone) {
        if (phone == null || phone.length() < 8) {
            return phone == null ? null : "****";
        }
        return phone.substring(0, 3) + "-****-" + phone.substring(phone.length() - 4);
    }
}
