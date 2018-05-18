package com.project.topaz.web.controller;

import com.project.topaz.model.Privilege;
import com.project.topaz.model.Role;
import com.project.topaz.model.User;
import com.project.topaz.model.VerificationToken;
import com.project.topaz.registration.OnRegistrationCompleteEvent;
import com.project.topaz.service.SecurityUserService;
import com.project.topaz.service.UserService;
import com.project.topaz.web.dto.PasswordDto;
import com.project.topaz.web.dto.UserDto;
import com.project.topaz.web.error.InvalidOldPasswordException;
import com.project.topaz.web.utils.GenericResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.core.env.Environment;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.stream.Collectors;

@Controller
public class RegistrationController {

    private Logger LOGGER = LoggerFactory.getLogger(getClass());

    private UserService userService;
    private SecurityUserService securityUserService;
    private MessageSource messageSource;
    private JavaMailSender mailSender;
    private ApplicationEventPublisher eventPublisher;
    private Environment environment;

    // API

    // registration process

    @Autowired
    public RegistrationController(UserService userService, SecurityUserService securityUserService,
                                  MessageSource messageSource, JavaMailSender mailSender,
                                  ApplicationEventPublisher eventPublisher, Environment environment) {
        this.userService = userService;
        this.securityUserService = securityUserService;
        this.messageSource = messageSource;
        this.mailSender = mailSender;
        this.eventPublisher = eventPublisher;
        this.environment = environment;
    }

    @PostMapping("/user/registration")
    @ResponseBody
    public GenericResponse registerUserAccount(@RequestBody @Valid UserDto accountDto, HttpServletRequest request) {
        LOGGER.debug("Registering user account. Provided information: {}", accountDto);
        User registeredUser = userService.registerNewUserAccount(accountDto);
        eventPublisher.publishEvent(new OnRegistrationCompleteEvent(registeredUser, request.getLocale(),
                getAppUrl(request)));
        return new GenericResponse("succes");
    }

    @GetMapping("/registrationConfirm")
    public String confirmRegistration(HttpServletRequest request, Model model, @RequestParam("token") String token) {
        Locale locale = request.getLocale();
        String result = userService.validateVerificationToken(token);
        if (result.equals("valid")) {
            User user = userService.getUser(token);
            authWithoutPassword(user);
            model.addAttribute("message", messageSource.getMessage("message.accountVerified", null, locale));
            return "redirect:/console.html?lang=" + locale.getLanguage();
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
        User user = userService.getUser(newToken.getToken());
        mailSender.send(createResendVerificationTokenEmail(getAppUrl(request), request.getLocale(), newToken, user));
        return new GenericResponse(messageSource.getMessage("message.resendToken", null, request.getLocale()));
    }

    @PostMapping("/user/resetPassword")
    @ResponseBody
    public GenericResponse resetPassword(HttpServletRequest request, @RequestParam("email") String userEmail) {
        User user = userService.findUserByEmail(userEmail);
        if (user != null) {
            String token = UUID.randomUUID().toString();
            userService.createPasswordResetTokenForUser(user, token);
            mailSender.send(createResetTokenEmail(getAppUrl(request), request.getLocale(), token, user));
        }
        return new GenericResponse(messageSource.getMessage("message.resetPasswordEmail", null,
                request.getLocale()));
    }

    @GetMapping("/user/changePassword")
    public String showChangePasswordPage(Locale locale, Model model, @RequestParam("id") Long id,
                                         @RequestParam("token") String token) {

        String result = securityUserService.validatePasswordResetToken(id, token);
        if (result != null) {
            model.addAttribute("message", messageSource.getMessage("auth.message." + result, null, locale));
            return "redirect:/login?lang=" + locale.getLanguage();
        }
        return "redirect:/updatePassword.html?lang=" + locale.getLanguage();
    }

    @PostMapping("/user/savePassword")
    @ResponseBody
    public GenericResponse savePassword(Locale locale, @Valid PasswordDto passwordDto) {
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


    // NON API (TODO: move to other util class)
    private String getAppUrl(HttpServletRequest request) {
        return "http://" + request.getServerName() + ":" + request.getServerPort() + request.getContextPath();
    }

    private void authWithoutPassword(User user) {
        List<Privilege> privileges = user.getRoles().stream().map(Role::getPrivileges).flatMap(Collection::stream)
                .distinct().collect(Collectors.toList());
        List<GrantedAuthority> authorities = privileges.stream().map(p ->
                new SimpleGrantedAuthority(p.getName())).collect(Collectors.toList());

        Authentication authentication = new UsernamePasswordAuthenticationToken(user, null, authorities);
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }

    private SimpleMailMessage createResendVerificationTokenEmail(String contextPath, Locale locale,
                                                                 VerificationToken newToken, User user) {
        String confirmationUrl = contextPath + "/registrationConfirm.html?token=" + newToken.getToken();
        String message = messageSource.getMessage("message.resendToken", null, locale);
        return constructEmail("Resend Registration Token", message + " \r\n" + confirmationUrl, user);
    }

    private SimpleMailMessage createResetTokenEmail(String contextPath, Locale locale, String token, User user) {
        String url = contextPath + "/user/changePassword?id=" + user.getId() + "&token=" + token;
        String message = messageSource.getMessage("message.resetPassword", null, locale);
        return constructEmail("Reset Password", message + " \r\n" + url, user);
    }

    private SimpleMailMessage constructEmail(String subject, String body, User user) {
        SimpleMailMessage email = new SimpleMailMessage();
        email.setSubject(subject);
        email.setText(body);
        email.setTo(user.getEmail());
        email.setFrom(environment.getProperty("support.email"));
        return email;
    }


}
