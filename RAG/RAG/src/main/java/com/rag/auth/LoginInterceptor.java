package com.rag.auth;

import com.rag.mapper.UserMapper;
import com.rag.entity.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 简易鉴权：前端在请求头 X-User-Id 中携带用户 ID（原型阶段免密，
 * 生产应替换为 JWT / SSO）。
 */
@Component
@RequiredArgsConstructor
public class LoginInterceptor implements HandlerInterceptor {

    private final UserMapper userMapper;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String userId = request.getHeader("X-User-Id");
        // EventSource 不支持自定义请求头，SSE 端点允许用 query 参数 uid 鉴权
        if (userId == null || userId.isBlank()) {
            userId = request.getParameter("uid");
        }
        if (userId != null && userId.matches("\\d+")) {
            User user = userMapper.selectById(Long.parseLong(userId));
            if (user != null) {
                UserContext.set(user);
                return true;
            }
        }
        response.setStatus(401);
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write("{\"message\":\"未登录或登录已失效\"}");
        return false;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        UserContext.clear();
    }
}
