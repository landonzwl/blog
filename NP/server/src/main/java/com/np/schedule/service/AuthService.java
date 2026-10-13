package com.np.schedule.service;

import com.np.schedule.config.ActorContext;
import com.np.schedule.model.SessionView;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.support.EncodedResource;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceUtils;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.Connection;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class AuthService {

    private static final long SESSION_MS = 12L * 60L * 60L * 1000L;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.of("Asia/Shanghai"));
    private static final String[][] USERS = new String[][] {
            {"u-admin", "admin", "Np@admin2026", "admin", "", "管理员"},
            {"u-chenchen", "chenchen", "Np@chenchen2026", "staff", "s1", "陈晨"},
            {"u-lisi", "lisi", "Np@lisi2026", "staff", "s2", "李四"},
            {"u-zhoumin", "zhoumin", "Np@zhoumin2026", "staff", "s6", "周敏"},
            {"u-wangwu", "wangwu", "Np@wangwu2026", "staff", "s3", "王五"},
            {"u-zhaoliu", "zhaoliu", "Np@zhaoliu2026", "staff", "s4", "赵六"},
            {"u-sunqi", "sunqi", "Np@sunqi2026", "staff", "s5", "孙七"},
            {"u-wujiu", "wujiu", "Np@wujiu2026", "staff", "s7", "吴九"},
            {"u-guest", "guest", "Np@guest2026", "visitor", "", "访客"}
    };

    private final JdbcTemplate jdbc;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
    private final SecureRandom random = new SecureRandom();

    public AuthService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        prepare();
    }

    public SessionView login(String username, String password) {
        String name = username == null ? "" : username.trim();
        String raw = password == null ? "" : password;
        UserRow user = findUser(name);
        if (user == null || user.enabled == 0 || !encoder.matches(raw, user.passwordHash)) {
            audit(name.isEmpty() ? "-" : name, "登录失败", "");
            return null;
        }
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = hex(bytes);
        long expires = System.currentTimeMillis() + SESSION_MS;
        jdbc.update("INSERT INTO np_session (token_hash, user_id, expires_at) VALUES (?, ?, ?)", sha(token), user.id, expires);
        audit(user.username, "登录成功", user.role);
        SessionView view = new SessionView();
        view.token = token;
        view.role = user.role;
        view.name = user.displayName;
        view.staffId = user.staffId;
        return view;
    }

    public void logout(String token) {
        if (token == null || token.isEmpty()) {
            return;
        }
        jdbc.update("DELETE FROM np_session WHERE token_hash = ?", sha(token));
    }

    public ActorContext.Actor resolve(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        ActorContext.Actor actor;
        try {
            actor = jdbc.queryForObject(
                    "SELECT u.id, u.username, u.role, u.staff_id, u.display_name, u.enabled, s.expires_at FROM np_session s JOIN np_user u ON u.id = s.user_id WHERE s.token_hash = ?",
                    (rs, rowNum) -> {
                        if (rs.getInt("enabled") == 0 || rs.getLong("expires_at") < System.currentTimeMillis()) {
                            return null;
                        }
                        return new ActorContext.Actor(
                                rs.getString("id"),
                                rs.getString("username"),
                                rs.getString("role"),
                                rs.getString("staff_id"),
                                rs.getString("display_name"));
                    },
                    sha(token));
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
        if (actor == null) {
            jdbc.update("DELETE FROM np_session WHERE token_hash = ?", sha(token));
            return null;
        }
        jdbc.update("UPDATE np_session SET expires_at = ? WHERE token_hash = ?", System.currentTimeMillis() + SESSION_MS, sha(token));
        return actor;
    }

    public Issued ensureAccount(String staffId, String displayName) {
        if (staffId == null || staffId.isEmpty()) {
            return new Issued("", false);
        }
        try {
            String username = jdbc.queryForObject("SELECT username FROM np_user WHERE staff_id = ?", String.class, staffId);
            return new Issued(username, false);
        } catch (EmptyResultDataAccessException ex) {
            String username = staffId;
            Integer taken = jdbc.queryForObject("SELECT COUNT(*) FROM np_user WHERE username = ?", Integer.class, username);
            if (taken != null && taken > 0) {
                username = "u" + staffId;
            }
            jdbc.update("INSERT INTO np_user (id, username, password_hash, role, staff_id, display_name, enabled) VALUES (?, ?, ?, ?, ?, ?, 1)",
                    "u-" + staffId, username, encoder.encode("Np@" + staffId + "2026"), "staff", staffId, displayName == null ? username : displayName);
            return new Issued(username, true);
        }
    }

    public void audit(String username, String action, String detail) {
        try {
            jdbc.update("INSERT INTO np_audit (at_ms, username, action, detail) VALUES (?, ?, ?, ?)",
                    System.currentTimeMillis(),
                    cut(username, 32),
                    cut(action, 64),
                    cut(detail, 255));
        } catch (RuntimeException ex) {
            // 审计写入失败不阻断业务
        }
    }

    public List<Map<String, Object>> recent() {
        return jdbc.query("SELECT at_ms, username, action, detail FROM np_audit ORDER BY id DESC LIMIT 100", (rs, rowNum) -> {
            Map<String, Object> row = new LinkedHashMap<String, Object>();
            row.put("at", STAMP.format(Instant.ofEpochMilli(rs.getLong("at_ms"))));
            row.put("username", rs.getString("username"));
            row.put("action", rs.getString("action"));
            row.put("detail", rs.getString("detail"));
            return row;
        });
    }

    private void prepare() {
        DataSource dataSource = jdbc.getDataSource();
        Connection connection = DataSourceUtils.getConnection(dataSource);
        try {
            ScriptUtils.executeSqlScript(connection, new EncodedResource(new ClassPathResource("np-auth-schema.sql"), "UTF-8"));
        } finally {
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
        Integer rows = jdbc.queryForObject("SELECT COUNT(*) FROM np_user", Integer.class);
        if (rows != null && rows > 0) {
            return;
        }
        for (String[] user : USERS) {
            jdbc.update("INSERT INTO np_user (id, username, password_hash, role, staff_id, display_name, enabled) VALUES (?, ?, ?, ?, ?, ?, 1)",
                    user[0], user[1], encoder.encode(user[2]), user[3], user[4], user[5]);
        }
    }

    private UserRow findUser(String username) {
        try {
            return jdbc.queryForObject(
                    "SELECT id, username, password_hash, role, staff_id, display_name, enabled FROM np_user WHERE username = ?",
                    (rs, rowNum) -> {
                        UserRow row = new UserRow();
                        row.id = rs.getString("id");
                        row.username = rs.getString("username");
                        row.passwordHash = rs.getString("password_hash");
                        row.role = rs.getString("role");
                        row.staffId = rs.getString("staff_id");
                        row.displayName = rs.getString("display_name");
                        row.enabled = rs.getInt("enabled");
                        return row;
                    },
                    username);
        } catch (EmptyResultDataAccessException ex) {
            return null;
        }
    }

    private static String sha(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return hex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("无法签发登录凭证", ex);
        }
    }

    private static String hex(byte[] bytes) {
        char[] chars = new char[bytes.length * 2];
        char[] alphabet = "0123456789abcdef".toCharArray();
        for (int i = 0; i < bytes.length; i++) {
            int value = bytes[i] & 0xff;
            chars[i * 2] = alphabet[value >>> 4];
            chars[i * 2 + 1] = alphabet[value & 0x0f];
        }
        return new String(chars);
    }

    private static String cut(String value, int max) {
        if (value == null) {
            return "";
        }
        return value.length() <= max ? value : value.substring(0, max);
    }

    public static final class Issued {
        public final String username;
        public final boolean created;

        public Issued(String username, boolean created) {
            this.username = username;
            this.created = created;
        }
    }

    private static final class UserRow {
        private String id;
        private String username;
        private String passwordHash;
        private String role;
        private String staffId;
        private String displayName;
        private int enabled;
    }
}
