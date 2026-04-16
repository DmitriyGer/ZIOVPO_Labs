package ru.mfa.airline.dto;

import ru.mfa.airline.model.MalwareSignature;
import ru.mfa.airline.model.SignatureStatus;

import java.time.Instant;
import java.util.UUID;

public class SignatureResponse {
    private UUID id;
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

    public SignatureResponse() {
    }

    // Преобразует сущность сигнатуры в DTO ответа.
    public static SignatureResponse from(MalwareSignature signature) {
        SignatureResponse response = new SignatureResponse();
        response.setId(signature.getId());
        response.setThreatName(signature.getThreatName());
        response.setFirstBytesHex(signature.getFirstBytesHex());
        response.setRemainderHashHex(signature.getRemainderHashHex());
        response.setRemainderLength(signature.getRemainderLength());
        response.setFileType(signature.getFileType());
        response.setOffsetStart(signature.getOffsetStart());
        response.setOffsetEnd(signature.getOffsetEnd());
        response.setUpdatedAt(signature.getUpdatedAt());
        response.setStatus(signature.getStatus());
        response.setDigitalSignatureBase64(signature.getDigitalSignatureBase64());
        return response;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
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
