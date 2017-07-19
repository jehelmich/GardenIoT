package com.mycompany.app.DeviceManagement;

import com.microsoft.azure.sdk.iot.device.IotHubEventCallback;
import com.microsoft.azure.sdk.iot.device.IotHubStatusCode;

/**
 * Created by Jan on 17.07.2017.
 */
public class DirectMethodStatusCallback implements IotHubEventCallback
{
    public void execute(IotHubStatusCode status, Object context)
    {
        System.out.println("IoT Hub responded to device method operation with status " + status.name());
    }
}