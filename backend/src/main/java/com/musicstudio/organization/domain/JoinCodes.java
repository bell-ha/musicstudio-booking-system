package com.musicstudio.organization.domain;

import java.security.SecureRandom;
import java.util.Locale;

/** 가입 코드. 헷갈리는 글자(0, O, 1, I)를 뺀 8자리라 약 1조 가지이고, 충돌은 유니크 제약에 맡긴다. */
public final class JoinCodes {

    private static final char[] CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private JoinCodes() {
    }

    public static String next() {
        char[] code = new char[8];
        for (int i = 0; i < code.length; i++) {
            code[i] = CHARS[RANDOM.nextInt(CHARS.length)];
        }
        return new String(code);
    }

    /** 사람이 입력한 코드: 대문자로 바꾸고 공백과 하이픈을 지운다. */
    public static String normalize(String input) {
        return input == null ? "" : input.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }
}
