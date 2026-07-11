package com.sonixhr.enums;

public enum Gender {
    MALE("male", "Male", "primary"),
    FEMALE("female", "Female", "primary"),
    OTHER("other", "Other", "secondary"),
    PREFER_NOT_TO_SAY("prefer_not_to_say", "Prefer Not to Say", "secondary");

    private final String code;
    private final String displayName;
    private final String badgeColor;

    Gender(String code, String displayName, String badgeColor) {
        this.code = code;
        this.displayName = displayName;
        this.badgeColor = badgeColor;
    }

    public String getCode() {
        return code;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getBadgeColor() {
        return badgeColor;
    }

    public static Gender fromCode(String code) {
        for (Gender gender : values()) {
            if (gender.code.equals(code)) {
                return gender;
            }
        }
        return PREFER_NOT_TO_SAY;
    }

    @com.fasterxml.jackson.annotation.JsonCreator
    public static Gender fromString(String value) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        String clean = value.trim();
        for (Gender gender : values()) {
            if (gender.name().equalsIgnoreCase(clean) ||
                gender.code.equalsIgnoreCase(clean) ||
                gender.displayName.equalsIgnoreCase(clean)) {
                return gender;
            }
        }
        return PREFER_NOT_TO_SAY;
    }
}