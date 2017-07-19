package com.mycompany.app;

import com.microsoft.azure.sdk.iot.service.devicetwin.DeviceMethod;
import com.microsoft.azure.sdk.iot.service.devicetwin.MethodResult;
import com.microsoft.azure.sdk.iot.service.exceptions.IotHubException;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Created by Jan on 17.07.2017.
 */
public class DeviceMessenger {

    public static void sendDeviceMethod(String iotHubConnectionString, String deviceId, String methodName, Long responseTimeout, Long connectTimeout) {
        System.out.println("Starting Messaging to Device " + deviceId);
        DeviceMethod methodClient = null;
        try {
            methodClient = DeviceMethod.createFromConnectionString(iotHubConnectionString);
            System.out.println("Invoke " + methodName + " direct method");
            MethodResult result = methodClient.invoke(deviceId, methodName, responseTimeout, connectTimeout, null);

            if(result == null)
            {
                throw new IOException("Invoke direct method " + methodName + " returns null");
            }
            System.out.println("Invoked " + methodName + " on device");
            System.out.println("Status for device:   " + result.getStatus());
            System.out.println("Message from device: " + result.getPayload());
        }
        catch (IotHubException e)
        {
            System.out.println(e.getMessage());
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

}
