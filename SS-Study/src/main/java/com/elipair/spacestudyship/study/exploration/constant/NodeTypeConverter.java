package com.elipair.spacestudyship.study.exploration.constant;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter
public class NodeTypeConverter implements AttributeConverter<NodeType, String> {

    @Override
    public String convertToDatabaseColumn(NodeType attribute) {
        return attribute == null ? null : attribute.value();
    }

    @Override
    public NodeType convertToEntityAttribute(String dbData) {
        return dbData == null ? null : NodeType.from(dbData);
    }
}
