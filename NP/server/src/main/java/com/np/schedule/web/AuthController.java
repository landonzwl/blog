package com.np.schedule.web;

import com.np.schedule.common.Result;
import com.np.schedule.config.ActorContext;
import com.np.schedule.model.LoginRequest;
import com.np.schedule.model.SessionView;
import com.np.schedule.service.AuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/np")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<Result<SessionView>> login(@RequestBody LoginRequest request) {
        SessionView view = authService.login(request == null ? null : request.username, request == null ? null : request.password);
        if (view == null) {
            return ResponseEntity.status(401).body(Result.fail(401, "账号或口令不正确"));
        }
        return ResponseEntity.ok(Result.ok(view));
    }

    @PostMapping("/logout")
    public Result<String> logout(@RequestHeader(value = "Authorization", required = false) String authorization) {
        authService.logout(token(authorization));
        return Result.ok("已退出");
    }

    @GetMapping("/me")
    public Result<SessionView> me() {
        ActorContext.Actor actor = ActorContext.get();
        SessionView view = new SessionView();
        view.role = actor.role;
        view.name = actor.name;
        view.staffId = actor.staffId;
        return Result.ok(view);
    }

    @GetMapping("/admin/audit")
    public Result<List<Map<String, Object>>> audit() {
        return Result.ok(authService.recent());
    }

    private static String token(String authorization) {
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
