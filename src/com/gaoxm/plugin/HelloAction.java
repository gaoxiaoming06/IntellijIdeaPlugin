package com.gaoxm.plugin;

import com.android.ddmlib.IDevice;
import com.android.tools.idea.run.DeviceCount;
import com.android.tools.idea.run.DeviceSelectionUtils;
import com.android.tools.idea.run.TargetDeviceFilter;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.PlatformDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.android.facet.AndroidFacet;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.android.util.AndroidUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class HelloAction extends AnAction {

    public void actionPerformed(AnActionEvent event) {
        Project project = event.getData(PlatformDataKeys.PROJECT);
        IDevice device = getConnectedDevice(project);
        try {
            Messages.showInfoMessage("name: " + device.getName() + "\nBattery: " + device.getBattery().get() + "\nAbis: " + device.getAbis()
                    + "\nDensity: " + device.getDensity() + "\nVersion: " + device.getVersion(), "设备信息");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * 对象获取当前连接的设备
     */
    private IDevice getConnectedDevice(Project project) {
        //获取所有以连接的设备
        List<IDevice> devices = Arrays.asList(AndroidSdkUtils.getDebugBridge(project).getDevices());
        //如果设备数量大于1 则让用户选择一个设备
        if (devices.size() > 1) {
            AndroidFacet facet = AndroidUtils.getApplicationFacets(project).get(0);
            Collection<IDevice> iDevices = DeviceSelectionUtils.chooseRunningDevice(
                    facet,
                    new TargetDeviceFilter.UsbDeviceFilter(),
                    DeviceCount.SINGLE
            );
            //选择设备，并返回值
            return (IDevice) iDevices.toArray()[0];
        } else {
            return devices.get(0);
        }
    }
}

