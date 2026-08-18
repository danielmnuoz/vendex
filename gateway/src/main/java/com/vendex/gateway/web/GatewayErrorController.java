package com.vendex.gateway.web;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.Instant;

@RestController
public class GatewayErrorController implements ErrorController {

    private final Clock clock;

    public GatewayErrorController(Clock clock) {
        this.clock = clock;
    }

    @RequestMapping("${server.error.path:${error.path:/error}}")
    ResponseEntity<ApiError> error(HttpServletRequest request) {
        HttpStatus status = status(request);
        ErrorDetail detail = detail(status);
        Object originalPath = request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);
        String path = originalPath == null ? request.getRequestURI() : originalPath.toString();
        return ResponseEntity.status(status).body(new ApiError(
                Instant.now(clock),
                status.value(),
                detail.code(),
                detail.message(),
                CorrelationIdFilter.requestId(request),
                path));
    }

    private static HttpStatus status(HttpServletRequest request) {
        Object value = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        if (value instanceof Integer code) {
            HttpStatus status = HttpStatus.resolve(code);
            if (status != null) {
                return status;
            }
        }
        return HttpStatus.INTERNAL_SERVER_ERROR;
    }

    private static ErrorDetail detail(HttpStatus status) {
        return switch (status) {
            case NOT_FOUND -> new ErrorDetail("ROUTE_NOT_FOUND", "No API route matches this request");
            case METHOD_NOT_ALLOWED -> new ErrorDetail("METHOD_NOT_ALLOWED", "The HTTP method is not supported for this route");
            case UNSUPPORTED_MEDIA_TYPE -> new ErrorDetail("UNSUPPORTED_MEDIA_TYPE", "The request media type is not supported");
            case BAD_REQUEST -> new ErrorDetail("INVALID_REQUEST", "Request validation failed");
            default -> status.is5xxServerError()
                    ? new ErrorDetail("INTERNAL_ERROR", "Unexpected gateway error")
                    : new ErrorDetail("REQUEST_FAILED", "The request could not be completed");
        };
    }

    private record ErrorDetail(String code, String message) {}
}
