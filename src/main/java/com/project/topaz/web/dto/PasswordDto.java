package com.project.topaz.web.dto;


import com.project.topaz.validators.ValidPassword;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class PasswordDto {

    private String oldPassword;

    @ValidPassword
    private String newPassword;

}
