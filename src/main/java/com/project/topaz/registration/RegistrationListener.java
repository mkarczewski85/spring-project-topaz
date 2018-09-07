package com.project.topaz.registration;

import com.project.topaz.model.User;
import com.project.topaz.service.UserService;
import com.project.topaz.web.utils.EmailUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class RegistrationListener implements ApplicationListener<OnRegistrationCompleteEvent> {

    private UserService userService;
    private JavaMailSender mailSender;
    private EmailUtils emailUtils;

    @Autowired
    public RegistrationListener(UserService userService, JavaMailSender mailSender, EmailUtils emailUtils) {
        this.userService = userService;
        this.mailSender = mailSender;
        this.emailUtils = emailUtils;
    }

    @Override
    public void onApplicationEvent(OnRegistrationCompleteEvent event) {
        confirmRegistration(event);
    }

    private void confirmRegistration(OnRegistrationCompleteEvent event) {
        User user = event.getUser();
        String token = UUID.randomUUID().toString();
        userService.createVerificationTokenForUser(user, token);
        SimpleMailMessage emailMsg = emailUtils.createVerificationTokenEmail(event, user, token);
        mailSender.send(emailMsg);
    }

}
