package ru.mfa.airline.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;

public class SignatureUpsertRequest {
    @NotBlank
    private String threatName;

    @NotBlank
    @Pattern(regexp = "^[0-9a-fA-F]+$", message = "firstBytesHex must contain only hex symbols")
    private String firstBytesHex;

    @NotBlank
    @Pattern(regexp = "^[0-9a-fA-F]+$", message = "remainderHashHex must contain only hex symbols")
    private String remainderHashHex;

    @NotNull
    @PositiveOrZero
    private Long remainderLength;

    @NotBlank
    private String fileType;

    @NotNull
    @PositiveOrZero
    private Long offsetStart;

    @NotNull
    @PositiveOrZero
    private Long offsetEnd;

    public SignatureUpsertRequest() {
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
}
