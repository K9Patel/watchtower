package com.watchtower.backend;

import com.watchtower.backend.entity.Device;
import com.watchtower.backend.entity.UsageLog;
import com.watchtower.backend.repository.DeviceRepository;
import com.watchtower.backend.repository.UsageLogRepository;
import com.watchtower.backend.service.SimulationControlService;
import com.watchtower.backend.service.SimulatorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * AJT — JUnit 5 + Mockito: SimulatorService unit tests.
 *
 * @ExtendWith(MockitoExtension.class): enables Mockito in JUnit 5
 * @Mock: creates a mock (fake) object — no real DB calls are made
 * @InjectMocks: creates the real SimulatorService, injecting the mocks above
 *
 * Tests verify that SimulatorService correctly:
 *   1. Generates one UsageLog per active device
 *   2. Respects the pause toggle (no logs when paused)
 *   3. Does nothing when there are no active devices
 *   4. Caps bandwidth percentage at 100%
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SimulatorService Tests")
class SimulatorServiceTest {

    @Mock private DeviceRepository       deviceRepository;
    @Mock private UsageLogRepository     usageLogRepository;
    @Mock private SimulationControlService controlService;

    @InjectMocks private SimulatorService simulatorService;

    private Device student1, student2, staff1;

    @BeforeEach
    void setUp() {
        student1 = Device.builder()
                .id(1L).deviceName("Student-Laptop-01").ipAddress("192.168.1.101")
                .isActive(true).status("ONLINE").build();

        student2 = Device.builder()
                .id(2L).deviceName("Student-Laptop-02").ipAddress("192.168.1.102")
                .isActive(true).status("ONLINE").build();

        staff1 = Device.builder()
                .id(3L).deviceName("Staff-PC-01").ipAddress("192.168.2.11")
                .isActive(true).status("ONLINE").build();
    }

    @Test
    @DisplayName("Should generate one UsageLog per active device")
    void shouldGenerateOneLogPerDevice() {
        // ARRANGE
        when(controlService.isRunning()).thenReturn(true);
        when(deviceRepository.findByIsActiveTrue()).thenReturn(List.of(student1, student2, staff1));
        when(usageLogRepository.save(any(UsageLog.class))).thenAnswer(i -> i.getArgument(0));

        // ACT
        simulatorService.generateUsage();

        // ASSERT — save() called once per device (3 times)
        verify(usageLogRepository, times(3)).save(any(UsageLog.class));
    }

    @Test
    @DisplayName("Should not generate logs when simulator is paused")
    void shouldNotGenerateWhenPaused() {
        // ARRANGE
        when(controlService.isRunning()).thenReturn(false);

        // ACT
        simulatorService.generateUsage();

        // ASSERT — no DB calls at all
        verify(deviceRepository, never()).findByIsActiveTrue();
        verify(usageLogRepository, never()).save(any(UsageLog.class));
    }

    @Test
    @DisplayName("Should not crash when no active devices exist")
    void shouldHandleNoActiveDevices() {
        when(controlService.isRunning()).thenReturn(true);
        when(deviceRepository.findByIsActiveTrue()).thenReturn(List.of());

        simulatorService.generateUsage();

        verify(usageLogRepository, never()).save(any(UsageLog.class));
    }

    @Test
    @DisplayName("Saved UsageLog should have valid bandwidthPercentage (0-100)")
    void savedLogShouldHaveValidBandwidth() {
        when(controlService.isRunning()).thenReturn(true);
        when(deviceRepository.findByIsActiveTrue()).thenReturn(List.of(student1));

        ArgumentCaptor<UsageLog> captor = ArgumentCaptor.forClass(UsageLog.class);
        when(usageLogRepository.save(captor.capture())).thenAnswer(i -> i.getArgument(0));

        simulatorService.generateUsage();

        UsageLog saved = captor.getValue();
        assertThat(saved.getBandwidthPercentage()).isBetween(0.0, 100.0);
        assertThat(saved.getBytesUsed()).isGreaterThan(0.0);
        assertThat(saved.getTrafficType()).isNotBlank();
        assertThat(saved.getDevice()).isEqualTo(student1);
    }

    @Test
    @DisplayName("Saved UsageLog should always have a valid traffic type")
    void savedLogShouldHaveKnownTrafficType() {
        when(controlService.isRunning()).thenReturn(true);
        when(deviceRepository.findByIsActiveTrue()).thenReturn(List.of(student1));
        ArgumentCaptor<UsageLog> captor = ArgumentCaptor.forClass(UsageLog.class);
        when(usageLogRepository.save(captor.capture())).thenAnswer(i -> i.getArgument(0));

        simulatorService.generateUsage();

        String trafficType = captor.getValue().getTrafficType();
        assertThat(trafficType).isIn("STREAMING", "BROWSING", "GAMING", "DOWNLOAD", "VOIP");
    }
}
