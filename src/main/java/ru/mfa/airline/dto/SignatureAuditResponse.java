package ru.mfa.airline.dto;

import ru.mfa.airline.model.MalwareSignatureAudit;

import java.time.Instant;
import java.util.UUID;

public class SignatureAuditResponse {
    private Long auditId;
    private UUID signatureId;
    private String changedBy;
    private Instant changedAt;
    private String fieldsChanged;
    private String description;

    public SignatureAuditResponse() {
    }

    // Преобразует запись аудита сигнатуры в DTO ответа.
    public static SignatureAuditResponse from(MalwareSignatureAudit audit) {
        SignatureAuditResponse response = new SignatureAuditResponse();
        response.setAuditId(audit.getAuditId());
        response.setSignatureId(audit.getSignature().getId());
        response.setChangedBy(audit.getChangedBy());
        response.setChangedAt(audit.getChangedAt());
        response.setFieldsChanged(audit.getFieldsChanged());
        response.setDescription(audit.getDescription());
        return response;
    }

    public Long getAuditId() {
        return auditId;
    }

    public void setAuditId(Long auditId) {
        this.auditId = auditId;
    }

    public UUID getSignatureId() {
        return signatureId;
    }

    public void setSignatureId(UUID signatureId) {
        this.signatureId = signatureId;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public void setChangedBy(String changedBy) {
        this.changedBy = changedBy;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(Instant changedAt) {
        this.changedAt = changedAt;
    }

    public String getFieldsChanged() {
        return fieldsChanged;
    }

    public void setFieldsChanged(String fieldsChanged) {
        this.fieldsChanged = fieldsChanged;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }
}
