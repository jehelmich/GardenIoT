package com.mycompany.app.DeviceManagement;

import com.microsoft.azure.sdk.iot.device.DeviceTwin.PropertyCallBack;

/**
 * Created by Jan on 17.07.2017.
 */
public class PropertyCallback implements PropertyCallBack<String, String>
{
    public void PropertyCall(String propertyKey, String propertyValue, Object context)
    {
        System.out.println("PropertyKey:     " + propertyKey);
        System.out.println("PropertyKvalue:  " + propertyKey);
    }
}
