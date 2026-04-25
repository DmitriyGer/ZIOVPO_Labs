package ru.mfa.airline.service.binary;

public enum BinaryExportType {
    FULL(1),
    INCREMENT(2),
    BY_IDS(3);

    private final int code;

    BinaryExportType(int code) {
        this.code = code;
    }

    // Возвращает код типа выгрузки для манифеста.
    public int code() {
        return code;
    }
}
