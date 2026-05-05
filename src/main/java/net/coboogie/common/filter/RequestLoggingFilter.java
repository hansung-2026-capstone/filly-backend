package net.coboogie.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * 요청 단위 로그와 MDC 추적 정보를 설정하는 필터.
 */
@Slf4j
@Component
@Order(Ordered.LOWEST_PRECEDENCE)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String REQUEST_ID_MDC_KEY = "requestId";
    private static final String USER_ID_MDC_KEY = "userId";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        long startedAt = System.currentTimeMillis();
        String requestId = resolveRequestId(request);

        MDC.put(REQUEST_ID_MDC_KEY, requestId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        try {
            putUserIdIfAuthenticated();
            log.info("request start method={} uri={} query={}",
                    request.getMethod(), request.getRequestURI(), sanitizeQuery(request.getQueryString()));
            filterChain.doFilter(request, response);
        } finally {
            putUserIdIfAuthenticated();
            long elapsedMs = System.currentTimeMillis() - startedAt;
            log.info("request end method={} uri={} status={} elapsedMs={}",
                    request.getMethod(), request.getRequestURI(), response.getStatus(), elapsedMs);
            MDC.clear();
        }
    }

    private String resolveRequestId(HttpServletRequest request) {
        String requestId = request.getHeader(REQUEST_ID_HEADER);
        if (requestId == null || requestId.isBlank()) {
            return UUID.randomUUID().toString();
        }
        return requestId;
    }

    private void putUserIdIfAuthenticated() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getPrincipal() == null) {
            return;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof Long userId) {
            MDC.put(USER_ID_MDC_KEY, String.valueOf(userId));
        }
    }

    private String sanitizeQuery(String queryString) {
        if (queryString == null || queryString.isBlank()) {
            return "";
        }
        return queryString.replaceAll("(?i)(token|accessToken|refreshToken|code)=[^&]*", "$1=***");
    }
}
