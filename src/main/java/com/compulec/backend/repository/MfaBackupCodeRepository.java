package com.compulec.backend.repository;

import com.compulec.backend.entity.MfaBackupCode;
import com.compulec.backend.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MfaBackupCodeRepository extends JpaRepository<MfaBackupCode, Long> {

    Optional<MfaBackupCode> findByUserAndCodeHashAndUsedAtIsNull(User user, String codeHash);

    long countByUserAndUsedAtIsNull(User user);

    @Modifying
    @Query("DELETE FROM MfaBackupCode c WHERE c.user = :user")
    void deleteAllByUser(User user);
}
