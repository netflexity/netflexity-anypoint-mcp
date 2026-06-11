package com.netflexity.anypoint.mcp.audit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/admin")
public class AuditController {

    private final AuditService auditService;
    private final String adminKey;

    public AuditController(AuditService auditService,
                            @Value("${admin.key:}") String adminKey) {
        this.auditService = auditService;
        this.adminKey = adminKey;
    }

    @GetMapping("/audit")
    public List<AuditEntry> getAudit(
            @RequestHeader(value = "X-Admin-Key", required = false) String key,
            @RequestParam(defaultValue = "100") int limit) {
        if (adminKey.isBlank() || !adminKey.equals(key)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid admin key");
        }
        return auditService.getRecent(limit);
    }

    @GetMapping("/health")
    public java.util.Map<String, Object> health() {
        return java.util.Map.of(
                "status", "UP",
                "auditEntries", auditService.getRecent(1).size() > 0 ? "active" : "empty",
                "timestamp", java.time.Instant.now().toString()
        );
    }
}
