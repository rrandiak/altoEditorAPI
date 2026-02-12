package cz.inovatika.altoEditor.presentation.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import cz.inovatika.altoEditor.domain.model.User;
import cz.inovatika.altoEditor.presentation.dto.response.CurrentUserDto;
import cz.inovatika.altoEditor.presentation.security.UserProfile;

/** Maps User entity to UserDto. */
@Mapper(componentModel = "spring")
public interface CurrentUserMapper {

    @Mapping(target = "id", source = "userId")
    CurrentUserDto toDto(UserProfile userProfile);

    @Mapping(target = "id", source = "user.id")
    @Mapping(target = "username", source = "user.username")
    @Mapping(target = "roles", source = "userProfile.roles")
    CurrentUserDto toDto(User user, UserProfile userProfile);
}
