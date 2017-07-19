package com.mycompany.app.MessageSender;

import com.google.gson.Gson;

/**
 * Created by Jan on 17.07.2017.
 */
public class TelemetryDataPoint {
    public String deviceId;
    public String localDateTime;
    public double temperature;
    public double humidity;


    public String serialize() {
        Gson gson = new Gson();
        return gson.toJson(this);
    }
}
