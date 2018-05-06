package com.project.topaz.service;

import com.project.topaz.model.PasswordResetToken;
import com.project.topaz.model.User;
import com.project.topaz.repository.PasswordResetTokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import javax.transaction.Transactional;
import java.util.Arrays;
import java.util.Calendar;

@Service
@Transactional
public class SecurityUserServiceImpl implements SecurityUserService {

    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Autowired
    public SecurityUserServiceImpl(PasswordResetTokenRepository passwordResetTokenRepository) {
        this.passwordResetTokenRepository = passwordResetTokenRepository;
    }

    // API

    @Override
    public String validatePasswordResetToken(Long id, String token) {

        PasswordResetToken resetToken = passwordResetTokenRepository.findByToken(token);
        if ((resetToken == null) || (resetToken.getUser().getId() != id)) {
            return "InvalidToken";
        }

        Calendar calendar = Calendar.getInstance();
        if ((resetToken.getExpiryDate().getTime() - calendar.getTime().getTime()) <= 0) {
            return "Expired";
        }

        User user = resetToken.getUser();
        Authentication auth = new UsernamePasswordAuthenticationToken(user, null,
                Arrays.asList(new SimpleGrantedAuthority("CHANGE_PASSWORD_PRIVILEGE")));
        SecurityContextHolder.getContext().setAuthentication(auth);
        return null;
    }
}
