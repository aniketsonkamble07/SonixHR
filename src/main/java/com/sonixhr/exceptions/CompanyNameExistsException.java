package com.sonixhr.exceptions;

public class CompanyNameExistsException extends RuntimeException{
    public  CompanyNameExistsException(String message)
    {
        super(message);
    }
}
