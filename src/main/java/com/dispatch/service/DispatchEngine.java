package com.dispatch.service;

import com.dispatch.exception.AmbulanceUnavailableException;
import com.dispatch.exception.InvalidEmergencyRequestException;
import com.dispatch.model.*;

import java.util.*;

public class DispatchEngine {
    private final List<Ambulance> fleet = new ArrayList<>();
    private final PriorityQueue<EmergencyRequest> waitingQueue = new PriorityQueue<>();
    private final List<EmergencyRequest> emergencyHistory = new ArrayList<>();
    private static final double AVERAGE_SPEED_KMPH = 50.0;

    public synchronized void registerAmbulance(Ambulance ambulance) {
        if (ambulance == null || ambulance.getAmbulanceId() == null || ambulance.getAmbulanceId().trim().isEmpty()
                || ambulance.getAmbulanceType() == null || ambulance.getDriverName() == null
                || ambulance.getDriverName().trim().isEmpty()) {
            throw new IllegalArgumentException("Invalid ambulance details.");
        }
        if (fleet.stream().anyMatch(a -> a.getAmbulanceId().equals(ambulance.getAmbulanceId()))) {
            throw new IllegalArgumentException("Ambulance ID already registered.");
        }
        fleet.add(ambulance);
        processWaitingQueue();
    }

    public synchronized void submitEmergencyRequest(EmergencyRequest request) {
        validateRequest(request);
        emergencyHistory.add(request);

        Ambulance matchedAmbulance = findOptimalAmbulance(request);
        if (matchedAmbulance != null) {
            allocateAmbulance(request, matchedAmbulance);
        } else {
            waitingQueue.add(request);
        }
    }

    private void validateRequest(EmergencyRequest request) {
        if (request == null
                || isBlank(request.getPatientId())
                || isBlank(request.getEmergencyType())
                || isBlank(request.getDestinationHospital())
                || request.getPriority() == null
                || !Double.isFinite(request.getPickupX())
                || !Double.isFinite(request.getPickupY())) {
            throw new InvalidEmergencyRequestException("Invalid emergency request data provided.");
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private Ambulance findOptimalAmbulance(EmergencyRequest request) {
        Ambulance optimal = null;
        double bestDistance = Double.MAX_VALUE;

        for (Ambulance ambulance : fleet) {
            if (ambulance.isAvailable() && isTypeCompatible(ambulance.getAmbulanceType(), request.getPriority())) {
                double distance = calculateDistance(ambulance.getCurrentX(), ambulance.getCurrentY(),
                        request.getPickupX(), request.getPickupY());
                if (distance < bestDistance) {
                    bestDistance = distance;
                    optimal = ambulance;
                }
            }
        }
        return optimal;
    }

    private boolean isTypeCompatible(AmbulanceType type, EmergencyPriority priority) {
        if (priority == EmergencyPriority.CRITICAL) {
            return type == AmbulanceType.ICU;
        }
        if (priority == EmergencyPriority.HIGH) {
            return type == AmbulanceType.ADVANCED_LIFE_SUPPORT || type == AmbulanceType.ICU;
        }
        return true;
    }

    private void allocateAmbulance(EmergencyRequest request, Ambulance ambulance) {
        if (!ambulance.isAvailable()) {
            throw new AmbulanceUnavailableException("Ambulance is already assigned to an active emergency.");
        }

        ambulance.setAvailable(false);
        request.setAmbulanceId(ambulance.getAmbulanceId());
        request.setEmergencyStatus(EmergencyStatus.DISPATCHED);

        double distance = calculateDistance(ambulance.getCurrentX(), ambulance.getCurrentY(),
                request.getPickupX(), request.getPickupY());
        request.setEstimatedDistance(distance);
        request.setEstimatedArrivalTimeMinutes((distance / AVERAGE_SPEED_KMPH) * 60.0);
    }

    public synchronized void transitionAmbulanceState(String ambulanceId, EmergencyStatus targetStatus,
                                                       double updatedX, double updatedY) {
        Ambulance ambulance = fleet.stream()
                .filter(a -> a.getAmbulanceId().equals(ambulanceId))
                .findFirst()
                .orElseThrow(() -> new AmbulanceUnavailableException(
                        "Ambulance ID not found in system fleet registration."));

        if (targetStatus == null || !Double.isFinite(updatedX) || !Double.isFinite(updatedY)) {
            throw new IllegalArgumentException("Invalid ambulance state transition data.");
        }

        EmergencyRequest activeRequest = emergencyHistory.stream()
                .filter(r -> ambulanceId.equals(r.getAmbulanceId())
                        && r.getEmergencyStatus() != EmergencyStatus.HOSPITAL_ARRIVED)
                .findFirst()
                .orElse(null);

        if (activeRequest == null) {
            throw new AmbulanceUnavailableException("Ambulance has no active emergency assignment.");
        }

        if (!isValidTransition(activeRequest.getEmergencyStatus(), targetStatus)) {
            throw new IllegalStateException("Invalid emergency state transition from "
                    + activeRequest.getEmergencyStatus() + " to " + targetStatus + ".");
        }

        ambulance.setCurrentLocation(updatedX, updatedY);
        activeRequest.setEmergencyStatus(targetStatus);

        if (targetStatus == EmergencyStatus.HOSPITAL_ARRIVED) {
            ambulance.setAvailable(true);
            processWaitingQueue();
        }
    }

    private boolean isValidTransition(EmergencyStatus current, EmergencyStatus target) {
        if (current == EmergencyStatus.DISPATCHED) {
            return target == EmergencyStatus.EN_ROUTE;
        }
        if (current == EmergencyStatus.EN_ROUTE) {
            return target == EmergencyStatus.PATIENT_PICKED_UP;
        }
        if (current == EmergencyStatus.PATIENT_PICKED_UP) {
            return target == EmergencyStatus.HOSPITAL_ARRIVED;
        }
        return false;
    }

    private void processWaitingQueue() {
        while (!waitingQueue.isEmpty()) {
            EmergencyRequest topPriorityRequest = waitingQueue.peek();
            Ambulance availableAmbulance = findOptimalAmbulance(topPriorityRequest);

            if (availableAmbulance == null) {
                break;
            }

            waitingQueue.poll();
            allocateAmbulance(topPriorityRequest, availableAmbulance);
        }
    }

    private double calculateDistance(double x1, double y1, double x2, double y2) {
        return Math.sqrt(Math.pow(x1 - x2, 2) + Math.pow(y1 - y2, 2));
    }

    public List<EmergencyRequest> getEmergencyHistory() {
        return new ArrayList<>(emergencyHistory);
    }

    public PriorityQueue<EmergencyRequest> getWaitingQueue() {
        return new PriorityQueue<>(waitingQueue);
    }

    public List<Ambulance> getFleet() {
        return new ArrayList<>(fleet);
    }
}
