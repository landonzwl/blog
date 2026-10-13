package com.np.schedule.web;

import com.np.schedule.common.Result;
import com.np.schedule.model.ErpBoard;
import com.np.schedule.service.ErpService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/np/admin/erp")
public class ErpController {

    private final ErpService erpService;

    public ErpController(ErpService erpService) {
        this.erpService = erpService;
    }

    @GetMapping
    public Result<ErpBoard> board() {
        return Result.ok(erpService.board());
    }

    @GetMapping("/{table}/options")
    public Result<List<Map<String, String>>> options(@PathVariable String table) {
        return Result.ok(erpService.options(table));
    }

    @PostMapping("/{table}/option")
    public Result<Map<String, String>> option(@PathVariable String table, @RequestBody Map<String, Object> body) {
        Object label = body == null ? null : body.get("label");
        return Result.ok(erpService.quickOption(table, label == null ? "" : String.valueOf(label)));
    }

    @PostMapping("/{table}/query")
    public Result<Map<String, Object>> query(@PathVariable String table, @RequestBody Map<String, Object> body) {
        return Result.ok(erpService.query(table, body));
    }

    @PostMapping("/{table}")
    public Result<ErpBoard> create(@PathVariable String table, @RequestBody Map<String, Object> body) {
        return Result.ok(erpService.create(table, body));
    }

    @PutMapping("/{table}/{id}")
    public Result<ErpBoard> update(@PathVariable String table, @PathVariable String id, @RequestBody Map<String, Object> body) {
        return Result.ok(erpService.update(table, id, body));
    }

    @PostMapping("/{table}/{id}/{action}")
    public Result<ErpBoard> act(@PathVariable String table, @PathVariable String id, @PathVariable String action) {
        return Result.ok(erpService.act(table, id, action));
    }
}
