package com.project.topaz.web.controller;

import com.project.topaz.model.User;
import com.project.topaz.model.VerificationToken;
import com.project.topaz.registration.OnRegistrationCompleteEvent;
import com.project.topaz.service.UserService;
import com.project.topaz.web.dto.PasswordDto;
import com.project.topaz.web.dto.UserDto;
import com.project.topaz.web.error.InvalidOldPasswordException;
import com.project.topaz.web.utils.EmailUtils;
import com.project.topaz.web.utils.GenericResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.Locale;
import java.util.UUID;

@Controller
public class RegistrationController {

    private Logger LOGGER = LoggerFactory.getLogger(getClass());

    private UserService userService;
    private MessageSource messageSource;
    private JavaMailSender mailSender;
    private ApplicationEventPublisher eventPublisher;
    private EmailUtils emailUtils;

    // API

    // registration process

    @Autowired
    public RegistrationController(UserService userService, MessageSource messageSource, JavaMailSender mailSender,
                                  ApplicationEventPublisher eventPublisher, EmailUtils emailUtils) {
        this.userService = userService;
        this.messageSource = messageSource;
        this.mailSender = mailSender;
        this.eventPublisher = eventPublisher;
        this.emailUtils = emailUtils;
    }

    @PostMapping("/user/registration")
    @ResponseBody
    public GenericResponse registerUserAccount(@RequestBody @Valid UserDto accountDto, HttpServletRequest request) {
        LOGGER.debug("Registering user account. Provided information: {}", accountDto);
        User registeredUser = userService.registerNewUserAccount(accountDto);
        eventPublisher.publishEvent(new OnRegistrationCompleteEvent(registeredUser, request.getLocale(),
                getAppUrl(request)));
        return new GenericResponse("success");
    }

    @GetMapping("/registrationConfirm")
    public String confirmRegistration(HttpServletRequest request, Model model, @RequestParam("token") String token) {
        Locale locale = request.getLocale();
        String result = userService.validateVerificationToken(token);
        if (result.equals("valid")) {
            model.addAttribute("message", messageSource.getMessage("message.accountVerified", null, locale));
            return "redirect:/rightUser.html?lang=" + locale.getLanguage();
        }

        model.addAttribute("message", messageSource.getMessage("auth.message." + result, null, locale));
        model.addAttribute("expired", "expired".equals(result));
        model.addAttribute("token", token);
        return "redirect:/badUser.html?lang=" + locale.getLanguage();
    }

    @GetMapping("/user/resendRegistrationToken")
    @ResponseBody
    public GenericResponse resendVerificationToken(HttpServletRequest request, @RequestParam("token")
            String existingToken) {

        VerificationToken newToken = userService.generateNewVerificationToken(existingToken);
        User user = userService.getUserByVerificationToken(newToken.getToken());
        SimpleMailMessage mailMsg = emailUtils.createResendVerificationTokenEmail(getAppUrl(request),
                request.getLocale(), newToken, user);
        mailSender.send(mailMsg);
        return new GenericResponse(messageSource.getMessage("message.resendToken", null, request.getLocale()));
    }

    @PostMapping("/user/resetPassword")
    @ResponseBody
    public GenericResponse resetPassword(HttpServletRequest request, @RequestParam("email") String userEmail) {
        User user = userService.findUserByEmail(userEmail);
        if (user != null) {
            String token = UUID.randomUUID().toString();
            userService.createPasswordResetTokenForUser(user, token);
            SimpleMailMessage mailMsg = emailUtils.createResetTokenEmail(getAppUrl(request), request.getLocale(),
                    token, user);
            mailSender.send(mailMsg);
        }
        return new GenericResponse(messageSource.getMessage("message.resetPasswordEmail", null,
                request.getLocale()));
    }

    @GetMapping("/user/changePassword")
    public String showChangePasswordPage(Locale locale, Model model, @RequestParam("id") Long id,
                                         @RequestParam("token") String token) {

        String result = userService.validatePasswordResetToken(id, token);
        if (result != null) {
            model.addAttribute("message", messageSource.getMessage("auth.message." + result, null, locale));
            return "redirect:/badUser?lang=" + locale.getLanguage();
        }
        return "redirect:/updatePassword.html?lang=" + locale.getLanguage();
    }

    @PostMapping("/user/setPassword")
    @ResponseBody
    public GenericResponse setNewPassword(Locale locale, @Valid PasswordDto passwordDto) {
        User user = (User) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        userService.changeUserPassword(user, passwordDto.getNewPassword());
        return new GenericResponse(messageSource.getMessage("message.resetPasswordSuc", null, locale));
    }

    @PostMapping("/user/updatePassword")
    @ResponseBody
    public GenericResponse changeUserPassword(Locale locale, @Valid PasswordDto passwordDto) {
        String email = ((User) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getEmail();
        User user = userService.findUserByEmail(email);
        if (!userService.checkIfValidOldPassword(user, passwordDto.getOldPassword())) {
            throw new InvalidOldPasswordException();
        }
        userService.changeUserPassword(user, passwordDto.getNewPassword());
        return new GenericResponse(messageSource.getMessage("message.updatePasswordSuc", null, locale));
    }


    // NON API
    private String getAppUrl(HttpServletRequest request) {
        return "http://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath();
    }

}
