package healthradar.model;

public enum ZoneType {
    RESIDENTIAL(1.0, 1),   // multiplicateur de transmission, rayon de transmission
    WORK(1.4, 1),
    COMMERCIAL(1.2, 1),
    EDUCATION(1.5, 1),
    HEALTHCARE(1.8, 1),
    TRANSPORT(2.2, 1),
    EMPTY_SPACE(0.0, 0);

    private final double transmissionMultiplier;
    private final int radiusOverride;

    ZoneType(double transmissionMultiplier, int radiusOverride) {
        this.transmissionMultiplier = transmissionMultiplier;
        this.radiusOverride = radiusOverride;
    }

    public double getTransmissionMultiplier() { return transmissionMultiplier; }
    public int getRadiusOverride() { return radiusOverride; }
}