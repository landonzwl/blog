package com.np.schedule.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.np.schedule.common.Result;
import com.np.schedule.service.AuthService;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class RoleGate implements HandlerInterceptor {

    private final ObjectMapper objectMapper;
    private final AuthService authService;

    public RoleGate(ObjectMapper objectMapper, AuthService authService) {
        this.objectMapper = objectMapper;
        this.authService = authService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String uri = request.getRequestURI();
        if ("/api/np/ping".equals(uri) || "/api/np/login".equals(uri)) {
            return true;
        }
        ActorContext.Actor actor = authService.resolve(bearer(request.getHeader("Authorization")));
        if (actor == null) {
            return reject(response, 401, "请先登录");
        }
        if ("/api/np/me".equals(uri) || "/api/np/logout".equals(uri)) {
            ActorContext.set(actor);
            return true;
        }
        boolean allowed = false;
        if (uri.startsWith("/api/np/admin/")) {
            allowed = "admin".equals(actor.role);
        } else if (uri.startsWith("/api/np/staff/")) {
            allowed = "staff".equals(actor.role) && actor.staffId != null && !actor.staffId.isEmpty();
        } else if (uri.startsWith("/api/np/visitor/")) {
            allowed = "visitor".equals(actor.role);
        }
        if (!allowed) {
            return reject(response, 403, "当前角色不能执行该操作");
        }
        ActorContext.set(actor);
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        ActorContext.Actor actor = ActorContext.get();
        try {
            if (actor != null && ex == null && response.getStatus() < 400 && write(request.getMethod())) {
                authService.audit(actor.username, request.getMethod() + " " + request.getRequestURI(), "");
            }
        } finally {
            ActorContext.clear();
        }
    }

    private boolean reject(HttpServletResponse response, int status, String message) throws Exception {
        response.setStatus(status);
        response.setCharacterEncoding("UTF-8");
        response.setContentType("application/json;charset=UTF-8");
        objectMapper.writeValue(response.getWriter(), Result.fail(status, message));
        return false;
    }

    private static boolean write(String method) {
        return "POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "DELETE".equalsIgnoreCase(method);
    }

    private static String bearer(String authorization) {
        if (authorization == null) {
            return "";
        }
        String value = authorization.trim();
        if (value.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return value.substring(7).trim();
        }
        return "";
    }
}
