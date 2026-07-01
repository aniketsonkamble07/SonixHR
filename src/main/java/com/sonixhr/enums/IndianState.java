package com.sonixhr.enums;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@SuppressWarnings("null")
public enum IndianState {
    ANDHRA_PRADESH("Andhra Pradesh"),
    ARUNACHAL_PRADESH("Arunachal Pradesh"),
    ASSAM("Assam"),
    BIHAR("Bihar"),
    CHHATTISGARH("Chhattisgarh"),
    GOA("Goa"),
    GUJARAT("Gujarat"),
    HARYANA("Haryana"),
    HIMACHAL_PRADESH("Himachal Pradesh"),
    JHARKHAND("Jharkhand"),
    KARNATAKA("Karnataka"),
    KERALA("Kerala"),
    MADHYA_PRADESH("Madhya Pradesh"),
    MAHARASHTRA("Maharashtra"),
    MANIPUR("Manipur"),
    MEGHALAYA("Meghalaya"),
    MIZORAM("Mizoram"),
    NAGALAND("Nagaland"),
    ODISHA("Odisha"),
    PUNJAB("Punjab"),
    RAJASTHAN("Rajasthan"),
    SIKKIM("Sikkim"),
    TAMIL_NADU("Tamil Nadu"),
    TELANGANA("Telangana"),
    TRIPURA("Tripura"),
    UTTAR_PRADESH("Uttar Pradesh"),
    UTTARAKHAND("Uttarakhand"),
    WEST_BENGAL("West Bengal"),
    DELHI("Delhi"),
    ANDAMAN_AND_NICOBAR_ISLANDS("Andaman and Nicobar Islands"),
    CHANDIGARH("Chandigarh"),
    DADRA_AND_NAGAR_HAVELI_AND_DAMAN_AND_DIU("Dadra and Nagar Haveli and Daman and Diu"),
    JAMMU_AND_KASHMIR("Jammu and Kashmir"),
    LADAKH("Ladakh"),
    LAKSHADWEEP("Lakshadweep"),
    PUDUCHERRY("Puducherry");

    private final String displayName;

    IndianState(String displayName) {
        this.displayName = displayName;
    }

    @JsonValue
    public String getDisplayName() {
        return displayName;
    }

    private static final Map<String, IndianState> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toMap(Enum::name, Function.identity()));

    @JsonCreator
    public static IndianState fromCode(String code) {
        if (code == null || code.trim().isEmpty())
            return null;
        
        String upper = code.trim().toUpperCase();
        String clean = upper.replaceAll("[\\s-_]", "");
        
        if ("OR".equals(clean)) { // Compatibility with old Odisha code OR
            return ODISHA;
        }

        // Direct enum name lookup
        String standardName = upper.replace(" ", "_").replace("-", "_");
        IndianState directMatch = BY_CODE.get(standardName);
        if (directMatch != null) {
            return directMatch;
        }

        // Match against enum name without underscores
        for (IndianState s : values()) {
            String enumClean = s.name().replace("_", "");
            if (enumClean.equals(clean)) {
                return s;
            }
        }

        // Match against display name without spaces, hyphens, or underscores
        for (IndianState s : values()) {
            String displayClean = s.getDisplayName().replaceAll("[\\s-_]", "").toUpperCase();
            if (displayClean.equals(clean)) {
                return s;
            }
        }

        // Also support lookup by 2-letter abbreviation
        switch (clean) {
            case "AP": return ANDHRA_PRADESH;
            case "AR": return ARUNACHAL_PRADESH;
            case "AS": return ASSAM;
            case "BR": return BIHAR;
            case "CG": return CHHATTISGARH;
            case "GA": return GOA;
            case "GJ": return GUJARAT;
            case "HR": return HARYANA;
            case "HP": return HIMACHAL_PRADESH;
            case "JH": return JHARKHAND;
            case "KA": return KARNATAKA;
            case "KL": return KERALA;
            case "MP": return MADHYA_PRADESH;
            case "MH": return MAHARASHTRA;
            case "MN": return MANIPUR;
            case "ML": return MEGHALAYA;
            case "MZ": return MIZORAM;
            case "NL": return NAGALAND;
            case "OD": return ODISHA;
            case "PB": return PUNJAB;
            case "RJ": return RAJASTHAN;
            case "SK": return SIKKIM;
            case "TN": return TAMIL_NADU;
            case "TS": return TELANGANA;
            case "TR": return TRIPURA;
            case "UP": return UTTAR_PRADESH;
            case "UK": return UTTARAKHAND;
            case "WB": return WEST_BENGAL;
            case "DL": return DELHI;
            case "AN": return ANDAMAN_AND_NICOBAR_ISLANDS;
            case "CH": return CHANDIGARH;
            case "DN": return DADRA_AND_NAGAR_HAVELI_AND_DAMAN_AND_DIU;
            case "JK": return JAMMU_AND_KASHMIR;
            case "LA": return LADAKH;
            case "LD": return LAKSHADWEEP;
            case "PY": return PUDUCHERRY;
        }

        throw new IllegalArgumentException("Invalid Indian state: " + code);
    }
}
