package io.github.dmitriyiliyov.idempify.jackson;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import io.github.dmitriyiliyov.idempify.core.config.BodyCanonicalizerConfig;
import io.github.dmitriyiliyov.idempify.core.fingerprint.BodyCanonicalizer;
import io.github.dmitriyiliyov.idempify.core.fingerprint.CanonicalizeStrategy;

import java.io.IOException;
import java.util.*;

/**
 * Canonicalizes a JSON body by walking the parsed tree and writing it back in a stable form.
 * <p>
 * Under {@link CanonicalizeStrategy#LEXICOGRAPHICAL} object keys are sorted with {@link String#compareTo},
 * which orders by UTF-16 code units - the same ordering RFC 8785 (JSON Canonicalization Scheme) prescribes,
 * and one that does not depend on the machine's locale. Array order is <em>never</em> touched: the position
 * of an element is part of its meaning.
 * <p>
 * Fields are selected by {@link BodyCanonicalizerConfig#getIncludedFields()} (default-deny) or pruned by
 * {@link BodyCanonicalizerConfig#getExcludedFields()} (default-allow); the config forbids using both. Paths
 * are dot-separated and carry no array indices, so {@code items.sku} addresses {@code sku} in <em>every</em>
 * element of {@code items}.
 * <p>
 * Numbers are written as they were parsed rather than normalized to the ECMAScript form, so {@code 1.0} and
 * {@code 1} still fingerprint differently. That is a deliberate deviation from RFC 8785 towards strictness.
 */
public class JsonBodyCanonicalizer implements BodyCanonicalizer {

    private static final String MISSING_FIELD = "\0missing";
    private static final String ROOT_PATH = "";
    private static final char PATH_SEPARATOR = '.';
    private static final char FIELD_SEPARATOR = ',';
    private static final char NAME_VALUE_SEPARATOR = ':';

    private final BodyCanonicalizerConfig config;
    private final ObjectMapper objectMapper;

    public JsonBodyCanonicalizer(BodyCanonicalizerConfig config, ObjectMapper objectMapper) {
        this.config = Objects.requireNonNull(config, "config cannot be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper cannot be null");
    }

    @Override
    public String canonicalize(byte [] body) {
        JsonNode root = readTree(body);
        StringBuilder sb = new StringBuilder();

        Set<String> includedFields = config.getIncludedFields();
        if (includedFields.isEmpty()) {
            write(root, ROOT_PATH, sb);
        } else {
            writeWithIncluded(root, includedFields, sb);
        }

        return sb.toString();
    }

    private String writeWithIncluded(JsonNode root, Set<String> includedFields, StringBuilder sb) {
        List<String> paths = new ArrayList<>(includedFields);
        if (CanonicalizeStrategy.LEXICOGRAPHICAL.equals(config.getCanonicalizeStrategy())) {
            paths.sort(Comparator.naturalOrder());
        }

        for (String path : paths) {
            if (!sb.isEmpty()) {
                sb.append(FIELD_SEPARATOR);
            }
            sb.append(TextNode.valueOf(path)).append(NAME_VALUE_SEPARATOR);
            write(resolve(root, path), path, sb);
        }

        return sb.toString();
    }

    private JsonNode readTree(byte [] body) {
        try {
            return objectMapper.readTree(body);
        } catch (IOException e) {
            throw new IllegalArgumentException("Request body is not valid JSON; cannot canonicalize it", e);
        }
    }

    private JsonNode resolve(JsonNode root, String path) {
        JsonNode current = root;
        int start = 0;

        while (start <= path.length()) {
            int end = path.indexOf(PATH_SEPARATOR, start);
            if (end < 0) {
                end = path.length();
            }
            current = current.path(path.substring(start, end));
            if (current.isMissingNode()) {
                return current;
            }
            start = end + 1;
        }

        return current;
    }

    private void write(JsonNode node, String path, StringBuilder sb) {
        if (node == null || node.isMissingNode()) {
            sb.append(MISSING_FIELD);
            return;
        }

        if (node.isObject()) {
            writeObject(node, path, sb);
        } else if (node.isArray()) {
            writeArray(node, path, sb);
        } else {
            sb.append(node);
        }
    }

    private void writeObject(JsonNode node, String path, StringBuilder sb) {
        List<String> names = new ArrayList<>(node.size());
        node.fieldNames().forEachRemaining(names::add);

        if (CanonicalizeStrategy.LEXICOGRAPHICAL.equals(config.getCanonicalizeStrategy())) {
            names.sort(Comparator.naturalOrder());
        }

        Set<String> excludedFields = config.getExcludedFields();

        sb.append('{');
        boolean first = true;
        for (String name : names) {
            String fieldPath = childPath(path, name);
            if (excludedFields.contains(fieldPath)) {
                continue;
            }
            if (!first) {
                sb.append(FIELD_SEPARATOR);
            }
            first = false;
            sb.append(TextNode.valueOf(name)).append(NAME_VALUE_SEPARATOR);
            write(node.get(name), fieldPath, sb);
        }
        sb.append('}');
    }

    private void writeArray(JsonNode node, String path, StringBuilder sb) {
        sb.append('[');
        for (int i = 0; i < node.size(); i++) {
            if (i > 0) {
                sb.append(FIELD_SEPARATOR);
            }
            write(node.get(i), path, sb);
        }
        sb.append(']');
    }

    private String childPath(String path, String name) {
        return path.isEmpty() ? name : path + PATH_SEPARATOR + name;
    }
}
