package com.sonixhr.exceptions;

public class SubdomainExistsException extends RuntimeException{
    public  SubdomainExistsException(String message)
    {
        super(message);
    }
}
