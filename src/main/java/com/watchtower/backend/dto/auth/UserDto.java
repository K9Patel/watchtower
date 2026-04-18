package com.watchtower.backend.dto.auth;

import com.watchtower.backend.entity.User;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class UserDto {
    private String id;
    private String name;
    private String email;
    private String avatar;
    private String emailVerifiedAt;

    public static UserDto fromEntity(User user) {
        return UserDto.builder()
                .id(user.getUserId())
                .name(user.getUserName())
                .email(user.getUserEmail())
                .avatar(user.getAvatar())
                .emailVerifiedAt(user.getEmailVerifiedAt() != null
                        ? user.getEmailVerifiedAt().toString() : null)
                .build();
    }
}
