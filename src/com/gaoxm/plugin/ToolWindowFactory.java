/*
 *    Copyright (C) 2016 Björn Quentin
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *      http://www.apache.org/licenses/LICENSE-2.0
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.gaoxm.plugin;

import com.android.ddmlib.*;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.fileEditor.OpenFileDescriptor;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.ComboBox;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.wm.ToolWindow;
import com.intellij.psi.JavaPsiFacade;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.search.PsiShortNamesCache;
import com.intellij.ui.content.Content;
import com.intellij.ui.content.ContentFactory;
import org.jetbrains.android.sdk.AndroidSdkUtils;
import org.jetbrains.annotations.NotNull;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionListener;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.text.MessageFormat;
import java.util.ResourceBundle;
import java.util.Vector;

/**
 * Android Device Controller Plugin for Android Studio
 */
public class ToolWindowFactory implements com.intellij.openapi.wm.ToolWindowFactory {

    private static ResourceBundle resourceBundle = ResourceBundle.getBundle("com.gaoxm.plugin.res.Plugin");
    private ComboBox devices;
    private AndroidDebugBridge adBridge;


    private static class StringShellOutputReceiver implements IShellOutputReceiver {
        private StringBuffer result = new StringBuffer();

        void reset() {
            result.delete(0, result.length());
        }

        String getResult() {
            return result.toString();
        }

        @Override
        public void addOutput(byte[] bytes, int i, int i1) {
            try {
                result.append(new String(bytes, i, i1, "UTF-8"));
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }

        @Override
        public void flush() {
        }

        @Override
        public boolean isCancelled() {
            return false;
        }
    }

    private StringShellOutputReceiver rcv = new StringShellOutputReceiver();

    private AndroidDebugBridge.IDeviceChangeListener deviceChangeListener = new AndroidDebugBridge.IDeviceChangeListener() {
        @Override
        public void deviceConnected(IDevice iDevice) {
            updateDeviceComboBox();
        }

        @Override
        public void deviceDisconnected(IDevice iDevice) {
            updateDeviceComboBox();
        }

        @Override
        public void deviceChanged(IDevice iDevice, int i) {
        }
    };

    private ActionListener deviceSelectedListener = e -> updateFromDevice();

    private boolean userAction = false;
    private JButton goToActivityButton;
    private JButton goToFragmentButton;

    public ToolWindowFactory() {
    }

    // Create the tool window content.
    public void createToolWindowContent(@NotNull final Project project, @NotNull final ToolWindow toolWindow) {
        ContentFactory contentFactory = ContentFactory.SERVICE.getInstance();
        JPanel framePanel = createPanel(project);
        disableAll();

        AndroidDebugBridge adb = AndroidSdkUtils.getDebugBridge(project);
        if (adb == null) {
            return;
        }

        if(adb.isConnected()){
            ToolWindowFactory.this.adBridge = adb;
            Logger.getInstance(ToolWindowFactory.class).info("Successfully obtained debug bridge");
            AndroidDebugBridge.addDeviceChangeListener(deviceChangeListener);
            updateDeviceComboBox();
        } else {
            Logger.getInstance(ToolWindowFactory.class).info("Unable to obtain debug bridge");
            String msg = MessageFormat.format(resourceBundle.getString("error.message.adb"), "");
            Messages.showErrorDialog(msg, resourceBundle.getString("error.title.adb"));
        }

        Content content = contentFactory.createContent(framePanel, "", false);
        toolWindow.getContentManager().addContent(content);
    }

    @NotNull
    private JPanel createPanel(@NotNull Project project) {
        // Create Panel and Content
        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.NONE;
        c.anchor = GridBagConstraints.LINE_START;
        c.insets = new Insets(6,0,0,0);

        devices = new ComboBox(new String[]{resourceBundle.getString("device.none")});

        c.gridx = 0;
        c.gridy = 0;
        panel.add(new JLabel("Device"), c);
        c.gridx = 1;
        c.gridy = 0;
        panel.add(devices, c);


        goToActivityButton = new JButton(resourceBundle.getString("button.goto_activity"));
        c.gridx = 0;
        c.gridy = 2;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(goToActivityButton, c);

        goToActivityButton.addActionListener(e -> ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
            ProgressManager.getInstance().getProgressIndicator().setIndeterminate(true);
            userAction = true;
            final String result = executeShellCommand("dumpsys activity top", false);
            userAction = false;

            if (result == null) {
                return;
            }

            ApplicationManager.getApplication().invokeLater(() -> {
                String activity = result.substring(result.lastIndexOf("ACTIVITY ") + 9);
                activity = activity.substring(0, activity.indexOf(" "));
                String pkg = activity.substring(0, activity.indexOf("/"));
                String clz = activity.substring(activity.indexOf("/") + 1);
                if (clz.startsWith(".")) {
                    clz = pkg + clz;
                }

                GlobalSearchScope scope = GlobalSearchScope.allScope(project);

                PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass(clz, scope);

                if (psiClass != null) {
                    FileEditorManager fileEditorManager = FileEditorManager.getInstance(project);
                    //Open the file containing the class
                    VirtualFile vf = psiClass.getContainingFile().getVirtualFile();
                    //Jump there
                    new OpenFileDescriptor(project, vf, 1, 0).navigateInEditor(project, false);
                } else {
                    Messages.showMessageDialog(project, clz, resourceBundle.getString("error.class_not_found"), Messages.getWarningIcon());
                    return;
                }

            });

        }, resourceBundle.getString("setting.values.title"), false, null));

