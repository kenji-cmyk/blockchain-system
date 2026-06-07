package com.kna.backend;

import com.kna.backend.exception.ApiExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import static org.assertj.core.api.Assertions.assertThat;

class ApiExceptionHandlerStructureTests {

    @Test
    void globalApiExceptionHandlerLivesOutsideControllerPackage() {
        assertThat(ApiExceptionHandler.class.getPackageName())
                .isEqualTo("com.kna.backend.exception");
        assertThat(ApiExceptionHandler.class.isAnnotationPresent(RestControllerAdvice.class))
                .isTrue();
    }
}
