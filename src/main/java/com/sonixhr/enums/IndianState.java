package com.sonixhr.enums;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@SuppressWarnings("null")
public enum IndianState {
    AP("Andhra Pradesh"),
    AR("Arunachal Pradesh"),
    AS("Assam"),
    BR("Bihar"),
    CG("Chhattisgarh"),
    GA("Goa"),
    GJ("Gujarat"),
    HR("Haryana"),
    HP("Himachal Pradesh"),
    JH("Jharkhand"),
    KA("Karnataka"),
    KL("Kerala"),
    MP("Madhya Pradesh"),
    MH("Maharashtra"),
    MN("Manipur"),
    ML("Meghalaya"),
    MZ("Mizoram"),
    NL("Nagaland"),
    OD("Odisha"),
    PB("Punjab"),
    RJ("Rajasthan"),
    SK("Sikkim"),
    TN("Tamil Nadu"),
    TS("Telangana"),
    TR("Tripura"),
    UP("Uttar Pradesh"),
    UK("Uttarakhand"),
    WB("West Bengal"),
    DL("Delhi"),
    AN("Andaman and Nicobar Islands"),
    CH("Chandigarh"),
    DN("Dadra and Nagar Haveli and Daman and Diu"),
    JK("Jammu and Kashmir"),
    LA("Ladakh"),
    LD("Lakshadweep"),
    PY("Puducherry");

    private final String displayName;

    IndianState(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    private static final Map<String, IndianState> BY_CODE = Arrays.stream(values())
            .collect(Collectors.toMap(Enum::name, Function.identity()));

    public static IndianState fromCode(String code) {
        if (code == null)
            return null;
        String upper = code.trim().toUpperCase();
        if ("OR".equals(upper)) { // Compatibility with old Odisha code OR
            return OD;
        }
        return BY_CODE.get(upper);
    }

    public static IndianState resolveFromCity(String text) {
        if (text == null) return null;
        String upper = text.trim().toUpperCase();
        
        if (upper.contains("BANGALORE") || upper.contains("BENGALURU")) return KA;
        if (upper.contains("MUMBAI") || upper.contains("PUNE") || upper.contains("NAGPUR") || upper.contains("THANE") || upper.contains("NASHIK") || upper.contains("AURANGABAD")) return MH;
        if (upper.contains("HYDERABAD") || upper.contains("SECUNDERABAD") || upper.contains("WARANGAL")) return TS;
        if (upper.contains("CHENNAI") || upper.contains("COIMBATORE") || upper.contains("MADURAI") || upper.contains("SALEM")) return TN;
        if (upper.contains("DELHI") || upper.contains("NEW DELHI")) return DL;
        if (upper.contains("KOLKATA") || upper.contains("CALCUTTA") || upper.contains("HOWRAH")) return WB;
        if (upper.contains("AHMEDABAD") || upper.contains("SURAT") || upper.contains("VADODARA") || upper.contains("RAJKOT")) return GJ;
        if (upper.contains("VISAKHAPATNAM") || upper.contains("VIJAYAWADA") || upper.contains("GUNTUR") || upper.contains("TIRUPATI")) return AP;
        if (upper.contains("INDORE") || upper.contains("BHOPAL") || upper.contains("JABALPUR")) return MP;
        if (upper.contains("THIRUVANANTHAPURAM") || upper.contains("TRIVANDRUM") || upper.contains("KOCHI") || upper.contains("COCHIN") || upper.contains("KOZHIKODE")) return KL;
        if (upper.contains("BHUBANESWAR") || upper.contains("CUTTACK")) return OD;
        if (upper.contains("RAIPUR") || upper.contains("BILASPUR")) return CG;
        if (upper.contains("PATNA")) return BR;
        if (upper.contains("RANCHI") || upper.contains("JAMSHEDPUR")) return JH;
        if (upper.contains("LUCKNOW") || upper.contains("KANPUR") || upper.contains("NOIDA") || upper.contains("GHAZIABAD") || upper.contains("AGRA") || upper.contains("VARANASI")) return UP;
        if (upper.contains("DEHRADUN")) return UK;
        if (upper.contains("CHANDIGARH")) return CH;
        if (upper.contains("JAIPUR") || upper.contains("JODHPUR") || upper.contains("UDAIPUR")) return RJ;
        if (upper.contains("AMRITSAR") || upper.contains("LUDHIANA")) return PB;
        if (upper.contains("GURUGRAM") || upper.contains("GURGAON") || upper.contains("FARIDABAD")) return HR;
        if (upper.contains("GUWAHATI")) return AS;
        if (upper.contains("PANAJI")) return GA;
        
        return null;
    }
}
