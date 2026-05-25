package com.sonixhr.enums;



public enum BloodGroup {
    A_POSITIVE("A+", "A+", "success"),
    A_NEGATIVE("A-", "A-", "warning"),
    B_POSITIVE("B+", "B+", "success"),
    B_NEGATIVE("B-", "B-", "warning"),
    O_POSITIVE("O+", "O+", "success"),
    O_NEGATIVE("O-", "O-", "danger"),
    AB_POSITIVE("AB+", "AB+", "primary"),
    AB_NEGATIVE("AB-", "AB-", "info");

    private final String code;
    private final String displayName;
    private final String badgeColor;

    BloodGroup(String code, String displayName, String badgeColor) {
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

    public static BloodGroup fromCode(String code) {
        for (BloodGroup bg : values()) {
            if (bg.code.equals(code)) {
                return bg;
            }
        }
        return O_POSITIVE;
    }
}