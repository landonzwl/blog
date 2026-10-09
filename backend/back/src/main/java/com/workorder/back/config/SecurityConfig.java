package com.workorder.back.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Spring Security 配置（P1 临时版）
 * 当前阶段：全部放行，方便联调
 * P3 阶段：收紧为 /api/articles 公开 + /api/admin/** 需 JWT
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        // BCrypt：加盐哈希，cost=10（默认）。P3 用户表密码用它存与校验
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .cors().and()
                .csrf().disable()                  // 前后端分离禁用 CSRF
                .authorizeRequests()
                .anyRequest().permitAll()           // P2 仍全放行；P3 收紧 /admin/**
                .and()
                .formLogin().disable()              // 关掉默认登录页
                .httpBasic().disable();             // 关掉 Basic 认证
        return http.build();
    }
}
