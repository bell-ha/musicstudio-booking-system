package com.musicstudio.common.error;

import java.net.URI;

import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;

public final class ProblemTypes {

    private static final String BASE = "https://api.example.com/problems/";

    private ProblemTypes() {
    }

    public static ProblemDetail of(HttpStatusCode status, String type, String title) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setType(URI.create(BASE + type));
        problem.setTitle(title);
        return problem;
    }
}
