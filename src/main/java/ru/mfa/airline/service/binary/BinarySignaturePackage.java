package ru.mfa.airline.service.binary;

public record BinarySignaturePackage(byte[] manifestBytes, byte[] dataBytes) {
}
