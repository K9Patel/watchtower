package com.watchtower.backend.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "user_network_scope",
        uniqueConstraints = {
                @UniqueConstraint(name = "uq_user_network_scope", columnNames = {"user_id", "network_prefix"})
        }
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserNetworkScope {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "network_prefix", nullable = false, length = 64)
    private String networkPrefix;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "last_seen_at", nullable = false)
    private LocalDateTime lastSeenAt;
}
