package com.example.gateway.admin;

import com.example.gateway.audit.AuditLogEntity;
import com.example.gateway.audit.AuditLogRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;

@Service
public class AuditQueryService {
    private final AuditLogRepository repository;

    public AuditQueryService(AuditLogRepository repository) { this.repository = repository; }

    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'AUDITOR')")
    public Page<AuditLogEntity> findAuditLogs(Pageable pageable) {
        return repository.findAll(pageable);
    }
}
