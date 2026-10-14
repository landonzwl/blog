package com.rag.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.rag.common.ApiException;
import com.rag.entity.User;
import com.rag.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/** 登录/注册（原型阶段按工号免密登录，新工号需填姓名自动注册） */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final UserMapper userMapper;

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody Map<String, String> body) {
        String empNo = body.getOrDefault("empNo", "").trim();
        String name = body.getOrDefault("name", "").trim();
        if (empNo.isEmpty()) {
            throw new ApiException("请输入工号");
        }
        User user = userMapper.selectOne(new LambdaQueryWrapper<User>().eq(User::getEmpNo, empNo));
        if (user == null) {
            if (name.isEmpty()) {
                throw new ApiException("该工号不存在，新用户请填写姓名完成注册");
            }
            user = new User();
            user.setEmpNo(empNo);
            user.setName(name);
            user.setDept(body.getOrDefault("dept", ""));
            user.setRole("USER");
            userMapper.insert(user);
        }
        return toVo(user);
    }

    private Map<String, Object> toVo(User user) {
        Map<String, Object> vo = new LinkedHashMap<>();
        vo.put("id", user.getId());
        vo.put("empNo", user.getEmpNo());
        vo.put("name", user.getName());
        vo.put("dept", user.getDept());
        vo.put("role", user.getRole());
        return vo;
    }
}
