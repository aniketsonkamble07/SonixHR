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
        if ("OR".equals(upper)) { // Compatibility with old Odisha code OR
            return ODISHA;
        }
        IndianState state = BY_CODE.get(upper.replace(" ", "_"));
        if (state != null) {
            return state;
        }
        for (IndianState s : values()) {
            if (s.getDisplayName().equalsIgnoreCase(upper)) {
                return s;
            }
        }
        // Also support lookup by 2-letter abbreviation
        switch (upper) {
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
        return null;
    }

    public static IndianState resolveFromCity(String text) {
        if (text == null) return null;
        String upper = text.trim().toUpperCase();
        
        if (upper.contains("BANGALORE") || upper.contains("BENGALURU")) return KARNATAKA;
        if (upper.contains("MUMBAI") || upper.contains("PUNE") || upper.contains("NAGPUR") || upper.contains("THANE") || upper.contains("NASHIK") || upper.contains("AURANGABAD")) return MAHARASHTRA;
        if (upper.contains("HYDERABAD") || upper.contains("SECUNDERABAD") || upper.contains("WARANGAL")) return TELANGANA;
        if (upper.contains("CHENNAI") || upper.contains("COIMBATORE") || upper.contains("MADURAI") || upper.contains("SALEM")) return TAMIL_NADU;
        if (upper.contains("DELHI") || upper.contains("NEW DELHI")) return DELHI;
        if (upper.contains("KOLKATA") || upper.contains("CALCUTTA") || upper.contains("HOWRAH")) return WEST_BENGAL;
        if (upper.contains("AHMEDABAD") || upper.contains("SURAT") || upper.contains("VADODARA") || upper.contains("RAJKOT")) return GUJARAT;
        if (upper.contains("VISAKHAPATNAM") || upper.contains("VIJAYAWADA") || upper.contains("GUNTUR") || upper.contains("TIRUPATI")) return ANDHRA_PRADESH;
        if (upper.contains("INDORE") || upper.contains("BHOPAL") || upper.contains("JABALPUR")) return MADHYA_PRADESH;
        if (upper.contains("THIRUVANANTHAPURAM") || upper.contains("TRIVANDRUM") || upper.contains("KOCHI") || upper.contains("COCHIN") || upper.contains("KOZHIKODE")) return KERALA;
        if (upper.contains("BHUBANESWAR") || upper.contains("CUTTACK")) return ODISHA;
        if (upper.contains("RAIPUR") || upper.contains("BILASPUR")) return CHHATTISGARH;
        if (upper.contains("PATNA")) return BIHAR;
        if (upper.contains("RANCHI") || upper.contains("JAMSHEDPUR")) return JHARKHAND;
        if (upper.contains("LUCKNOW") || upper.contains("KANPUR") || upper.contains("NOIDA") || upper.contains("GHAZIABAD") || upper.contains("AGRA") || upper.contains("VARANASI")) return UTTAR_PRADESH;
        if (upper.contains("DEHRADUN")) return UTTARAKHAND;
        if (upper.contains("CHANDIGARH")) return CHANDIGARH;
        if (upper.contains("JAIPUR") || upper.contains("JODHPUR") || upper.contains("UDAIPUR")) return RAJASTHAN;
        if (upper.contains("AMRITSAR") || upper.contains("LUDHIANA")) return PUNJAB;
        if (upper.contains("GURUGRAM") || upper.contains("GURGAON") || upper.contains("FARIDABAD")) return HARYANA;
        if (upper.contains("GUWAHATI")) return ASSAM;
        if (upper.contains("PANAJI")) return GOA;
        
        return null;
    }
}
