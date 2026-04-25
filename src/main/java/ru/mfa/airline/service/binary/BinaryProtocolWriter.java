package ru.mfa.airline.service.binary;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class BinaryProtocolWriter {
    private final ByteArrayOutputStream stream = new ByteArrayOutputStream();

    // Пишет uint8 в BigEndian.
    public BinaryProtocolWriter writeUInt8(int value) {
        if (value < 0 || value > 0xFF) {
            throw new IllegalArgumentException("uint8 is out of range: " + value);
        }
        stream.write(value);
        return this;
    }

    // Пишет uint16 в BigEndian.
    public BinaryProtocolWriter writeUInt16(int value) {
        if (value < 0 || value > 0xFFFF) {
            throw new IllegalArgumentException("uint16 is out of range: " + value);
        }
        stream.write((value >>> 8) & 0xFF);
        stream.write(value & 0xFF);
        return this;
    }

    // Пишет uint32 в BigEndian.
    public BinaryProtocolWriter writeUInt32(long value) {
        if (value < 0 || value > 0xFFFF_FFFFL) {
            throw new IllegalArgumentException("uint32 is out of range: " + value);
        }
        stream.write((int) ((value >>> 24) & 0xFF));
        stream.write((int) ((value >>> 16) & 0xFF));
        stream.write((int) ((value >>> 8) & 0xFF));
        stream.write((int) (value & 0xFF));
        return this;
    }

    // Пишет uint64 в BigEndian.
    public BinaryProtocolWriter writeUInt64(long value) {
        if (value < 0) {
            throw new IllegalArgumentException("uint64 is out of range: " + value);
        }
        return writeInt64(value);
    }

    // Пишет int64 в BigEndian.
    public BinaryProtocolWriter writeInt64(long value) {
        stream.write((int) ((value >>> 56) & 0xFF));
        stream.write((int) ((value >>> 48) & 0xFF));
        stream.write((int) ((value >>> 40) & 0xFF));
        stream.write((int) ((value >>> 32) & 0xFF));
        stream.write((int) ((value >>> 24) & 0xFF));
        stream.write((int) ((value >>> 16) & 0xFF));
        stream.write((int) ((value >>> 8) & 0xFF));
        stream.write((int) (value & 0xFF));
        return this;
    }

    // Пишет UUID как два int64 в BigEndian.
    public BinaryProtocolWriter writeUuid(UUID value) {
        return writeInt64(value.getMostSignificantBits()).writeInt64(value.getLeastSignificantBits());
    }

    // Пишет UTF-8 строку как uint32 длина + байты.
    public BinaryProtocolWriter writeUtf8(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        return writeByteArray(bytes);
    }

    // Пишет byte[] как uint32 длина + байты.
    public BinaryProtocolWriter writeByteArray(byte[] value) {
        writeUInt32(value.length);
        stream.writeBytes(value);
        return this;
    }

    // Пишет массив байт без префикса длины.
    public BinaryProtocolWriter writeRaw(byte[] value) {
        stream.writeBytes(value);
        return this;
    }

    // Возвращает длину текущего буфера.
    public int size() {
        return stream.size();
    }

    // Возвращает накопленный массив байт.
    public byte[] toByteArray() {
        return stream.toByteArray();
    }

    // Декодирует hex-строку в сырые байты.
    public static byte[] decodeHex(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(fieldName + " must not be blank");
        }
        if ((value.length() & 1) != 0) {
            throw new IllegalStateException(fieldName + " must have even length");
        }
        byte[] out = new byte[value.length() / 2];
        for (int i = 0; i < value.length(); i += 2) {
            int hi = Character.digit(value.charAt(i), 16);
            int lo = Character.digit(value.charAt(i + 1), 16);
            if (hi < 0 || lo < 0) {
                throw new IllegalStateException(fieldName + " contains non-hex characters");
            }
            out[i / 2] = (byte) ((hi << 4) + lo);
        }
        return out;
    }
}
