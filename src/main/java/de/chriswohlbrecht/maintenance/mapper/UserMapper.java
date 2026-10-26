package de.chriswohlbrecht.maintenance.mapper;

import de.chriswohlbrecht.maintenance.api.model.Language;
import de.chriswohlbrecht.maintenance.api.model.ProfileResponse;
import de.chriswohlbrecht.maintenance.api.model.UserResponse;
import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import org.mapstruct.Mapper;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResponse toResponse(AppUser user);

    /** Users created in portal mode have no display name; their email stands in for it. */
    default ProfileResponse toProfileResponse(AppUser user) {
        return new ProfileResponse()
                .id(user.getId())
                .displayName(user.getDisplayName() != null ? user.getDisplayName() : user.getEmail());
    }

    default Language toLanguage(String language) {
        return language == null ? Language.DE : Language.fromValue(language);
    }

    default OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }
}
