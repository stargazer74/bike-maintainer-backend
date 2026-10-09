package de.chriswohlbrecht.maintenance.mapper;

import de.chriswohlbrecht.maintenance.api.model.Language;
import de.chriswohlbrecht.maintenance.api.model.UserResponse;
import de.chriswohlbrecht.maintenance.persistence.model.AppUser;
import org.mapstruct.Mapper;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserResponse toResponse(AppUser user);

    default Language toLanguage(String language) {
        return language == null ? Language.DE : Language.fromValue(language);
    }

    default OffsetDateTime toOffsetDateTime(LocalDateTime value) {
        return value == null ? null : value.atZone(ZoneId.systemDefault()).toOffsetDateTime();
    }
}
