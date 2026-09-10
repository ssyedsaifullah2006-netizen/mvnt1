package com.dispatch.model;

public class Ambulance {
    private final String ambulanceId;
    private final AmbulanceType ambulanceType;
    private final String driverName;
    private boolean isAvailable;
    private double currentX;
    private double currentY;

    public Ambulance(String ambulanceId, AmbulanceType ambulanceType, String driverName, double currentX, double currentY) {
        this.ambulanceId = ambulanceId;
        this.ambulanceType = ambulanceType;
        this.driverName = driverName;
        this.currentX = currentX;
        this.currentY = currentY;
        this.isAvailable = true;
    }

    public String getAmbulanceId() { return ambulanceId; }
    public AmbulanceType getAmbulanceType() { return ambulanceType; }
    public String getDriverName() { return driverName; }
    public boolean isAvailable() { return isAvailable; }
    public void setAvailable(boolean available) { isAvailable = available; }
    public double getCurrentX() { return currentX; }
    public double getCurrentY() { return currentY; }
    public void setCurrentLocation(double x, double y) { this.currentX = x; this.currentY = y; }
}
