package com.sonixhr.security;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class HashingServiceTest {

    private final HashingService hashingService = new HashingService();

    @Test
    public void testHashEmail_Normal() {
        String email = "test.user@example.com";
        String hash1 = hashingService.hashEmail(email);
        assertNotNull(hash1);
        assertNotEquals(email, hash1);
        assertEquals(64, hash1.length()); // SHA-256 is 64 hex characters
    }

    @Test
    public void testHashEmail_CaseInsensitiveAndTrim() {
        String email1 = "  Test.User@Example.Com  ";
        String email2 = "test.user@example.com";
        
        String hash1 = hashingService.hashEmail(email1);
        String hash2 = hashingService.hashEmail(email2);
        
        assertEquals(hash1, hash2);
    }

    @Test
    public void testHashEmail_Null() {
        String hash = hashingService.hashEmail(null);
        assertEquals("", hash);
    }

    @Test
    public void testHashEmail_Empty() {
        String hash = hashingService.hashEmail("");
        assertNotNull(hash);
        assertEquals(64, hash.length());
    }
}
