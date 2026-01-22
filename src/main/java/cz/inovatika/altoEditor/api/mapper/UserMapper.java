package cz.inovatika.altoEditor.api.mapper;

import org.mapstruct.Mapper;

import cz.inovatika.altoEditor.api.dto.UserDto;
import cz.inovatika.altoEditor.core.entity.User;

@Mapper(componentModel = "spring")
public interface UserMapper {

    UserDto toDto(User user);

    User toEntity(UserDto dto);
}