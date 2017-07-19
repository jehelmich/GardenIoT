package com.mycompany.app;

/**
 * Created by Jan on 17.07.2017.
 */
public class Analyser {
    private double humidTrigger;

    public Analyser (double pHumidTrigger) {
        humidTrigger = pHumidTrigger;
    }

    public boolean toWater(String s) {
        String[] tokens = s.split(",");

        for (String token : tokens) {
            if (token.contains("humidity")) {
                token = token.substring(token.indexOf(":")+1, token.length()-1);
                Double humidity = Double.valueOf(token);
                System.out.println("Humidity: " + humidity);
                System.out.println("Humid trigger: " + humidTrigger);

                if (humidity < humidTrigger) {
                    System.out.println("Sensor humidity low. Initialize watering!");
                    DeviceMessenger.sendDeviceMethod(App.iotHubConnectionString, App.deviceId,"water", App.responseTimeout, App.connectTimeout);
                }
            }
        }


        return false;
    }
}
