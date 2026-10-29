package de.chriswohlbrecht.maintenance.component;

import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import de.chriswohlbrecht.maintenance.persistence.model.UserToken;
import de.chriswohlbrecht.maintenance.persistence.repository.UserTokenRepository;
import de.chriswohlbrecht.maintenance.persistence.type.UserTokenType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class UserTokenComponentImpl implements IUserTokenComponent {

    private static final int TOKEN_BYTES = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserTokenRepository userTokenRepository;
    private final Clock clock;

    @Override
    @Transactional
    public String issue(AppUser user, UserTokenType type, Duration validity) {
        LocalDateTime now = LocalDateTime.now(clock);
        userTokenRepository.invalidateOpenTokens(user, type, now);

        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        UserToken userToken = new UserToken();
        userToken.setUser(user);
        userToken.setType(type);
        userToken.setTokenHash(hash(token));
        userToken.setExpiresAt(now.plus(validity));
        userTokenRepository.save(userToken);
        return token;
    }

    @Override
    @Transactional
    public Optional<AppUser> consume(String token, UserTokenType type) {
        LocalDateTime now = LocalDateTime.now(clock);
        return userTokenRepository.findByTokenHash(hash(token))
                .filter(candidate -> candidate.getType() == type)
                .filter(candidate -> candidate.getUsedAt() == null)
                .filter(candidate -> candidate.getExpiresAt().isAfter(now))
                .filter(candidate -> userTokenRepository.markUsed(candidate.getId(), now) == 1)
                .map(UserToken::getUser);
    }

    @Override
    @Transactional
    public void revokeOpen(AppUser user, UserTokenType type) {
        userTokenRepository.invalidateOpenTokens(user, type, LocalDateTime.now(clock));
    }

    @Override
    @Transactional
    public int deleteExpired() {
        return userTokenRepository.deleteUsedOrExpired(LocalDateTime.now(clock));
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
