package com.mycompany.app.DeviceManagement;

import com.microsoft.azure.sdk.iot.device.DeviceTwin.DeviceMethodData;

/**
 * Created by Jan on 17.07.2017.
 */
public class DirectMethodCallback implements com.microsoft.azure.sdk.iot.device.DeviceTwin.DeviceMethodCallback
{
    private static final int METHOD_SUCCESS = 200;
    private static final int METHOD_NOT_DEFINED = 404;

    @Override
    public DeviceMethodData call(String methodName, Object methodData, Object context)
    {
        DeviceMethodData deviceMethodData;
        switch (methodName)
        {
            case "reboot" :
            {
                int status = METHOD_SUCCESS;
                System.out.println("Received reboot request");
                deviceMethodData = new DeviceMethodData(status, "Started reboot");
                RebootDeviceThread rebootThread = new RebootDeviceThread();
                Thread t = new Thread(rebootThread);
                t.start();
                break;
            }
            case "water" :
            {
                int status = METHOD_SUCCESS;
                System.out.println("Received watering request");
                deviceMethodData = new DeviceMethodData(status, "Started watering");

                WaterDeviceThread waterThread = new WaterDeviceThread();
                Thread t = new Thread(waterThread);
                t.start();

                break;
            }
            case "test" :
            {
                int status = METHOD_SUCCESS;
                System.out.println("Test successful!");
                deviceMethodData = new DeviceMethodData(status, "Tested ...");
                RebootDeviceThread rebootThread = new RebootDeviceThread();
                Thread t = new Thread(rebootThread);
                t.start();
                break;
            }
            default:
            {
                int status = METHOD_NOT_DEFINED;
                deviceMethodData = new DeviceMethodData(status, "Not defined direct method " + methodName);
            }
        }
        return deviceMethodData;
    }
}
