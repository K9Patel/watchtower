package com.watchtower.backend.service;

import com.watchtower.backend.entity.UserNetworkScope;
import com.watchtower.backend.repository.UserNetworkScopeRepository;
import com.watchtower.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class UserDataScopeService {

    private final UserRepository userRepository;
    private final UserNetworkScopeRepository userNetworkScopeRepository;
    private final NetworkScopeResolver networkScopeResolver;

    @Transactional
    public LocalDateTime getOrCreateScopeStart(String userEmail) {
        String normalizedEmail = normalizeEmail(userEmail);
        if (normalizedEmail == null) {
            return LocalDateTime.now();
        }

        var user = userRepository.findByUserEmail(normalizedEmail).orElse(null);
        if (user == null) {
            return LocalDateTime.now();
        }

        String networkPrefix = networkScopeResolver.resolveCurrentNetworkPrefix();
        LocalDateTime now = LocalDateTime.now();

        UserNetworkScope scope = userNetworkScopeRepository
                .findByUser_UserEmailAndNetworkPrefix(normalizedEmail, networkPrefix)
                .orElseGet(() -> UserNetworkScope.builder()
                        .user(user)
                        .networkPrefix(networkPrefix)
                        .startedAt(now)
                        .lastSeenAt(now)
                        .build());

        scope.setLastSeenAt(now);
        userNetworkScopeRepository.save(scope);
        return scope.getStartedAt();
    }

    @Transactional
    public void activateScope(String userEmail) {
        getOrCreateScopeStart(userEmail);
    }

    @Transactional(readOnly = true)
    public List<UserNetworkScope> getPreviousScopes(String userEmail) {
        String normalizedEmail = normalizeEmail(userEmail);
        if (normalizedEmail == null) {
            return Collections.emptyList();
        }

        String currentPrefix = networkScopeResolver.resolveCurrentNetworkPrefix();
        return userNetworkScopeRepository.findAllByUser_UserEmailOrderByLastSeenAtDesc(normalizedEmail)
                .stream()
                .filter(scope -> !scope.getNetworkPrefix().equals(currentPrefix))
                .toList();
    }

    @Transactional(readOnly = true)
    public Optional<UserNetworkScope> getScopeForNetwork(String userEmail, String networkPrefix) {
        String normalizedEmail = normalizeEmail(userEmail);
        if (normalizedEmail == null || networkPrefix == null || networkPrefix.isBlank()) {
            return Optional.empty();
        }
        return userNetworkScopeRepository.findByUser_UserEmailAndNetworkPrefix(normalizedEmail, networkPrefix.trim());
    }

    private String normalizeEmail(String userEmail) {
        if (userEmail == null) {
            return null;
        }
        String normalized = userEmail.trim().toLowerCase();
        return normalized.isBlank() ? null : normalized;
    }
}
