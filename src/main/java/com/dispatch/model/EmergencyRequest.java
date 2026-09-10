package com.dispatch.model;

public class EmergencyRequest implements Comparable<EmergencyRequest> {
    private final String patientId;
    private final String emergencyType;
    private final EmergencyPriority priority;
    private final double pickupX;
    private final double pickupY;
    private final String destinationHospital;
    
    private String ambulanceId;
    private EmergencyStatus emergencyStatus;
    private double estimatedDistance;
    private double estimatedArrivalTimeMinutes;

    public EmergencyRequest(String patientId, String emergencyType, EmergencyPriority priority, 
                            double pickupX, double pickupY, String destinationHospital) {
        this.patientId = patientId;
        this.emergencyType = emergencyType;
        this.priority = priority;
        this.pickupX = pickupX;
        this.pickupY = pickupY;
        this.destinationHospital = destinationHospital;
        this.emergencyStatus = EmergencyStatus.AVAILABLE; 
    }

    @Override
    public int compareTo(EmergencyRequest other) {
        return Integer.compare(this.priority.getRank(), other.priority.getRank());
    }

    // Getters and Setters
    public String getPatientId() { return patientId; }
    public String getEmergencyType() { return emergencyType; }
    public EmergencyPriority getPriority() { return priority; }
    public double getPickupX() { return pickupX; }
    public double getPickupY() { return pickupY; }
    public String getDestinationHospital() { return destinationHospital; }
    public String getAmbulanceId() { return ambulanceId; }
    public void setAmbulanceId(String ambulanceId) { this.ambulanceId = ambulanceId; }
    public EmergencyStatus getEmergencyStatus() { return emergencyStatus; }
    public void setEmergencyStatus(EmergencyStatus emergencyStatus) { this.emergencyStatus = emergencyStatus; }
    public double getEstimatedDistance() { return estimatedDistance; }
    public void setEstimatedDistance(double estimatedDistance) { this.estimatedDistance = estimatedDistance; }
    public double getEstimatedArrivalTimeMinutes() { return estimatedArrivalTimeMinutes; }
    public void setEstimatedArrivalTimeMinutes(double estimatedArrivalTimeMinutes) { this.estimatedArrivalTimeMinutes = estimatedArrivalTimeMinutes; }
}
