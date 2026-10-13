package com.np.schedule.web;

import com.np.schedule.common.Result;
import com.np.schedule.model.ErpBoard;
import com.np.schedule.service.ErpService;
import com.np.schedule.store.ErpDatabase;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/np/admin/v1")
public class ResourceController {

    private final ErpService erpService;
    private final ErpDatabase database;

    public ResourceController(ErpService erpService, ErpDatabase database) {
        this.erpService = erpService;
        this.database = database;
    }

    @GetMapping("/meta")
    public Result<Map<String, Object>> meta() {
        return Result.ok(database.meta());
    }

    @PostMapping("/meta/entities")
    public ResponseEntity<Result<Map<String, Object>>> define(@RequestBody Map<String, Object> body) {
        String message = erpService.defineEntity(body);
        if (message != null) return reject(422, message);
        return ResponseEntity.ok(Result.ok(database.meta()));
    }

    @PostMapping("/meta/entities/{code}/fields")
    public ResponseEntity<Result<Map<String, Object>>> addField(@PathVariable String code, @RequestBody Map<String, Object> body) {
        String message = erpService.addField(code, body);
        if (message != null) return reject(422, message);
        return ResponseEntity.ok(Result.ok(database.meta()));
    }

    @DeleteMapping("/meta/entities/{code}/fields/{key}")
    public ResponseEntity<Result<Map<String, Object>>> removeField(@PathVariable String code, @PathVariable String key) {
        String message = erpService.removeField(code, key);
        if (message != null) return reject(422, message);
        return ResponseEntity.ok(Result.ok(database.meta()));
    }

    @PostMapping("/meta/entities/{code}/actions")
    public ResponseEntity<Result<Map<String, Object>>> addAction(@PathVariable String code, @RequestBody Map<String, Object> body) {
        String message = erpService.addAction(code, body);
        if (message != null) return reject(422, message);
        return ResponseEntity.ok(Result.ok(database.meta()));
    }

    @PostMapping("/meta/entities/{code}/copy")
    public ResponseEntity<Result<Map<String, Object>>> copy(@PathVariable String code, @RequestBody Map<String, Object> body) {
        String next = body == null || body.get("code") == null ? "" : String.valueOf(body.get("code"));
        String title = body == null || body.get("title") == null ? "" : String.valueOf(body.get("title"));
        String message = erpService.copyEntity(code, next, title);
        if (message != null) return reject(422, message);
        return ResponseEntity.ok(Result.ok(database.meta()));
    }

    @DeleteMapping("/meta/entities/{code}")
    public ResponseEntity<Result<Map<String, Object>>> removeEntity(@PathVariable String code) {
        String message = erpService.removeEntity(code);
        if (message != null) return reject(422, message);
        return ResponseEntity.ok(Result.ok(database.meta()));
    }

    @GetMapping("/org")
    public Result<Map<String, Object>> org() {
        Object org = database.meta().get("org");
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        if (org instanceof Map) {
            Map<?, ?> source = (Map<?, ?>) org;
            for (Map.Entry<?, ?> entry : source.entrySet()) body.put(String.valueOf(entry.getKey()), entry.getValue());
        }
        return Result.ok(body);
    }

    @PutMapping("/org")
    public ResponseEntity<Result<Map<String, Object>>> rename(@RequestBody Map<String, Object> body) {
        String name = body == null || body.get("name") == null ? "" : String.valueOf(body.get("name"));
        String sites = body == null || body.get("sites") == null ? "" : String.valueOf(body.get("sites"));
        String message = erpService.renameOrg(name, sites);
        if (message != null) return reject(422, message);
        return orgResponse();
    }

    @GetMapping("/{entity}")
    public ResponseEntity<Result<Map<String, Object>>> list(@PathVariable String entity,
                                                            @RequestParam(value = "page", defaultValue = "1") int page,
                                                            @RequestParam(value = "size", defaultValue = "20") int size,
                                                            @RequestParam(value = "status", defaultValue = "") String status,
                                                            @RequestParam(value = "q", defaultValue = "") String keyword) {
        if (!database.knownEntity(entity)) return reject(422, "业务对象不存在");
        return ResponseEntity.ok(Result.ok(database.page(entity, status, keyword, page, size)));
    }

    @GetMapping("/{entity}/{id}")
    public ResponseEntity<Result<Map<String, Object>>> one(@PathVariable String entity, @PathVariable String id) {
        if (!database.recordIn(entity, id)) return reject(422, "单据不存在");
        return ResponseEntity.ok(Result.ok(database.readRecord(id)));
    }

    @PostMapping("/{entity}")
    public ResponseEntity<Result<Map<String, Object>>> create(@PathVariable String entity, @RequestBody Map<String, Object> body) {
        if (!database.knownEntity(entity)) return reject(422, "业务对象不存在");
        ErpBoard board = erpService.create(entity, body);
        if (rejected(board.notice)) return reject(noticeCode(board.notice), board.notice);
        List<Map<String, Object>> rows = board.tables.get(entity);
        Map<String, Object> record = new LinkedHashMap<String, Object>();
        if (rows != null && !rows.isEmpty()) record.putAll(rows.get(0));
        record.put("notice", board.notice);
        return ResponseEntity.ok(Result.ok(record));
    }

