package com.dispatch.service;

import com.dispatch.exception.AmbulanceUnavailableException;
import com.dispatch.exception.InvalidEmergencyRequestException;
import com.dispatch.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DispatchEngineTest {
    private DispatchEngine engine;

    @BeforeEach
    public void setup() {
        engine = new DispatchEngine();
    }

    private Ambulance ambulance(String id, AmbulanceType type, double x, double y) {
        return new Ambulance(id, type, "Driver-" + id, x, y);
    }

    private EmergencyRequest request(String patient, EmergencyPriority priority, double x, double y) {
        return new EmergencyRequest(patient, priority.name() + " emergency", priority, x, y, "City Hospital");
    }

    @Test
    public void testAllEmergencyPrioritiesAreAccepted() {
        engine.registerAmbulance(ambulance("B1", AmbulanceType.BASIC, 0, 0));
        engine.registerAmbulance(ambulance("A1", AmbulanceType.ADVANCED_LIFE_SUPPORT, 10, 10));
        engine.registerAmbulance(ambulance("I1", AmbulanceType.ICU, 20, 20));

        EmergencyRequest critical = request("P1", EmergencyPriority.CRITICAL, 20, 20);
        EmergencyRequest high = request("P2", EmergencyPriority.HIGH, 10, 10);
        EmergencyRequest moderate = request("P3", EmergencyPriority.MODERATE, 0, 0);
        EmergencyRequest normal = request("P4", EmergencyPriority.NORMAL, 50, 50);

        engine.submitEmergencyRequest(critical);
        engine.submitEmergencyRequest(high);
        engine.submitEmergencyRequest(moderate);
        engine.submitEmergencyRequest(normal);

        assertEquals(EmergencyStatus.DISPATCHED, critical.getEmergencyStatus());
        assertEquals(EmergencyStatus.DISPATCHED, high.getEmergencyStatus());
        assertEquals(EmergencyStatus.DISPATCHED, moderate.getEmergencyStatus());
        assertEquals(EmergencyStatus.DISPATCHED, normal.getEmergencyStatus());
    }

    @Test
    public void testCriticalGetsPriorityOverNormalWhenWaiting() {
        engine.registerAmbulance(ambulance("B1", AmbulanceType.BASIC, 0, 0));
        EmergencyRequest normal = request("P1", EmergencyPriority.NORMAL, 1, 1);
        EmergencyRequest critical = request("P2", EmergencyPriority.CRITICAL, 5, 5);

        engine.submitEmergencyRequest(normal);
        engine.submitEmergencyRequest(critical);

        assertEquals(EmergencyStatus.DISPATCHED, normal.getEmergencyStatus());
        assertEquals(EmergencyStatus.AVAILABLE, critical.getEmergencyStatus());
        assertEquals("P2", engine.getWaitingQueue().peek().getPatientId());
    }

    @Test
    public void testNearestCompatibleAmbulanceIsSelected() {
        engine.registerAmbulance(ambulance("FAR", AmbulanceType.BASIC, 10, 10));
        engine.registerAmbulance(ambulance("NEAR", AmbulanceType.BASIC, 1, 1));

        EmergencyRequest r = request("P1", EmergencyPriority.NORMAL, 2, 2);
        engine.submitEmergencyRequest(r);

        assertEquals("NEAR", r.getAmbulanceId());
        assertEquals(1.0, r.getEstimatedDistance(), 0.0001);
        assertEquals(1.2, r.getEstimatedArrivalTimeMinutes(), 0.0001);
    }

    @Test
    public void testCriticalRequiresIcuAmbulance() {
        engine.registerAmbulance(ambulance("B1", AmbulanceType.BASIC, 0, 0));
        engine.registerAmbulance(ambulance("A1", AmbulanceType.ADVANCED_LIFE_SUPPORT, 0, 0));

        EmergencyRequest r = request("P1", EmergencyPriority.CRITICAL, 0, 0);
        engine.submitEmergencyRequest(r);

        assertNull(r.getAmbulanceId());
        assertEquals(1, engine.getWaitingQueue().size());
    }

    @Test
    public void testHighCanUseAdvancedLifeSupportOrIcu() {
        engine.registerAmbulance(ambulance("A1", AmbulanceType.ADVANCED_LIFE_SUPPORT, 5, 5));
        EmergencyRequest r = request("P1", EmergencyPriority.HIGH, 5, 5);
        engine.submitEmergencyRequest(r);
        assertEquals("A1", r.getAmbulanceId());
    }

    @Test
    public void testAmbulanceCannotBeAssignedTwice() {
        Ambulance a = ambulance("B1", AmbulanceType.BASIC, 0, 0);
        engine.registerAmbulance(a);
        EmergencyRequest first = request("P1", EmergencyPriority.NORMAL, 0, 0);
        EmergencyRequest second = request("P2", EmergencyPriority.NORMAL, 0, 0);

        engine.submitEmergencyRequest(first);
        engine.submitEmergencyRequest(second);

        assertEquals("B1", first.getAmbulanceId());
        assertNull(second.getAmbulanceId());
        assertEquals(1, engine.getWaitingQueue().size());
        assertFalse(a.isAvailable());
    }

    @Test
    public void testWaitingRequestAutomaticallyAllocatedWhenAmbulanceRegistered() {
        EmergencyRequest r = request("P1", EmergencyPriority.NORMAL, 2, 2);
        engine.submitEmergencyRequest(r);
        assertEquals(1, engine.getWaitingQueue().size());

        engine.registerAmbulance(ambulance("B1", AmbulanceType.BASIC, 2, 2));

        assertEquals("B1", r.getAmbulanceId());
        assertEquals(EmergencyStatus.DISPATCHED, r.getEmergencyStatus());
        assertEquals(0, engine.getWaitingQueue().size());
    }

    @Test
    public void testCompleteAmbulanceStateWorkflow() {
        Ambulance a = ambulance("B1", AmbulanceType.BASIC, 0, 0);
        engine.registerAmbulance(a);
        EmergencyRequest r = request("P1", EmergencyPriority.NORMAL, 1, 1);
        engine.submitEmergencyRequest(r);

        assertEquals(EmergencyStatus.DISPATCHED, r.getEmergencyStatus());
        engine.transitionAmbulanceState("B1", EmergencyStatus.EN_ROUTE, 0.5, 0.5);
        assertEquals(EmergencyStatus.EN_ROUTE, r.getEmergencyStatus());
        engine.transitionAmbulanceState("B1", EmergencyStatus.PATIENT_PICKED_UP, 1, 1);
        assertEquals(EmergencyStatus.PATIENT_PICKED_UP, r.getEmergencyStatus());
        engine.transitionAmbulanceState("B1", EmergencyStatus.HOSPITAL_ARRIVED, 5, 5);
        assertEquals(EmergencyStatus.HOSPITAL_ARRIVED, r.getEmergencyStatus());
        assertTrue(a.isAvailable());
    }

    @Test
    public void testInvalidStateTransitionIsRejected() {
        engine.registerAmbulance(ambulance("B1", AmbulanceType.BASIC, 0, 0));
        EmergencyRequest r = request("P1", EmergencyPriority.NORMAL, 0, 0);
        engine.submitEmergencyRequest(r);

        assertThrows(IllegalStateException.class, () ->
                engine.transitionAmbulanceState("B1", EmergencyStatus.HOSPITAL_ARRIVED, 0, 0));
    }

    @Test
    public void testUnknownAmbulanceThrowsException() {
        assertThrows(AmbulanceUnavailableException.class, () ->
                engine.transitionAmbulanceState("UNKNOWN", EmergencyStatus.EN_ROUTE, 0, 0));
    }

    @Test
    public void testInvalidRequestWithBlankPatientId() {
        EmergencyRequest r = new EmergencyRequest("   ", "Accident", EmergencyPriority.HIGH, 0, 0, "Hospital");
        assertThrows(InvalidEmergencyRequestException.class, () -> engine.submitEmergencyRequest(r));
    }

    @Test
    public void testInvalidRequestWithBlankEmergencyType() {
        EmergencyRequest r = new EmergencyRequest("P1", "", EmergencyPriority.HIGH, 0, 0, "Hospital");
        assertThrows(InvalidEmergencyRequestException.class, () -> engine.submitEmergencyRequest(r));
    }

    @Test
    public void testInvalidRequestWithBlankHospital() {
        EmergencyRequest r = new EmergencyRequest("P1", "Accident", EmergencyPriority.HIGH, 0, 0, " ");
        assertThrows(InvalidEmergencyRequestException.class, () -> engine.submitEmergencyRequest(r));
    }

    @Test
    public void testNullRequestIsRejected() {
        assertThrows(InvalidEmergencyRequestException.class, () -> engine.submitEmergencyRequest(null));
    }

    @Test
    public void testDuplicateAmbulanceRegistrationIsRejected() {
        engine.registerAmbulance(ambulance("B1", AmbulanceType.BASIC, 0, 0));
        assertThrows(IllegalArgumentException.class, () ->
                engine.registerAmbulance(ambulance("B1", AmbulanceType.BASIC, 1, 1)));
    }

    @Test
    public void testNullAmbulanceRegistrationIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> engine.registerAmbulance(null));
    }

    @Test
    public void testEmergencyHistoryRecordsEveryValidRequest() {
        engine.registerAmbulance(ambulance("B1", AmbulanceType.BASIC, 0, 0));
        EmergencyRequest r1 = request("P1", EmergencyPriority.NORMAL, 0, 0);
        EmergencyRequest r2 = request("P2", EmergencyPriority.MODERATE, 1, 1);
        engine.submitEmergencyRequest(r1);
        engine.submitEmergencyRequest(r2);

        List<EmergencyRequest> history = engine.getEmergencyHistory();
        assertEquals(2, history.size());
        assertEquals("P1", history.get(0).getPatientId());
        assertEquals("P2", history.get(1).getPatientId());
    }

    @Test
    public void testWaitingQueueReturnsCopy() {
        EmergencyRequest r = request("P1", EmergencyPriority.NORMAL, 0, 0);
        engine.submitEmergencyRequest(r);
        assertEquals(1, engine.getWaitingQueue().size());
        engine.getWaitingQueue().clear();
        assertEquals(1, engine.getWaitingQueue().size());
    }
}
