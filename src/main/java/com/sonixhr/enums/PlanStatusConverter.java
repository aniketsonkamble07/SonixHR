package com.sonixhr.enums;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

@Converter(autoApply = true)
public class PlanStatusConverter implements AttributeConverter<PlanStatus, String> {

    @Override
    public String convertToDatabaseColumn(PlanStatus attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.name();
    }

    @Override
    public PlanStatus convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.trim().isEmpty()) {
            return null;
        }
        try {
            return PlanStatus.valueOf(dbData.toUpperCase().trim());
        } catch (IllegalArgumentException e) {
            try {
                return PlanStatus.fromCode(dbData.trim());
            } catch (Exception ex) {
                return null;
            }
        }
    }
}
