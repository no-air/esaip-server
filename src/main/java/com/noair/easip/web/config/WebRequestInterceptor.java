package com.noair.easip.web.config;

import com.noair.easip.util.IdGenerator;
import com.noair.easip.web.config.properties.WebProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Slf4j
@RequiredArgsConstructor
@Component
public class WebRequestInterceptor implements HandlerInterceptor {

    private static final String START_TIME_ATTR_NAME = "startTime";

    final WebProperties webProperties;


    @Override
    public boolean preHandle(
            HttpServletRequest request, HttpServletResponse response, Object handler
    ) throws Exception {
        MDC.put("requestId", IdGenerator.generateAlphanumericString(8));
        long startTime = System.currentTimeMillis();
        request.setAttribute(START_TIME_ATTR_NAME, startTime);
        return true;
    }

    @Override
    public void afterCompletion(
            HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex
    ) throws Exception {
        if (webProperties.isNoLoggable(request.getServletPath())) return;
        if (isPreflight(request)) return;

        String appVersionValue = request.getHeader(webProperties.headerNames().appVersionHeader());
        String platformValue = request.getHeader(webProperties.headerNames().platformHeader());
        String userIdValue = request.getHeader(webProperties.headerNames().userIdHeader());
        String originIp = request.getRemoteAddr();

        String appVersion =
                String.format("[%s]", appVersionValue != null ? appVersionValue : "UNKNOWN VERSION");
        String platform =
                String.format("[%s]", platformValue != null ? platformValue : "UNKNOWN PLATFORM");
        String userId =
                String.format("[%s]", userIdValue != null ? userIdValue : "UNKNOWN USER");

        long startTime = (long) request.getAttribute(START_TIME_ATTR_NAME);
        long endTime = System.currentTimeMillis();
        long executionTime = endTime - startTime;


        log.info("{} {} {} {} {} {} {} {}ms", appVersion, platform, userId, request.getMethod(), request.getRequestURI(), originIp, response.getStatus(), executionTime);
        MDC.clear();
    }

    private boolean isPreflight(HttpServletRequest request) {
        return request.getMethod().equals(HttpMethod.OPTIONS.toString());
    }
}
