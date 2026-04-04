package ru.mfa.signature;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@Component
public class JsonCanonicalizer {
    private final ObjectMapper objectMapper;

    public JsonCanonicalizer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    // Переводит payload в канонический JSON UTF-8.
    public byte[] canonicalize(Object payload) {
        JsonNode root = objectMapper.valueToTree(payload);
        String canonicalJson = toCanonicalJson(root);
        return canonicalJson.getBytes(StandardCharsets.UTF_8);
    }

    // Собирает канонический JSON без пробелов.
    private String toCanonicalJson(JsonNode node) {
        if (node == null || node.isNull()) {
            return "null";
        }
        if (node.isObject()) {
            return writeObject(node);
        }
        if (node.isArray()) {
            return writeArray(node);
        }
        if (node.isTextual()) {
            return writeString(node.textValue());
        }
        return node.toString();
    }

    // Сортирует поля объекта по имени.
    private String writeObject(JsonNode node) {
        List<String> fieldNames = new ArrayList<>();
        Iterator<String> iterator = node.fieldNames();
        while (iterator.hasNext()) {
            fieldNames.add(iterator.next());
        }
        fieldNames.sort(String::compareTo);

        StringBuilder builder = new StringBuilder();
        builder.append('{');
        boolean first = true;
        for (String fieldName : fieldNames) {
            if (!first) {
                builder.append(',');
            }
            builder.append(writeString(fieldName));
            builder.append(':');
            builder.append(toCanonicalJson(node.get(fieldName)));
            first = false;
        }
        builder.append('}');
        return builder.toString();
    }

    // Сохраняет порядок элементов массива.
    private String writeArray(JsonNode node) {
        StringBuilder builder = new StringBuilder();
        builder.append('[');
        for (int i = 0; i < node.size(); i++) {
            if (i > 0) {
                builder.append(',');
            }
            builder.append(toCanonicalJson(node.get(i)));
        }
        builder.append(']');
        return builder.toString();
    }

    // Экранирует строку по правилам JSON.
    private String writeString(String value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            throw new IllegalStateException("Could not canonicalize string", ex);
        }
    }
}
