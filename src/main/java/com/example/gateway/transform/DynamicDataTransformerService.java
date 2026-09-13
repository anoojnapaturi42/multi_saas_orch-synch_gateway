package com.example.gateway.transform;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import com.jayway.jsonpath.InvalidPathException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Applies database-defined transformation expressions without requiring a Java
 * class for each SaaS provider or workflow.
 *
 * <p>Schema metadata beginning with an underscore is reserved. Supported
 * metadata is {@code _inputFormat: "JSON"|"XML"}; XML input can be supplied
 * as a textual JsonNode and is converted to a Jackson tree before JsonPath
 * evaluation. Every mapping failure is reported in {@code _transformationErrors}
 * and does not prevent other fields from being transformed.</p>
 */
@Service
public class DynamicDataTransformerService {
    public static final String INPUT_FORMAT = "_inputFormat";
    public static final String ERRORS = "_transformationErrors";

    private final ObjectMapper objectMapper;
    private final XmlMapper xmlMapper;

    public DynamicDataTransformerService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        this.xmlMapper = new XmlMapper();
    }

    /**
     * Transforms JSON or XML input using a JSON object schema.
     *
     * <pre>
     * {
     *   "email": "$.user.work_email",
     *   "source": "CONST:WORKDAY",
     *   "fullName": "CONCAT($.first_name, ' ', $.last_name)",
     *   "isVip": "EQUALS($.account.tier, 'ENTERPRISE')"
     * }
     * </pre>
     */
    public ObjectNode transform(JsonNode incomingData, JsonNode transformationSchema) {
        ObjectNode result = objectMapper.createObjectNode();
        ArrayNode errors = result.putArray(ERRORS);

        if (transformationSchema == null || !transformationSchema.isObject()) {
            addError(errors, "$", "Schema must be a JSON object");
            return result;
        }

        JsonNode normalizedInput = normalizeInput(incomingData, transformationSchema, errors);
        DocumentContext document;
        try {
            document = JsonPath.parse(objectMapper.writeValueAsString(normalizedInput));
        } catch (JsonProcessingException | RuntimeException ex) {
            addError(errors, "$", "Malformed input JSON: " + safeMessage(ex));
            return result;
        }

        transformationSchema.fields().forEachRemaining(entry -> {
            String outputField = entry.getKey();
            if (outputField.startsWith("_")) return;
            try {
                result.set(outputField, evaluate(entry.getValue(), document));
            } catch (TransformationEvaluationException ex) {
                addError(errors, outputField, ex.getMessage());
            } catch (RuntimeException ex) {
                addError(errors, outputField, "Transformation failed: " + safeMessage(ex));
            }
        });
        if (errors.isEmpty()) result.remove(ERRORS);
        return result;
    }

    private JsonNode normalizeInput(JsonNode incomingData, JsonNode schema, ArrayNode errors) {
        if (incomingData == null || incomingData.isNull()) {
            addError(errors, "$", "Incoming data is missing");
            return NullNode.getInstance();
        }
        String inputFormat = schema.path(INPUT_FORMAT).asText("JSON").toUpperCase(Locale.ROOT);
        if (!"XML".equals(inputFormat)) return incomingData;
        if (!incomingData.isTextual()) {
            addError(errors, "$", "XML input must be supplied as a textual JsonNode");
            return NullNode.getInstance();
        }
        try {
            return xmlMapper.readTree(incomingData.textValue());
        } catch (JsonProcessingException | RuntimeException ex) {
            addError(errors, "$", "Malformed XML: " + safeMessage(ex));
            return NullNode.getInstance();
        }
    }

    private JsonNode evaluate(JsonNode rule, DocumentContext document) {
        if (rule == null || rule.isNull()) return NullNode.getInstance();
        if (!rule.isTextual()) return rule.deepCopy();
        String expression = rule.textValue().trim();
        if (expression.startsWith("CONST:")) return TextNode.valueOf(expression.substring(6));
        if (expression.startsWith("CONCAT(") && expression.endsWith(")")) {
            return TextNode.valueOf(parseArguments(expression, "CONCAT", 2).stream()
                    .map(argument -> resolveArgument(argument, document).asText())
                    .reduce("", String::concat));
        }
        if (expression.startsWith("EQUALS(") && expression.endsWith(")")) {
            List<String> args = parseArguments(expression, "EQUALS", 2);
            return objectMapper.getNodeFactory().booleanNode(
                    valuesEqual(resolveArgument(args.get(0), document), resolveArgument(args.get(1), document)));
        }
        if (expression.startsWith("XML_TO_JSON(") && expression.endsWith(")")) {
            // XML_TO_JSON is intentionally an explicit alias for JsonPath over
            // the normalized XML tree; it makes XML rules self-documenting.
            String path = expression.substring("XML_TO_JSON(".length(), expression.length() - 1).trim();
            return readJsonPath(path, document);
        }
        if (expression.startsWith("$")) return readJsonPath(expression, document);
        return TextNode.valueOf(expression);
    }

    private JsonNode resolveArgument(String argument, DocumentContext document) {
        String value = argument.trim();
        if (isQuoted(value)) return TextNode.valueOf(unquote(value));
        if (value.startsWith("CONST:")) return TextNode.valueOf(value.substring(6));
        if (value.startsWith("$")) return readJsonPath(value, document);
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) return objectMapper.getNodeFactory().booleanNode(Boolean.parseBoolean(value));
        try { return objectMapper.readTree(value); }
        catch (JsonProcessingException ex) { return TextNode.valueOf(value); }
    }

    private JsonNode readJsonPath(String path, DocumentContext document) {
        try {
            Object value = document.read(path);
            if (value == null) return NullNode.getInstance();
            return objectMapper.valueToTree(value);
        } catch (PathNotFoundException ex) {
            throw new TransformationEvaluationException("Missing JsonPath value: " + path, ex);
        } catch (InvalidPathException | IllegalArgumentException ex) {
            throw new TransformationEvaluationException("Invalid JsonPath target: " + path, ex);
        }
    }

    private List<String> parseArguments(String expression, String function, int expected) {
        String body = expression.substring(function.length() + 1, expression.length() - 1);
        List<String> arguments = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        char quote = 0;
        int depth = 0;
        for (char c : body.toCharArray()) {
            if ((c == '\'' || c == '"') && (quote == 0 || quote == c)) quote = quote == 0 ? c : 0;
            if (quote == 0 && c == '(') depth++;
            if (quote == 0 && c == ')') depth--;
            if (c == ',' && quote == 0 && depth == 0) { arguments.add(current.toString().trim()); current.setLength(0); }
            else current.append(c);
        }
        arguments.add(current.toString().trim());
        if (arguments.size() != expected || arguments.stream().anyMatch(String::isBlank)) {
            throw new TransformationEvaluationException(function + " requires exactly " + expected + " arguments", null);
        }
        return arguments;
    }

    private boolean valuesEqual(JsonNode left, JsonNode right) {
        if (left.isNumber() && right.isTextual()) return new BigDecimal(left.asText()).compareTo(new BigDecimal(right.textValue())) == 0;
        if (left.isTextual() && right.isNumber()) return new BigDecimal(left.textValue()).compareTo(new BigDecimal(right.asText())) == 0;
        return Objects.equals(left, right) || left.asText().equals(right.asText());
    }

    private boolean isQuoted(String value) { return value.length() >= 2 && ((value.startsWith("'") && value.endsWith("'")) || (value.startsWith("\"") && value.endsWith("\""))); }
    private String unquote(String value) { return value.substring(1, value.length() - 1).replace("\\'", "'").replace("\\\"", "\""); }
    private void addError(ArrayNode errors, String field, String message) { errors.addObject().put("field", field).put("message", message); }
    private String safeMessage(Exception ex) { return ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage(); }

    private static final class TransformationEvaluationException extends RuntimeException {
        private TransformationEvaluationException(String message, Throwable cause) { super(message, cause); }
    }
}
