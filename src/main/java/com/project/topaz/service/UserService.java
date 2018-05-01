package com.project.topaz.service;

import com.project.topaz.model.PasswordResetToken;
import com.project.topaz.model.User;
import com.project.topaz.model.VerificationToken;
import com.project.topaz.web.dto.UserDto;
import com.project.topaz.web.error.UserAlreadyExistException;

import java.util.List;

public interface UserService {

    User registerNewUserAccount(UserDto accountDto) throws UserAlreadyExistException;

    User getUser(String verificationToken);

    void saveRegisteredUser(User user);

    void deleteUser(User user);

    void createVerificationTokenForUser(User user, String token);

    VerificationToken getVerificationToken(String verificationToken);

    VerificationToken generateNewVerificationToken(String token);

    void createPasswordResetTokenForUser(User user, String token);

    User findUserByEmail(String email);

    PasswordResetToken getPasswordResetToken(String token);

    User getUserByPasswordResetToken(String token);

    User getUserByID(long id);

    void changeUserPassword(User user, String password);

    boolean checkIfValidOldPassword(User user, String password);

    String validateVerificationToken(String token);

    List<String> getUsersFromSessionRegistry();
}
