package com.watchtower.backend;

import com.watchtower.backend.entity.Alert;
import com.watchtower.backend.entity.Device;
import com.watchtower.backend.entity.UsageLog;
import com.watchtower.backend.observer.AlertPublisher;
import com.watchtower.backend.repository.DeviceRepository;
import com.watchtower.backend.repository.UsageLogRepository;
import com.watchtower.backend.service.DiagnosisEngine;
import com.watchtower.backend.strategy.DiagnosisRule;
import com.watchtower.backend.strategy.HighBandwidthRule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * AJT — JUnit 5 + Mockito: DiagnosisEngine unit tests.
 *
 * Tests verify that DiagnosisEngine:
 *   1. Calls evaluate() on all injected rules for each device
 *   2. Publishes alerts when rules fire
 *   3. Does NOT publish alerts when all rules pass
 *   4. HighBandwidthRule fires correctly at >= 80% bandwidth
 *   5. HighBandwidthRule does NOT fire below threshold
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("DiagnosisEngine + Rule Tests")
class DiagnosisEngineTest {

    @Mock private DeviceRepository   deviceRepository;
    @Mock private UsageLogRepository usageLogRepository;
    @Mock private AlertPublisher     alertPublisher;
    @Mock private DiagnosisRule      mockRule;

    @InjectMocks private DiagnosisEngine diagnosisEngine;

    private Device testDevice;
    private List<UsageLog> recentLogs;

    @BeforeEach
    void setUp() {
        testDevice = Device.builder()
                .id(1L).deviceName("Student-Laptop-01").ipAddress("192.168.1.101")
                .isActive(true).status("ONLINE").build();

        recentLogs = List.of(
                UsageLog.builder().device(testDevice).bytesUsed(850.0)
                        .bandwidthPercentage(85.0).trafficType("STREAMING")
                        .timestamp(LocalDateTime.now().minusSeconds(20)).build(),
                UsageLog.builder().device(testDevice).bytesUsed(900.0)
                        .bandwidthPercentage(90.0).trafficType("STREAMING")
                        .timestamp(LocalDateTime.now().minusSeconds(10)).build()
        );

        // Manually inject the mocked rule list
        ReflectionTestUtils.setField(diagnosisEngine, "rules", List.of(mockRule));
    }

    @Test
    @DisplayName("Should call evaluate() on every rule for every active device")
    void shouldCallEvaluateOnAllRules() {
        // Stub the initial "total network load" query so DiagnosisEngine doesn't short-circuit
        when(usageLogRepository.findByTimestampAfter(any(LocalDateTime.class)))
                .thenReturn(recentLogs);
        when(deviceRepository.findByIsActiveTrue()).thenReturn(List.of(testDevice));
        when(usageLogRepository.findByDeviceAndTimestampAfter(eq(testDevice), any(LocalDateTime.class)))
                .thenReturn(recentLogs);
        when(mockRule.evaluate(any(Device.class), anyList())).thenReturn(Optional.empty());

        diagnosisEngine.runDiagnosis();

        verify(mockRule, times(1)).evaluate(eq(testDevice), eq(recentLogs));
    }

    @Test
    @DisplayName("Should publish alert when a rule fires")
    void shouldPublishAlertWhenRuleFires() {
        Alert alert = Alert.builder()
                .device(testDevice).alertType("CONGESTION")
                .severity(Alert.Severity.HIGH).message("Test alert").build();

        when(usageLogRepository.findByTimestampAfter(any(LocalDateTime.class)))
                .thenReturn(recentLogs);
        when(deviceRepository.findByIsActiveTrue()).thenReturn(List.of(testDevice));
        when(usageLogRepository.findByDeviceAndTimestampAfter(eq(testDevice), any(LocalDateTime.class)))
                .thenReturn(recentLogs);
        when(mockRule.evaluate(any(Device.class), anyList())).thenReturn(Optional.of(alert));

        diagnosisEngine.runDiagnosis();

        ArgumentCaptor<Alert> captor = ArgumentCaptor.forClass(Alert.class);
        verify(alertPublisher).publish(captor.capture());
        assertThat(captor.getValue().getAlertType()).isEqualTo("CONGESTION");
        assertThat(captor.getValue().getSeverity()).isEqualTo(Alert.Severity.HIGH);
    }

    @Test
    @DisplayName("Should NOT publish when no rules fire")
    void shouldNotPublishWhenNoRulesFire() {
        when(usageLogRepository.findByTimestampAfter(any(LocalDateTime.class)))
                .thenReturn(recentLogs);
        when(deviceRepository.findByIsActiveTrue()).thenReturn(List.of(testDevice));
        when(usageLogRepository.findByDeviceAndTimestampAfter(eq(testDevice), any(LocalDateTime.class)))
                .thenReturn(recentLogs);
        when(mockRule.evaluate(any(Device.class), anyList())).thenReturn(Optional.empty());

        diagnosisEngine.runDiagnosis();

        verify(alertPublisher, never()).publish(any(Alert.class));
    }

    @Test
    @DisplayName("HighBandwidthRule fires CONGESTION/HIGH at >= 80% bandwidth")
    void highBandwidthRuleShouldFireAboveThreshold() {
        // Use real rule (not mock) for strategy pattern verification
        HighBandwidthRule rule = new HighBandwidthRule(usageLogRepository);

        List<UsageLog> highLogs = List.of(
                UsageLog.builder().device(testDevice).bytesUsed(820.0)
                        .bandwidthPercentage(82.0).trafficType("BROWSING")
                        .timestamp(LocalDateTime.now()).build()
        );

        // Stub the total network bandwidth query used inside HighBandwidthRule
        when(usageLogRepository.findByTimestampAfter(any(LocalDateTime.class)))
                .thenReturn(highLogs);

        Optional<Alert> result = rule.evaluate(testDevice, highLogs);

        assertThat(result).isPresent();
        assertThat(result.get().getAlertType()).isEqualTo("CONGESTION");
        assertThat(result.get().getSeverity()).isEqualTo(Alert.Severity.HIGH);
        assertThat(result.get().getMessage()).contains("Student-Laptop-01");
    }

    @Test
    @DisplayName("HighBandwidthRule should NOT fire at < 80% bandwidth")
    void highBandwidthRuleShouldNotFireBelowThreshold() {
        HighBandwidthRule rule = new HighBandwidthRule(usageLogRepository);

        List<UsageLog> normalLogs = List.of(
                UsageLog.builder().device(testDevice).bytesUsed(50.0)
                        .bandwidthPercentage(50.0).trafficType("BROWSING")
                        .timestamp(LocalDateTime.now()).build()
        );

        // Total network = 200 bytes, device = 50 bytes → 25% share → no alert
        List<UsageLog> allNetworkLogs = List.of(
                UsageLog.builder().device(testDevice).bytesUsed(50.0)
                        .bandwidthPercentage(50.0).trafficType("BROWSING")
                        .timestamp(LocalDateTime.now()).build(),
                UsageLog.builder().device(testDevice).bytesUsed(150.0)
                        .bandwidthPercentage(30.0).trafficType("BROWSING")
                        .timestamp(LocalDateTime.now()).build()
        );

        when(usageLogRepository.findByTimestampAfter(any(LocalDateTime.class)))
                .thenReturn(allNetworkLogs);

        Optional<Alert> result = rule.evaluate(testDevice, normalLogs);
        assertThat(result).isEmpty();
    }
}
