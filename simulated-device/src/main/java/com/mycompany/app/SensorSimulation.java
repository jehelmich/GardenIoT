package com.mycompany.app;

import com.microsoft.azure.sdk.iot.device.Message;
import com.mycompany.app.MessageSender.EventCallback;
import com.mycompany.app.MessageSender.TelemetryDataPoint;

import java.time.LocalDateTime;
import java.util.Random;

/**
 * Created by Jan on 17.07.2017.
 */
public class SensorSimulation {
    private double minTemperature;
    private double minHumidity;
    private double lastHumid;
    private double lastTemp;
    private Random rand;

    public SensorSimulation (double minTemp, double minHumid) {
        rand = new Random();
        minTemperature = minTemp;
        lastTemp = 22.0;
        minHumidity = minHumid;
        lastHumid = 26.0;
    }

    public void waterPlant() {
        lastHumid = 100.0;
    }

    private void nextHumidity() {
        if (lastHumid > minHumidity) {
            lastHumid = lastHumid - (lastTemp*0.01);
        } else {
            System.out.println("Minimal Humidity reached. Watering needed!");
        }
    }

    private void nextTemp() {
        if (lastTemp >= minTemperature) {
            double randValue = rand.nextDouble();
            if (randValue >= 0.5) {
                lastTemp += (randValue*0.15);
            } else {
                lastTemp -= (randValue*0.15);
            }
        } else {
            lastTemp += (rand.nextDouble()*0.3);
        }
    }

    public Double[] getSensorData() {
        nextTemp();
        nextHumidity();
        Double[] data = {lastTemp, lastHumid};
        return data;
    }


}
