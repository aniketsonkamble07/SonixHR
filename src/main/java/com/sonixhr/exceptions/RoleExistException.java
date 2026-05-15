package com.sonixhr.exceptions;

public class RoleExistException extends RuntimeException{

    public  RoleExistException(String message)
    {
        super(message);
    }
}
