package ru.mfa.airline.service;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import ru.mfa.airline.service.binary.BinarySignaturePackage;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

@Component
public class MultipartMixedResponseFactory {
    private static final String CRLF = "\r\n";

    // Формирует multipart/mixed ответ с manifest.bin и data.bin.
    public ResponseEntity<byte[]> build(BinarySignaturePackage binaryPackage) {
        String boundary = "b-" + UUID.randomUUID().toString().replace("-", "");
        byte[] body = buildMultipartBody(boundary, binaryPackage.manifestBytes(), binaryPackage.dataBytes());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "multipart/mixed; boundary=" + boundary)
                .body(body);
    }

    // Собирает тело multipart/mixed с фиксированным порядком частей.
    private byte[] buildMultipartBody(String boundary, byte[] manifestBytes, byte[] dataBytes) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        writePart(stream, boundary, "manifest.bin", manifestBytes);
        writePart(stream, boundary, "data.bin", dataBytes);
        writeAscii(stream, "--" + boundary + "--" + CRLF);
        return stream.toByteArray();
    }

    // Добавляет одну бинарную часть в multipart/mixed.
    private void writePart(ByteArrayOutputStream stream, String boundary, String filename, byte[] payload) {
        writeAscii(stream, "--" + boundary + CRLF);
        writeAscii(stream, "Content-Disposition: attachment; filename=\"" + filename + "\"" + CRLF);
        writeAscii(stream, "Content-Type: application/octet-stream" + CRLF);
        writeAscii(stream, "Content-Length: " + payload.length + CRLF);
        writeAscii(stream, CRLF);
        stream.writeBytes(payload);
        writeAscii(stream, CRLF);
    }

    // Пишет ASCII-строку в поток multipart тела.
    private void writeAscii(ByteArrayOutputStream stream, String value) {
        stream.writeBytes(value.getBytes(StandardCharsets.US_ASCII));
    }
}
