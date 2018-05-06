package com.project.topaz.service;

public interface SecurityUserService {

    String validatePasswordResetToken(Long id, String token);

}
