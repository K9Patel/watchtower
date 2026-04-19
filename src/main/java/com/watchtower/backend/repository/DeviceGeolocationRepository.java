package com.watchtower.backend.repository;

import com.watchtower.backend.entity.DeviceGeolocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface DeviceGeolocationRepository extends JpaRepository<DeviceGeolocation, Long> {

    Optional<DeviceGeolocation> findByDeviceId(Long deviceId);

    List<DeviceGeolocation> findAllByDeviceIdIn(Collection<Long> deviceIds);
}