    @PutMapping("/{entity}/{id}")
    public ResponseEntity<Result<Map<String, Object>>> update(@PathVariable String entity, @PathVariable String id, @RequestBody Map<String, Object> body) {
        ErpBoard board = erpService.update(entity, id, body);
        if (rejected(board.notice)) return reject(noticeCode(board.notice), board.notice);
        return ResponseEntity.ok(Result.ok(find(board, entity, id, board.notice)));
    }

    @DeleteMapping("/{entity}/{id}")
    public ResponseEntity<Result<Map<String, Object>>> delete(@PathVariable String entity, @PathVariable String id) {
        ErpBoard board = erpService.act(entity, id, "remove");
        if (rejected(board.notice)) return reject(noticeCode(board.notice), board.notice);
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("id", id);
        body.put("notice", board.notice);
        return ResponseEntity.ok(Result.ok(body));
    }

    @GetMapping("/{entity}/{id}/files")
    public ResponseEntity<Result<List<Map<String, Object>>>> files(@PathVariable String entity, @PathVariable String id) {
        if (!database.recordIn(entity, id)) return ResponseEntity.status(422).body(Result.fail(422, "单据不存在"));
        return ResponseEntity.ok(Result.ok(database.listFiles(id)));
    }

    @PostMapping("/{entity}/{id}/files")
    public ResponseEntity<Result<Map<String, Object>>> upload(@PathVariable String entity, @PathVariable String id, @RequestParam("file") MultipartFile file) throws Exception {
        if (!database.recordIn(entity, id)) return reject(422, "单据不存在");
        if (file == null || file.isEmpty()) return reject(422, "请选择文件");
        if (file.getSize() > 2 * 1024 * 1024) return reject(422, "单个文件不能超过 2MB");
        String stored = database.storeFile(id, file.getOriginalFilename(), file.getContentType(), file.getBytes());
        if (stored == null) return reject(422, "文件没有保存");
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("id", stored);
        body.put("name", file.getOriginalFilename());
        return ResponseEntity.ok(Result.ok(body));
    }

    @GetMapping("/{entity}/{id}/files/{fileId}")
    public ResponseEntity<byte[]> download(@PathVariable String entity, @PathVariable String id, @PathVariable String fileId) {
        if (!database.recordIn(entity, id)) return ResponseEntity.notFound().build();
        Map<String, Object> file = database.readFile(fileId);
        if (file == null || !id.equals(file.get("recordId"))) return ResponseEntity.notFound().build();
        byte[] content = (byte[]) file.get("content");
        String mime = String.valueOf(file.get("mime"));
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(mime == null || mime.isEmpty() || "null".equals(mime) ? "application/octet-stream" : mime));
        headers.setContentDisposition(ContentDisposition.attachment().filename(String.valueOf(file.get("name")), java.nio.charset.StandardCharsets.UTF_8).build());
        return ResponseEntity.ok().headers(headers).body(content);
    }

    @DeleteMapping("/{entity}/{id}/files/{fileId}")
    public ResponseEntity<Result<Map<String, Object>>> deleteFile(@PathVariable String entity, @PathVariable String id, @PathVariable String fileId) {
        if (!database.recordIn(entity, id)) return reject(422, "单据不存在");
        Map<String, Object> file = database.readFile(fileId);
        if (file == null || !id.equals(file.get("recordId"))) return reject(422, "附件不存在");
        database.deleteFile(fileId);
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("id", fileId);
        return ResponseEntity.ok(Result.ok(body));
    }

    @PostMapping("/{entity}/{id}/actions/{action}")
    public ResponseEntity<Result<Map<String, Object>>> act(@PathVariable String entity, @PathVariable String id, @PathVariable String action) {
        ErpBoard board = erpService.act(entity, id, action);
        if (rejected(board.notice)) return reject(noticeCode(board.notice), board.notice);
        return ResponseEntity.ok(Result.ok(find(board, entity, id, board.notice)));
    }

    private ResponseEntity<Result<Map<String, Object>>> orgResponse() {
        return ResponseEntity.ok(org().getCode() == 200 ? org() : Result.ok(new LinkedHashMap<String, Object>()));
    }

    private static Map<String, Object> find(ErpBoard board, String entity, String id, String notice) {
        List<Map<String, Object>> rows = board.tables.get(entity);
        if (rows != null) {
            for (Map<String, Object> row : rows) {
                if (id.equals(String.valueOf(row.get("id")))) {
                    Map<String, Object> copy = new LinkedHashMap<String, Object>(row);
                    copy.put("notice", notice);
                    return copy;
                }
            }
        }
        Map<String, Object> body = new LinkedHashMap<String, Object>();
        body.put("id", id);
        body.put("notice", notice);
        return body;
    }

    private static boolean rejected(String notice) {
        return notice != null && !notice.isEmpty() && !notice.startsWith("已");
    }

    private static int noticeCode(String notice) {
        return notice != null && notice.contains("刷新后重试") ? 409 : 422;
    }

    private static ResponseEntity<Result<Map<String, Object>>> reject(int code, String message) {
        return ResponseEntity.status(code).body(Result.fail(code, message));
    }
}
