package com.project.topaz.web.controller;

import com.project.topaz.model.Privilege;
import com.project.topaz.model.Role;
import com.project.topaz.model.User;
import com.project.topaz.registration.OnRegistrationCompleteEvent;
import com.project.topaz.service.SecurityUserService;
import com.project.topaz.service.UserService;
import com.project.topaz.web.dto.UserDto;
import com.project.topaz.web.utils.GenericResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.MessageSource;
import org.springframework.core.env.Environment;
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
import java.io.UnsupportedEncodingException;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

@Controller
public class RegistrationController {

    private final Logger LOGGER = LoggerFactory.getLogger(getClass());

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
    public GenericResponse registerUserAccount(@Valid UserDto accountDto, HttpServletRequest request) {
        LOGGER.debug("Registering of user account. Provided information: {}", accountDto);
        User registeredUser = userService.registerNewUserAccount(accountDto);
        eventPublisher.publishEvent(new OnRegistrationCompleteEvent(registeredUser, request.getLocale(),
                getAppUrl(request)));
        return new GenericResponse("succes");
    }

    @GetMapping("/registrationConfirm")
    public String confirmRegistration(HttpServletRequest request, Model model, @RequestParam("token") String token)
            throws UnsupportedEncodingException {
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


    // NON API

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
}
