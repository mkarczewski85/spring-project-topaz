package com.project.topaz.repository;

import com.project.topaz.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Date;
import java.util.stream.Stream;

@Repository
public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetTokenRepository, Long> {

    PasswordResetTokenRepository findByToken(String token);

    PasswordResetTokenRepository findByUser(User user);

    Stream<PasswordResetTokenRepository> findAllByExpiryDateLessThan(Date now);

    void deleteByExpiryDateLessThan(Date now);

    @Modifying
    @Query("delete from PasswordResetTokenRepository t where t.expiryDate <= :now")
    void deleteAllExpiredSince(@Param("now") Date now);

}
