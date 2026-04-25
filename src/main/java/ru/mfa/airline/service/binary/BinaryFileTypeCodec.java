package ru.mfa.airline.service.binary;

import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Map;

@Component
public class BinaryFileTypeCodec {
    private static final int UNKNOWN_CODE = 255;

    private static final Map<String, Integer> FILE_TYPE_TO_CODE = Map.ofEntries(
            Map.entry("exe", 1),
            Map.entry("dll", 2),
            Map.entry("sys", 3),
            Map.entry("bin", 4),
            Map.entry("elf", 5),
            Map.entry("macho", 6),
            Map.entry("apk", 7),
            Map.entry("jar", 8),
            Map.entry("doc", 9),
            Map.entry("pdf", 10),
            Map.entry("script", 11));

    // Преобразует fileType в числовой бинарный код.
    public int toCode(String fileType) {
        if (fileType == null || fileType.isBlank()) {
            throw new IllegalArgumentException("fileType must not be blank");
        }
        return FILE_TYPE_TO_CODE.getOrDefault(fileType.trim().toLowerCase(Locale.ROOT), UNKNOWN_CODE);
    }
}
