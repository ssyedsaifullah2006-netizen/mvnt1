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
        if (ambulance == null) {
            throw new IllegalArgumentException("Ambulance object cannot be null.");
        }
        fleet.add(ambulance);
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
        if (request == null || 
            request.getPatientId() == null || request.getPatientId().trim().isEmpty() ||
            request.getEmergencyType() == null || request.getEmergencyType().trim().isEmpty() ||
            request.getDestinationHospital() == null || request.getDestinationHospital().trim().isEmpty() ||
            request.getPriority() == null) {
            throw new InvalidEmergencyRequestException("Invalid emergency request data provided.");
        }
    }

    private Ambulance findOptimalAmbulance(EmergencyRequest request) {
        Ambulance optimal = null;
        double shortestDistance = Double.MAX_VALUE;

        for (Ambulance ambulance : fleet) {
            if (ambulance.isAvailable() && isTypeCompatible(ambulance.getAmbulanceType(), request.getPriority())) {
                double distance = calculateDistance(ambulance.getCurrentX(), ambulance.getCurrentY(), request.getPickupX(), request.getPickupY());
                if (distance < shortestDistance) {
                    shortestDistance = distance;
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
        ambulance.setAvailable(false); // Prevents duplicate assignment
        ambulance.setAvailable(false);
        
        request.setAmbulanceId(ambulance.getAmbulanceId());
        request.setEmergencyStatus(EmergencyStatus.DISPATCHED);
        
        double distance = calculateDistance(ambulance.getCurrentX(), ambulance.getCurrentY(), request.getPickupX(), request.getPickupY());
        request.setEstimatedDistance(distance);
        
        double eta = (distance / AVERAGE_SPEED_KMPH) * 60.0;
        request.setEstimatedArrivalTimeMinutes(eta);
    }

    public synchronized void transitionAmbulanceState(String ambulanceId, EmergencyStatus targetStatus, double updatedX, double updatedY) {
        Ambulance ambulance = fleet.stream()
                .filter(a -> a.getAmbulanceId().equals(ambulanceId))
                .findFirst()
                .orElseThrow(() -> new AmbulanceUnavailableException("Ambulance ID not found in system fleet registration."));

        ambulance.setCurrentLocation(updatedX, updatedY);

        EmergencyRequest activeRequest = emergencyHistory.stream()
                .filter(r -> ambulanceId.equals(r.getAmbulanceId()) && r.getEmergencyStatus() != EmergencyStatus.HOSPITAL_ARRIVED)
                .findFirst()
                .orElse(null);

        if (activeRequest != null) {
            activeRequest.setEmergencyStatus(targetStatus);
        }

        if (targetStatus == EmergencyStatus.HOSPITAL_ARRIVED) {
            ambulance.setAvailable(true);
            processWaitingQueue();
        }
    }

    private void processWaitingQueue() {
        while (!waitingQueue.isEmpty()) {
            EmergencyRequest topPriorityRequest = waitingQueue.peek();
            Ambulance availableAmbulance = findOptimalAmbulance(topPriorityRequest);

            if (availableAmbulance != null) {
                waitingQueue.poll();
                allocateAmbulance(topPriorityRequest, availableAmbulance);
            } else {
                break; 
            }
        }
    }

    private double calculateDistance(double x1, double y1, double x2, double y2) {
        return Math.sqrt(Math.pow(x1 - x2, 2) + Math.pow(y1 - y2, 2));
    }

    public List<EmergencyRequest> getEmergencyHistory() { return new ArrayList<>(emergencyHistory); }
    public PriorityQueue<EmergencyRequest> getWaitingQueue() { return waitingQueue; }
    public List<Ambulance> getFleet() { return fleet; }
}
