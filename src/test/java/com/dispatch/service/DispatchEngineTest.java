package com.dispatch.service;

import com.dispatch.exception.InvalidEmergencyRequestException;
import com.dispatch.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class DispatchEngineTest {
    private DispatchEngine engine;

    @BeforeEach
    public void setup() {
        engine = new DispatchEngine();
    }

    @Test
    public void testStrictPriorityAndQueueAllocationFlow() {
        Ambulance basicAmbulance = new Ambulance("AMB-BASIC-1", AmbulanceType.BASIC, "Officer Smith", 0, 0);
        engine.registerAmbulance(basicAmbulance);

        EmergencyRequest normalReq = new EmergencyRequest("P-001", "Minor Injury", EmergencyPriority.NORMAL, 3, 4, "North Wing Hospital");
        EmergencyRequest criticalReq = new EmergencyRequest("P-002", "Severe Spasms", EmergencyPriority.CRITICAL, 10, 10, "Central ICU Complex");

        // Basic picks up normal request first
        engine.submitEmergencyRequest(normalReq);
        // Critical request is pushed to waiting queue since no ICU asset is registered
        engine.submitEmergencyRequest(criticalReq);

        assertEquals(EmergencyStatus.DISPATCHED, normalReq.getEmergencyStatus());
        assertEquals(1, engine.getWaitingQueue().size());

        // Register matching ICU ambulance
        Ambulance icuAmbulance = new Ambulance("AMB-ICU-1", AmbulanceType.ICU, "Medic Adams", 10, 10);
        engine.registerAmbulance(icuAmbulance);

        // Complete normal ambulance workflow to drop off location -> triggers auto-reallocation check
        engine.transitionAmbulanceState("AMB-BASIC-1", EmergencyStatus.HOSPITAL_ARRIVED, 3, 4);

        // Verify waiting queue systematically deployed the newly verified asset to the highest item
        assertEquals(EmergencyStatus.DISPATCHED, criticalReq.getEmergencyStatus());
        assertEquals("AMB-ICU-1", criticalReq.getAmbulanceId());
        assertEquals(0, engine.getWaitingQueue().size());
    }

    @Test
    public void testInvalidRequestExceptionTrigger() {
        assertThrows(InvalidEmergencyRequestException.class, () -> {
            EmergencyRequest badReq = new EmergencyRequest("", null, EmergencyPriority.CRITICAL, 0, 0, "General Hospital");
            engine.submitEmergencyRequest(badReq);
        });
    }
}
