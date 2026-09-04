package com.phuang.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.StructuredOutputConverter;

import java.util.List;

public class CustomListOutputConverter<T> implements StructuredOutputConverter<List<T>> {

    private final Class<T> elementType;

    private final ObjectMapper objectMapper;

    private final BeanOutputConverter<T> elementConverter;

    public CustomListOutputConverter(Class<T> elementType) {
        this(elementType, new ObjectMapper());
    }

    public CustomListOutputConverter(Class<T> elementType, ObjectMapper objectMapper) {
        this.elementType = elementType;
        this.objectMapper = objectMapper;
        this.elementConverter = new BeanOutputConverter<>(elementType, objectMapper);
    }

    @Override
    public List<T> convert(String text) {
        try {
            return objectMapper.readValue(extractJsonArray(text),
                    objectMapper.getTypeFactory().constructCollectionType(List.class, elementType));
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("无法将模型输出转换为 Bean 集合: " + text, e);
        }
    }

    @Override
    public String getFormat() {
        return """
                请将响应结果输出为合法的 JSON 数组，不要使用 Markdown 代码块，也不要添加额外说明。
                数组中每个元素都必须符合以下格式要求：
                %s
                """.formatted(elementConverter.getFormat());
    }

    private String extractJsonArray(String text) {
        String content = text == null ? "" : text.trim();
        if (content.startsWith("```")) {
            content = content.replaceFirst("^```(?:json)?\\s*", "").replaceFirst("\\s*```$", "").trim();
        }
        int start = content.indexOf('[');
        int end = content.lastIndexOf(']');
        if (start >= 0 && end >= start) {
            return content.substring(start, end + 1);
        }
        return content;
    }
}
