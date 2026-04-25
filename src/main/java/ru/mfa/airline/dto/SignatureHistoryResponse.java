package ru.mfa.airline.dto;

import ru.mfa.airline.model.MalwareSignatureHistory;
import ru.mfa.airline.model.SignatureStatus;

import java.time.Instant;
import java.util.UUID;

public class SignatureHistoryResponse {
    private Long historyId;
    private UUID signatureId;
    private Instant versionCreatedAt;
    private String threatName;
    private String firstBytesHex;
    private String remainderHashHex;
    private Long remainderLength;
    private String fileType;
    private Long offsetStart;
    private Long offsetEnd;
    private Instant updatedAt;
    private SignatureStatus status;
    private String digitalSignatureBase64;

    public SignatureHistoryResponse() {
    }

    // Преобразует запись истории сигнатуры в DTO ответа.
    public static SignatureHistoryResponse from(MalwareSignatureHistory history) {
        SignatureHistoryResponse response = new SignatureHistoryResponse();
        response.setHistoryId(history.getHistoryId());
        response.setSignatureId(history.getSignature().getId());
        response.setVersionCreatedAt(history.getVersionCreatedAt());
        response.setThreatName(history.getThreatName());
        response.setFirstBytesHex(history.getFirstBytesHex());
        response.setRemainderHashHex(history.getRemainderHashHex());
        response.setRemainderLength(history.getRemainderLength());
        response.setFileType(history.getFileType());
        response.setOffsetStart(history.getOffsetStart());
        response.setOffsetEnd(history.getOffsetEnd());
        response.setUpdatedAt(history.getUpdatedAt());
        response.setStatus(history.getStatus());
        response.setDigitalSignatureBase64(history.getDigitalSignatureBase64());
        return response;
    }

    public Long getHistoryId() {
        return historyId;
    }

    public void setHistoryId(Long historyId) {
        this.historyId = historyId;
    }

    public UUID getSignatureId() {
        return signatureId;
    }

    public void setSignatureId(UUID signatureId) {
        this.signatureId = signatureId;
    }

    public Instant getVersionCreatedAt() {
        return versionCreatedAt;
    }

    public void setVersionCreatedAt(Instant versionCreatedAt) {
        this.versionCreatedAt = versionCreatedAt;
    }

    public String getThreatName() {
        return threatName;
    }

    public void setThreatName(String threatName) {
        this.threatName = threatName;
    }

    public String getFirstBytesHex() {
        return firstBytesHex;
    }

    public void setFirstBytesHex(String firstBytesHex) {
        this.firstBytesHex = firstBytesHex;
    }

    public String getRemainderHashHex() {
        return remainderHashHex;
    }

    public void setRemainderHashHex(String remainderHashHex) {
        this.remainderHashHex = remainderHashHex;
    }

    public Long getRemainderLength() {
        return remainderLength;
    }

    public void setRemainderLength(Long remainderLength) {
        this.remainderLength = remainderLength;
    }

    public String getFileType() {
        return fileType;
    }

    public void setFileType(String fileType) {
        this.fileType = fileType;
    }

    public Long getOffsetStart() {
        return offsetStart;
    }

    public void setOffsetStart(Long offsetStart) {
        this.offsetStart = offsetStart;
    }

    public Long getOffsetEnd() {
        return offsetEnd;
    }

    public void setOffsetEnd(Long offsetEnd) {
        this.offsetEnd = offsetEnd;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public SignatureStatus getStatus() {
        return status;
    }

    public void setStatus(SignatureStatus status) {
        this.status = status;
    }

    public String getDigitalSignatureBase64() {
        return digitalSignatureBase64;
    }

    public void setDigitalSignatureBase64(String digitalSignatureBase64) {
        this.digitalSignatureBase64 = digitalSignatureBase64;
    }
}
