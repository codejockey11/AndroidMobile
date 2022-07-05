package com.example.metars;

public class StationInfo {
    public String station_id;
    public double latitude;
    public double longitude;
    public double elevation_m;
    public String site;
    public String state;
    public String country;
    public String type;

    StationInfo() {
        station_id = "";
        latitude = 0.0;
        longitude = 0.0;
        elevation_m = 0.0;
        site = "";
        state = "";
        country = "";
        type = "";
    }
}
