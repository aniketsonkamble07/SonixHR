package com.sonixhr.util;

import java.util.Locale;
import java.util.Set;

public class CountryUtils {

    private static final Set<String> ISO_COUNTRIES = Set.of(Locale.getISOCountries());

    /**
     * Normalizes and validates the country input.
     * Default value is "IN" (India) if empty or null.
     * Checks if it is a valid 2-letter ISO code or a matching display country name in English.
     */
    public static String normalizeAndValidateCountry(String country) {
        if (country == null || country.trim().isEmpty()) {
            return "IN";
        }
        
        String clean = country.trim().toUpperCase();
        
        if ("INDIA".equals(clean)) {
            return "IN";
        }
        
        if (ISO_COUNTRIES.contains(clean)) {
            return clean;
        }
        
        // Search by English display country name
        for (String code : ISO_COUNTRIES) {
            Locale locale = new Locale("", code);
            if (locale.getDisplayCountry(Locale.ENGLISH).toUpperCase().equals(clean)) {
                return code;
            }
        }
        
        throw new com.sonixhr.exceptions.ValidationException("country", "Invalid country name or ISO code: " + country);
    }
}
