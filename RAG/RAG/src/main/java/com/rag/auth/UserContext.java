package com.rag.auth;

import com.rag.entity.User;

/** 当前请求用户（由 LoginInterceptor 注入） */
public class UserContext {

    private static final ThreadLocal<User> HOLDER = new ThreadLocal<>();

    public static void set(User user) {
        HOLDER.set(user);
    }

    public static User get() {
        return HOLDER.get();
    }

    public static User require() {
        User user = HOLDER.get();
        if (user == null) {
            throw new com.rag.common.ApiException("未登录或登录已失效");
        }
        return user;
    }

    public static void clear() {
        HOLDER.remove();
    }
}
