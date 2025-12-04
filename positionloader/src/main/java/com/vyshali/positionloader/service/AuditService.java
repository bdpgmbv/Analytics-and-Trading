package com.vyshali.positionloader.service;

/*
 * 12/04/2025 - 11:41 AM
 * @author Vyshali Prabananth Lal
 */

import com.vyshali.positionloader.repository.AuditRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuditService {
    private final AuditRepository auditRepo;

    @Transactional
    public void logAction(String actionType, String targetId, String user, String status) {
        auditRepo.logAction(actionType, targetId, user, status);
    }
}
