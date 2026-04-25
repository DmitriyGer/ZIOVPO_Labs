package ru.mfa.airline.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public class SignatureIdsRequest {
    @NotEmpty
    private List<UUID> ids;

    public SignatureIdsRequest() {
    }

    public List<UUID> getIds() {
        return ids;
    }

    public void setIds(List<UUID> ids) {
        this.ids = ids;
    }
}
