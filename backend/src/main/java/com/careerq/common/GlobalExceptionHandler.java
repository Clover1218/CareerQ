package com.careerq.common;

import com.careerq.common.ApiResponse;
import com.careerq.common.BusinessException;
import com.careerq.common.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiResponse<Void>> handleAuthentication(AuthenticationException ex) {
        return ResponseEntity.status(ErrorCode.UNAUTHORIZED.status())
                .body(ApiResponse.error(ErrorCode.UNAUTHORIZED.code(), ErrorCode.UNAUTHORIZED.message()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(ErrorCode.FORBIDDEN.status())
                .body(ApiResponse.error(ErrorCode.FORBIDDEN.code(), ErrorCode.FORBIDDEN.message()));
    }

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException ex) {
        ErrorCode code = ex.errorCode();
        return ResponseEntity.status(code.status())
                .body(ApiResponse.error(code.code(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        FieldError first = ex.getBindingResult().getFieldErrors().stream().findFirst().orElse(null);
        String message = first == null ? ErrorCode.INVALID_PARAM.message()
                : first.getField() + " " + first.getDefaultMessage();
        return ResponseEntity.badRequest()
                .body(ApiResponse.error(ErrorCode.INVALID_PARAM.code(), message));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unhandled exception", ex);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.status())
                .body(ApiResponse.error(ErrorCode.INTERNAL_ERROR.code(), ErrorCode.INTERNAL_ERROR.message()));
    }
}
