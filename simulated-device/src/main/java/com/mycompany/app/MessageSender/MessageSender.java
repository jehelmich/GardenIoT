package com.mycompany.app.MessageSender;

import com.microsoft.azure.sdk.iot.device.DeviceClient;
import com.microsoft.azure.sdk.iot.device.Message;
import com.mycompany.app.App;
import com.mycompany.app.SensorSimulation;

import java.time.LocalDateTime;
import java.util.Random;

/**
 * Created by Jan on 17.07.2017.
 *
 *
 * Todo: Add the integration of the physical sensors.
 * The aim is that the device is sending real world data instead of generating arbitraty one
 */
public class MessageSender implements Runnable {
    String deviceId;
    DeviceClient client;

    public MessageSender(String pID, DeviceClient pDC) {
        deviceId = pID;
        client = pDC;
    }

    public void run()  {
        try {
            Random rand = new Random();
            SensorSimulation sm = App.getSm();

            while (true) {
                Double[] sensorData = sm.getSensorData();

                double currentTemperature = sensorData[0];
                double currentHumidity = sensorData[1];
                TelemetryDataPoint telemetryDataPoint = new TelemetryDataPoint();
                telemetryDataPoint.deviceId = deviceId;
                telemetryDataPoint.localDateTime = LocalDateTime.now().toString();
                telemetryDataPoint.temperature = currentTemperature;
                telemetryDataPoint.humidity = currentHumidity;

                String msgStr = telemetryDataPoint.serialize();
                Message msg = new Message(msgStr);
                msg.setProperty("temperatureAlert", (currentTemperature > 30) ? "true" : "false");
                msg.setMessageId(java.util.UUID.randomUUID().toString());
                System.out.println("Sending: " + msgStr);

                Object lockobj = new Object();
                EventCallback callback = new EventCallback();
                client.sendEventAsync(msg, callback, lockobj);

                synchronized (lockobj) {
                    lockobj.wait();
                }
                Thread.sleep(5000);
            }
        } catch (InterruptedException e) {
            System.out.println("Finished.");
        }
    }
}