        goToFragmentButton = new JButton(resourceBundle.getString("button.goto_fragment"));
        c.gridx = 0;
        c.gridy = 3;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        panel.add(goToFragmentButton, c);

        goToFragmentButton.addActionListener(e -> ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
            ProgressManager.getInstance().getProgressIndicator().setIndeterminate(true);
            userAction = true;
            final String result = executeShellCommand("dumpsys activity top | grep '#0: ' | tail -n 1", false);
            userAction = false;

            if (result == null) {
                return;
            }

            ApplicationManager.getApplication().invokeLater(() -> {
//                Messages.showMessageDialog(project, result, resourceBundle.getString("error.class_not_found"), Messages.getWarningIcon());
                String fragment = result.substring(result.lastIndexOf("#0: ") + 4);
                fragment = fragment.substring(0, fragment.indexOf("{"));

                GlobalSearchScope scope = GlobalSearchScope.allScope(project);

                @NotNull PsiClass[] classes = PsiShortNamesCache.getInstance(project).getClassesByName(fragment, scope);
                if (classes.length > 0) {
                    String qualifiedName = classes[0].getQualifiedName();
                    PsiClass psiClass = JavaPsiFacade.getInstance(project).findClass(qualifiedName, scope);
                    if (psiClass != null) {
                        //Open the file containing the class
                        VirtualFile vf = psiClass.getContainingFile().getVirtualFile();
                        //Jump there
                        new OpenFileDescriptor(project, vf, 1, 0).navigateInEditor(project, false);
                    } else {
                        Messages.showMessageDialog(project, qualifiedName, resourceBundle.getString("error.class_not_found"), Messages.getWarningIcon());
                        return;
                    }
                } else {
                    Messages.showMessageDialog(project, fragment+"-->getClassesByName失败", resourceBundle.getString("error.class_not_found"), Messages.getWarningIcon());
                }



            });

        }, resourceBundle.getString("setting.values.title"), false, null));

        JPanel framePanel = new JPanel(new BorderLayout());
        framePanel.add(panel, BorderLayout.NORTH);
        return framePanel;
    }

    private void updateFromDevice() {
        ProgressManager.getInstance().runProcessWithProgressSynchronously(() -> {
            ProgressManager.getInstance().getProgressIndicator().setIndeterminate(true);
            IDevice selectedDevice = getSelectedDevice();
            if (selectedDevice != null) {
                SwingUtilities.invokeLater(() -> enableAll());
            } else {
                SwingUtilities.invokeLater(() -> disableAll());
            }
        }, resourceBundle.getString("initializing.device.message"), false, null);
    }

    private void disableAll() {
        goToActivityButton.setEnabled(false);
        goToFragmentButton.setEnabled(false);
    }

    private void enableAll() {
        goToActivityButton.setEnabled(true);
        goToFragmentButton.setEnabled(true);
    }

    @SuppressWarnings("unchecked")
    private void updateDeviceComboBox() {
        devices.removeActionListener(deviceSelectedListener);
        String selectedDevice = (String) devices.getSelectedItem();

        IDevice[] devs = adBridge.getDevices();
        Vector devicesList = new Vector();
        devicesList.add("-- none --");
        for (IDevice device : devs) {
            devicesList.add(device.toString());
        }
        devices.setModel(new DefaultComboBoxModel<>(devicesList));

        if (devicesList.size() == 1) {
            disableAll();
        } else {
            devices.setSelectedItem(selectedDevice);

            devices.setSelectedItem(devices.getSelectedItem());
            if (devices.getSelectedIndex() == 0) {
                disableAll();
            } else {
                enableAll();
            }
        }

        devices.addActionListener(deviceSelectedListener);
    }

    private String executeShellCommand(String cmd, boolean doPoke) {
        if (!userAction) {
            return null;
        }

        if (devices.getSelectedIndex() == 0) {
            return null;
        }

        String res = null;
        String selDevice = (String) devices.getSelectedItem();
        for (IDevice device : adBridge.getDevices()) {
            if (selDevice.equals(device.toString())) {

                try {
                    rcv.reset();
                    device.executeShellCommand(cmd, rcv);
                    res = rcv.getResult();
                    if (doPoke) {
                        device.executeShellCommand("am start -a POKESYSPROPS", rcv);
                    }
                } catch (TimeoutException | AdbCommandRejectedException | ShellCommandUnresponsiveException | IOException e1) {
                    e1.printStackTrace();
                }

            }
        }
        return res;
    }

    private IDevice getSelectedDevice() {
        String selDevice = (String) devices.getSelectedItem();
        for (IDevice device : adBridge.getDevices()) {
            if (selDevice.equals(device.toString())) {
                return device;
            }
        }
        return null;
    }

}