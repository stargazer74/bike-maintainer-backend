package de.chriswohlbrecht.maintenance.mapper;

import de.chriswohlbrecht.maintenance.api.model.Currency;
import de.chriswohlbrecht.maintenance.api.model.Language;
import de.chriswohlbrecht.maintenance.api.model.ProfileResponse;
import de.chriswohlbrecht.maintenance.api.model.UserResponse;
import de.chriswohlbrecht.maintenance.component.model.Access;
import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Mapper(componentModel = "spring")
public interface UserMapper {

    /** The access status is not part of the entity; it comes from {@code IAccessComponent}. */
    @Mapping(target = "accessStatus", source = "access.status")
    @Mapping(target = "accessUntil", source = "access.until")
    @Mapping(target = "hasSubscription", source = "access.hasSubscription")
    UserResponse toResponse(AppUser user, Access access);

    /** Users created in portal mode have no display name; their email stands in for it. */
    default ProfileResponse toProfileResponse(AppUser user) {
        return new ProfileResponse()
                .id(user.getId())
                .displayName(user.getDisplayName() != null ? user.getDisplayName() : user.getEmail());
    }

    default Language toLanguage(String language) {
        return language == null ? Language.DE : Language.fromValue(language);
    }

    default Currency toCurrency(String currency) {
        return currency == null ? Currency.EUR : Currency.fromValue(currency);
    }

    default OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }
}
