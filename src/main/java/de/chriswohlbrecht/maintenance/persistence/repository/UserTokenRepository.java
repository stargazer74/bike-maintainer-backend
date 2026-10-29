package de.chriswohlbrecht.maintenance.persistence.repository;

import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import de.chriswohlbrecht.maintenance.persistence.model.UserToken;
import de.chriswohlbrecht.maintenance.persistence.type.UserTokenType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Optional;

public interface UserTokenRepository extends JpaRepository<UserToken, Long> {

    Optional<UserToken> findByTokenHash(String tokenHash);

    /** Marks all open tokens of this user and type as used, so only a token issued afterwards is valid. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE UserToken t SET t.usedAt = :now WHERE t.user = :user AND t.type = :type AND t.usedAt IS NULL")
    int invalidateOpenTokens(@Param("user") AppUser user, @Param("type") UserTokenType type,
                             @Param("now") LocalDateTime now);

    /**
     * Marks the token as used unless another request did so first; the condition is checked by the database, so of
     * two parallel requests only one gets 1.
     */
    @Modifying(flushAutomatically = true)
    @Query("UPDATE UserToken t SET t.usedAt = :now WHERE t.id = :id AND t.usedAt IS NULL")
    int markUsed(@Param("id") Long id, @Param("now") LocalDateTime now);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("DELETE FROM UserToken t WHERE t.expiresAt < :now OR t.usedAt IS NOT NULL")
    int deleteUsedOrExpired(@Param("now") LocalDateTime now);
}
