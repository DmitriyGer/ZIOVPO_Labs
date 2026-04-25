package ru.mfa.airline.service.binary;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.mfa.airline.model.MalwareSignature;
import ru.mfa.airline.model.SignatureStatus;
import ru.mfa.airline.repository.MalwareSignatureRepository;
import ru.mfa.signature.SignatureService;

import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;

@Service
public class BinarySignatureExportService {
    private static final String MANIFEST_MAGIC = "MF-DMITRIY";
    private static final String DATA_MAGIC = "DB-DMITRIY";
    private static final int FORMAT_VERSION = 1;
    private static final long SINCE_NOT_USED = -1L;

    private static final int STATUS_ACTUAL = 1;
    private static final int STATUS_DELETED = 2;

    private final MalwareSignatureRepository signatureRepository;
    private final SignatureService signatureService;
    private final BinaryFileTypeCodec binaryFileTypeCodec;

    public BinarySignatureExportService(MalwareSignatureRepository signatureRepository,
            SignatureService signatureService,
            BinaryFileTypeCodec binaryFileTypeCodec) {
        this.signatureRepository = signatureRepository;
        this.signatureService = signatureService;
        this.binaryFileTypeCodec = binaryFileTypeCodec;
    }

    // Формирует полный бинарный пакет без DELETED.
    @Transactional(readOnly = true)
    public BinarySignaturePackage exportFull() {
        List<MalwareSignature> signatures = signatureRepository.findAllByStatusOrderByUpdatedAtAsc(SignatureStatus.ACTUAL);
        return buildPackage(signatures, BinaryExportType.FULL, SINCE_NOT_USED);
    }

    // Формирует бинарный инкремент по updatedAt > since.
    @Transactional(readOnly = true)
    public BinarySignaturePackage exportIncrement(Instant since) {
        if (since == null) {
            throw new IllegalArgumentException("since is required");
        }
        List<MalwareSignature> signatures = signatureRepository.findAllByUpdatedAtAfterOrderByUpdatedAtAsc(since);
        return buildPackage(signatures, BinaryExportType.INCREMENT, since.toEpochMilli());
    }

