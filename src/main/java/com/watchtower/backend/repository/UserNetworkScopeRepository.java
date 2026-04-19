package com.watchtower.backend.repository;

import com.watchtower.backend.entity.UserNetworkScope;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserNetworkScopeRepository extends JpaRepository<UserNetworkScope, Long> {

    Optional<UserNetworkScope> findByUser_UserEmailAndNetworkPrefix(String userEmail, String networkPrefix);

    List<UserNetworkScope> findAllByUser_UserEmailOrderByLastSeenAtDesc(String userEmail);
}
