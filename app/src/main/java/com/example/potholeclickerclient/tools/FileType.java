package com.example.potholeclickerclient.tools;

public enum FileType {
    LABELS("labels"),
    ACCELEROMETER("accel"),
    GYROSCOPE("gyro");

    private final String suffix;

    FileType(String suffix) {
        this.suffix = suffix;
    }

    public String getSuffix() {
        return suffix;
    }

    public String getFileHeader()
    {
        return switch (this) {
            case LABELS -> "timestamp,lat,lon,label";
            case ACCELEROMETER, GYROSCOPE ->
                    "timestamp,lat,lon,sensor_timestamp,sensor_type,x,y,z,raw_data";
        };
    }

    public static FileType fromSensorName(String sensorName)
    {
        return switch (sensorName) {
            case "Accelerometer" -> ACCELEROMETER;
            case "Gyroscope" -> GYROSCOPE;
            default -> throw new IllegalArgumentException("Unknown sensor name: " + sensorName);
        };
    }
}