    // Формирует бинарный пакет по списку UUID.
    @Transactional(readOnly = true)
    public BinarySignaturePackage exportByIds(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            throw new IllegalArgumentException("ids must not be empty");
        }
        List<MalwareSignature> signatures = signatureRepository.findAllByIdIn(ids);
        Map<UUID, Integer> order = new HashMap<>();
        for (int i = 0; i < ids.size(); i++) {
            order.putIfAbsent(ids.get(i), i);
        }
        signatures.sort(Comparator.comparingInt(signature -> order.getOrDefault(signature.getId(), Integer.MAX_VALUE)));
        return buildPackage(signatures, BinaryExportType.BY_IDS, SINCE_NOT_USED);
    }

    // Собирает data.bin и manifest.bin для ответа.
    private BinarySignaturePackage buildPackage(List<MalwareSignature> signatures, BinaryExportType exportType, long sinceEpochMillis) {
        List<DataRecordFrame> frames = buildDataFrames(signatures);
        byte[] dataBytes = buildDataBinary(frames);
        byte[] dataSha256 = sha256(dataBytes);
        byte[] manifestBytes = buildManifestBinary(frames, exportType, sinceEpochMillis, dataSha256);
        return new BinarySignaturePackage(manifestBytes, dataBytes);
    }

    // Готовит бинарные записи data.bin с расчетом offset/length.
    private List<DataRecordFrame> buildDataFrames(List<MalwareSignature> signatures) {
        List<DataRecordFrame> frames = new ArrayList<>(signatures.size());
        long offset = 0L;
        for (MalwareSignature signature : signatures) {
            byte[] recordBinary = serializeDataRecord(signature);
            byte[] recordSignatureBytes = decodeRecordSignature(signature.getDigitalSignatureBase64());
            frames.add(new DataRecordFrame(signature, offset, recordBinary, recordSignatureBytes));
            offset += recordBinary.length;
        }
        return frames;
    }

    // Сериализует data.bin заголовок и массив записей.
    private byte[] buildDataBinary(List<DataRecordFrame> frames) {
        BinaryProtocolWriter writer = new BinaryProtocolWriter();
        writer.writeUtf8(DATA_MAGIC);
        writer.writeUInt16(FORMAT_VERSION);
        writer.writeUInt32(frames.size());
        for (DataRecordFrame frame : frames) {
            writer.writeRaw(frame.dataBytes());
        }
        return writer.toByteArray();
    }

    // Сериализует и подписывает manifest.bin.
    private byte[] buildManifestBinary(List<DataRecordFrame> frames, BinaryExportType exportType,
            long sinceEpochMillis, byte[] dataSha256) {
        if (dataSha256.length != 32) {
            throw new IllegalStateException("dataSha256 must contain 32 bytes");
        }

        BinaryProtocolWriter unsignedWriter = new BinaryProtocolWriter();
        unsignedWriter.writeUtf8(MANIFEST_MAGIC);
        unsignedWriter.writeUInt16(FORMAT_VERSION);
        unsignedWriter.writeUInt8(exportType.code());
        unsignedWriter.writeInt64(Instant.now().toEpochMilli());
        unsignedWriter.writeInt64(sinceEpochMillis);
        unsignedWriter.writeUInt32(frames.size());
        unsignedWriter.writeRaw(dataSha256);

        for (DataRecordFrame frame : frames) {
            MalwareSignature signature = frame.signature();
            unsignedWriter.writeUuid(signature.getId());
            unsignedWriter.writeUInt8(statusCode(signature.getStatus()));
            unsignedWriter.writeInt64(signature.getUpdatedAt().toEpochMilli());
            unsignedWriter.writeUInt64(frame.dataOffset());
            unsignedWriter.writeUInt32(frame.dataBytes().length);
            unsignedWriter.writeUInt32(frame.recordSignatureBytes().length);
            unsignedWriter.writeRaw(frame.recordSignatureBytes());
        }

        byte[] unsignedManifest = unsignedWriter.toByteArray();
        byte[] manifestSignature = signatureService.sign(unsignedManifest);

        BinaryProtocolWriter signedWriter = new BinaryProtocolWriter();
        signedWriter.writeRaw(unsignedManifest);
        signedWriter.writeUInt32(manifestSignature.length);
        signedWriter.writeRaw(manifestSignature);
        return signedWriter.toByteArray();
    }

    // Сериализует одну запись data.bin.
    private byte[] serializeDataRecord(MalwareSignature signature) {
        long remainderLength = requireNonNegative(signature.getRemainderLength(), "remainderLength");
        long offsetStart = requireNonNegative(signature.getOffsetStart(), "offsetStart");
        long offsetEnd = requireNonNegative(signature.getOffsetEnd(), "offsetEnd");
        if (offsetEnd < offsetStart) {
            throw new IllegalStateException("offsetEnd must be greater or equal offsetStart");
        }

        BinaryProtocolWriter writer = new BinaryProtocolWriter();
        writer.writeUtf8(signature.getThreatName());
        writer.writeByteArray(BinaryProtocolWriter.decodeHex(signature.getFirstBytesHex(), "firstBytesHex"));
        writer.writeByteArray(BinaryProtocolWriter.decodeHex(signature.getRemainderHashHex(), "remainderHashHex"));
        writer.writeUInt64(remainderLength);
        writer.writeUInt8(binaryFileTypeCodec.toCode(signature.getFileType()));
        writer.writeUInt64(offsetStart);
        writer.writeUInt64(offsetEnd);
        return writer.toByteArray();
    }

    // Преобразует base64-подпись записи в byte[].
    private byte[] decodeRecordSignature(String digitalSignatureBase64) {
        try {
            return Base64.getDecoder().decode(digitalSignatureBase64);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("digitalSignatureBase64 is not valid Base64", ex);
        }
    }

    // Возвращает код статуса сигнатуры для манифеста.
    private int statusCode(SignatureStatus status) {
        return switch (status) {
            case ACTUAL -> STATUS_ACTUAL;
            case DELETED -> STATUS_DELETED;
        };
    }

    // Проверяет, что значение не null и не отрицательное.
    private long requireNonNegative(Long value, String fieldName) {
        if (value == null || value < 0) {
            throw new IllegalStateException(fieldName + " must be non-negative");
        }
        return value;
    }

    // Считает SHA-256 от массива байт.
    private byte[] sha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(data);
        } catch (Exception ex) {
            throw new IllegalStateException("Could not build SHA-256", ex);
        }
    }

    private record DataRecordFrame(
            MalwareSignature signature,
            long dataOffset,
            byte[] dataBytes,
            byte[] recordSignatureBytes) {
    }
}
