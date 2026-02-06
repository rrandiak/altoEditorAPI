package cz.inovatika.altoEditor.presentation.mapper;

import org.mapstruct.Mapper;

import cz.inovatika.altoEditor.domain.model.User;
import cz.inovatika.altoEditor.presentation.dto.response.UserDto;

/** Maps User entity to UserDto. */
@Mapper(componentModel = "spring")
public interface UserMapper {

    UserDto toDto(User user);
}