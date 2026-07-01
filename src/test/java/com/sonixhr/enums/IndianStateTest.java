package com.sonixhr.enums;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class IndianStateTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    public void testFromCode() {
        assertEquals(IndianState.MAHARASHTRA, IndianState.fromCode("MH"));
        assertEquals(IndianState.MAHARASHTRA, IndianState.fromCode("mh"));
        assertEquals(IndianState.MAHARASHTRA, IndianState.fromCode("  MH  "));
        assertEquals(IndianState.MAHARASHTRA, IndianState.fromCode("Maharashtra"));
        assertEquals(IndianState.MAHARASHTRA, IndianState.fromCode("maharashtra"));
        assertEquals(IndianState.MAHARASHTRA, IndianState.fromCode("  Maharashtra  "));
        assertEquals(IndianState.ODISHA, IndianState.fromCode("OR")); // Fallback/compatibility

        assertNull(IndianState.fromCode(null));
        assertNull(IndianState.fromCode(""));
        assertNull(IndianState.fromCode("   "));
        assertThrows(IllegalArgumentException.class, () -> IndianState.fromCode("InvalidStateName"));
    }

    @Test
    public void testConverter() {
        IndianStateConverter converter = new IndianStateConverter();

        // Convert to database column (should be the display name)
        assertEquals("Maharashtra", converter.convertToDatabaseColumn(IndianState.MAHARASHTRA));
        assertEquals("Karnataka", converter.convertToDatabaseColumn(IndianState.KARNATAKA));
        assertNull(converter.convertToDatabaseColumn(null));

        // Convert to entity attribute
        assertEquals(IndianState.MAHARASHTRA, converter.convertToEntityAttribute("Maharashtra"));
        assertEquals(IndianState.MAHARASHTRA, converter.convertToEntityAttribute("MH"));
        assertEquals(IndianState.MAHARASHTRA, converter.convertToEntityAttribute("mh"));
        assertEquals(IndianState.MAHARASHTRA, converter.convertToEntityAttribute("  maharashtra  "));
        assertNull(converter.convertToEntityAttribute(null));
        assertNull(converter.convertToEntityAttribute(""));
    }

    @Test
    public void testJacksonSerialization() throws Exception {
        // Serialization: should use display name due to @JsonValue
        String serialized = objectMapper.writeValueAsString(IndianState.MAHARASHTRA);
        assertEquals("\"Maharashtra\"", serialized);

        // Deserialization: should resolve from both display name and code
        IndianState deserializedFromDisplayName = objectMapper.readValue("\"Maharashtra\"", IndianState.class);
        assertEquals(IndianState.MAHARASHTRA, deserializedFromDisplayName);

        IndianState deserializedFromCode = objectMapper.readValue("\"MH\"", IndianState.class);
        assertEquals(IndianState.MAHARASHTRA, deserializedFromCode);

        IndianState deserializedFromLowerCase = objectMapper.readValue("\"maharashtra\"", IndianState.class);
        assertEquals(IndianState.MAHARASHTRA, deserializedFromLowerCase);
    }
}
