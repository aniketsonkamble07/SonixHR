package com.sonixhr.exceptions;
 
public class ResourceNotFoundException extends BaseException {

    public ResourceNotFoundException(String resourceName, Object id) {
        super("RES_001", 404,
                String.format("%s not found with id: %s", resourceName, id),
                String.format("%s with identifier %s does not exist", resourceName, id));
    }

    public ResourceNotFoundException(String message) {
        super("RES_001", 404, message, message);
    }

    public ResourceNotFoundException(String resourceName, String fieldName, Object value) {
        super("RES_002", 404,
                String.format("%s not found with %s: %s", resourceName, fieldName, value),
                String.format("%s with %s = %s does not exist", resourceName, fieldName, value));
    }
}