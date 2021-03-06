/**
 * Copyright (c) 2014,2018 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 */
package org.eclipse.smarthome.binding.bluetooth.blukii.handler;

import org.eclipse.smarthome.binding.bluetooth.BeaconBluetoothHandler;
import org.eclipse.smarthome.binding.bluetooth.BluetoothDeviceListener;
import org.eclipse.smarthome.binding.bluetooth.blukii.BlukiiBindingConstants;
import org.eclipse.smarthome.binding.bluetooth.notification.BluetoothScanNotification;
import org.eclipse.smarthome.core.library.types.DecimalType;
import org.eclipse.smarthome.core.library.types.OnOffType;
import org.eclipse.smarthome.core.thing.Thing;
import org.eclipse.smarthome.core.util.HexUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The {@link BlukiiHandler} is responsible for handling commands, which are
 * sent to one of the channels.
 *
 * @author Kai Kreuzer - Initial contribution and API
 */
public class BlukiiHandler extends BeaconBluetoothHandler implements BluetoothDeviceListener {

    private final Logger logger = LoggerFactory.getLogger(BlukiiHandler.class);

    public BlukiiHandler(Thing thing) {
        super(thing);
    }

    @Override
    public void onScanRecordReceived(BluetoothScanNotification scanNotification) {
        byte[] data = scanNotification.getManufacturerData();
        if (data != null && data[0] == 0x4F) { // only data starting with 0x4F is Blukii-specific data
            logger.debug("Manufacturer data: {}", HexUtils.bytesToHex(scanNotification.getManufacturerData(), " "));

            int battery = scanNotification.getManufacturerData()[12] & 0x7F;
            updateState(BlukiiBindingConstants.CHANNEL_ID_BATTERY, new DecimalType(battery));

            if ((data[14] & 0x30) == 0x30) {
                processMagnetometerData(data);
            } else if ((data[14] & 0x10) == 0x10) {
                processEnvironmentData(data);
            } else if ((data[14] & 0x20) == 0x20) {
                processAccelerometerData(data);
            }
        }
        super.onScanRecordReceived(scanNotification);
    }

    private void processEnvironmentData(byte[] data) {
        double pressure = doubleByteToInt(data[15], data[16]) / 10;
        int luminance = doubleByteToInt(data[17], data[18]);
        int humidity = data[19] & 0xFF;
        double temperature = (data[20] & 0xFF) + (data[21] & 0xFF) / 100000000;

        updateState(BlukiiBindingConstants.CHANNEL_ID_TEMPERATURE, new DecimalType(temperature));
        updateState(BlukiiBindingConstants.CHANNEL_ID_HUMIDITY, new DecimalType(humidity));
        updateState(BlukiiBindingConstants.CHANNEL_ID_PRESSURE, new DecimalType(pressure));
        updateState(BlukiiBindingConstants.CHANNEL_ID_LUMINANCE, new DecimalType(luminance));
    }

    private void processAccelerometerData(byte[] data) {
        int range = data[15] & 0xFF;
        int x = (short) doubleByteToInt(data[16], data[17]) * (range / 2);
        int y = (short) doubleByteToInt(data[18], data[19]) * (range / 2);
        int z = (short) doubleByteToInt(data[20], data[21]) * (range / 2);

        double tiltX = 180 * Math.acos(x / Math.sqrt(Math.pow(x, 2) + Math.pow(y, 2) + Math.pow(z, 2))) / Math.PI;
        double tiltY = 180 * Math.acos(y / Math.sqrt(Math.pow(x, 2) + Math.pow(y, 2) + Math.pow(z, 2))) / Math.PI;
        double tiltZ = 180 * Math.acos(z / Math.sqrt(Math.pow(x, 2) + Math.pow(y, 2) + Math.pow(z, 2))) / Math.PI;

        updateState(BlukiiBindingConstants.CHANNEL_ID_TILTX, new DecimalType(tiltX));
        updateState(BlukiiBindingConstants.CHANNEL_ID_TILTY, new DecimalType(tiltY));
        updateState(BlukiiBindingConstants.CHANNEL_ID_TILTZ, new DecimalType(tiltZ));

        updateState(BlukiiBindingConstants.CHANNEL_ID_SWITCH, z > 0 ? OnOffType.ON : OnOffType.OFF);
    }

    @SuppressWarnings("unused")
    private void processMagnetometerData(byte[] data) {
        int x = (short) doubleByteToInt(data[16], data[17]);
        int y = (short) doubleByteToInt(data[18], data[19]);
        int z = (short) doubleByteToInt(data[20], data[21]);

        // future TODO: what kind of channel/feature should we offer with those values?
    }

    private int doubleByteToInt(byte b1, byte b2) {
        int i1 = b1 & 0xFF;
        int i2 = b2 & 0xFF;
        return (i1 * 0x100) + i2;
    }

}
