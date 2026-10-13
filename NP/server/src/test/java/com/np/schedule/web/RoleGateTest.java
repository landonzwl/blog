package com.np.schedule.web;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RoleGateTest {

    @Autowired
    private TestRestTemplate rest;

    @Test
    void pingIsOpenAndHeaderCannotGrantAdmin() {
        ResponseEntity<String> ping = rest.getForEntity("/api/np/ping", String.class);
        assertEquals(200, ping.getStatusCodeValue());
        HttpHeaders forged = new HttpHeaders();
        forged.add("X-NP-Role", "admin");
        ResponseEntity<String> hidden = rest.exchange("/api/np/admin/board", HttpMethod.GET, new HttpEntity<Void>(forged), String.class);
        assertEquals(401, hidden.getStatusCodeValue());
    }

    @Test
    void staffCannotCallSolveAndOnlySeesOwnPublishedTask() {
        HttpHeaders headers = bearer("lisi", "Np@lisi2026");
        ResponseEntity<String> denied = rest.exchange(
                "/api/np/admin/solve?deptId=east",
                HttpMethod.POST,
                new HttpEntity<Void>(headers),
                String.class);
        assertEquals(403, denied.getStatusCodeValue());

        ResponseEntity<String> home = rest.exchange(
                "/api/np/staff/home",
                HttpMethod.GET,
                new HttpEntity<Void>(headers),
                String.class);
        assertEquals(200, home.getStatusCodeValue());
        String body = home.getBody();
        assertTrue(body.contains("李四已有早会"));
        assertFalse(body.contains("东区货架整理"));
        assertFalse(body.contains("陈晨"));
        assertFalse(body.contains("周敏"));
    }

    @Test
    void visitorSeesPublishedResultOnly() {
        HttpHeaders headers = bearer("guest", "Np@guest2026");
        ResponseEntity<String> response = rest.exchange(
                "/api/np/visitor/tasks",
                HttpMethod.GET,
                new HttpEntity<Void>(headers),
                String.class);
        assertEquals(200, response.getStatusCodeValue());
        String body = response.getBody();
        assertTrue(body.contains("李四已有早会"));
        assertTrue(body.contains("李四"));
        assertFalse(body.contains("东区货架整理"));
        assertFalse(body.contains("陈晨"));
        assertFalse(body.contains("maxMinutes"));
    }

    private HttpHeaders bearer(String username, String password) {
        Map<String, String> body = new LinkedHashMap<String, String>();
        body.put("username", username);
        body.put("password", password);
        ResponseEntity<String> login = rest.postForEntity("/api/np/login", body, String.class);
        assertEquals(200, login.getStatusCodeValue());
        String raw = login.getBody();
        int start = raw.indexOf("\"token\":\"");
        assertTrue(start >= 0);
        start += "\"token\":\"".length();
        int end = raw.indexOf('"', start);
        HttpHeaders headers = new HttpHeaders();
        headers.add("Authorization", "Bearer " + raw.substring(start, end));
        return headers;
    }
}
