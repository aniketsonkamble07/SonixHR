package com.sonixhr.service;

public interface EmailService {

    void sendActivationEmail(
            String to,
            String name,
            String activationLink
    );
}
