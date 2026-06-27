package com.sonixhr.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class IndianStateConverter implements AttributeConverter<IndianState, String> {

    @Override
    public String convertToDatabaseColumn(IndianState attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.getDisplayName();
    }

    @Override
    public IndianState convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.trim().isEmpty()) {
            return null;
        }
        return IndianState.fromCode(dbData);
    }
}
