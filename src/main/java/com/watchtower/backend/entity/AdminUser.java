package com.watchtower.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "admin_user")
@Data @NoArgsConstructor @AllArgsConstructor @Builder
public class AdminUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    @Column(name = "password", nullable = false, length = 255)
    private String password;            // BCrypt hash — NEVER plain text

    @Column(name = "email", nullable = false, unique = true, length = 150)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false, length = 10)
    private Role role;

    public enum Role { ADMIN, USER }
}