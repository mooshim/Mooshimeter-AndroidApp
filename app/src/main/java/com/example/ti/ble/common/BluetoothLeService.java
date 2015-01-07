/**************************************************************************************************
  Filename:       BluetoothLeService.java
  Revised:        $Date: 2013-09-09 16:23:36 +0200 (ma, 09 sep 2013) $
  Revision:       $Revision: 27674 $

  Copyright (c) 2013 - 2014 Texas Instruments Incorporated

  All rights reserved not granted herein.
  Limited License. 

  Texas Instruments Incorporated grants a world-wide, royalty-free,
  non-exclusive license under copyrights and patents it now or hereafter
  owns or controls to make, have made, use, import, offer to sell and sell ("Utilize")
  this software subject to the terms herein.  With respect to the foregoing patent
  license, such license is granted  solely to the extent that any such patent is necessary
  to Utilize the software alone.  The patent license shall not apply to any combinations which
  include this software, other than combinations with devices manufactured by or for TI (TI Devices).
  No hardware patent is licensed hereunder.

  Redistributions must preserve existing copyright notices and reproduce this license (including the
  above copyright notice and the disclaimer and (if applicable) source code license limitations below)
  in the documentation and/or other materials provided with the distribution

  Redistribution and use in binary form, without modification, are permitted provided that the following
  conditions are met:

    * No reverse engineering, decompilation, or disassembly of this software is permitted with respect to any
      software provided in binary form.
    * any redistribution and use are licensed by TI for use only with TI Devices.
    * Nothing shall obligate TI to provide you with source code for the software licensed and provided to you in object code.

  If software source code is provided to you, modification and redistribution of the source code are permitted
  provided that the following conditions are met:

    * any redistribution and use of the source code, including any resulting derivative works, are licensed by
      TI for use only with TI Devices.
    * any redistribution and use of any object code compiled from the source code and any resulting derivative
      works, are licensed by TI for use only with TI Devices.

  Neither the name of Texas Instruments Incorporated nor the names of its suppliers may be used to endorse or
  promote products derived from this software without specific prior written permission.

  DISCLAIMER.

  THIS SOFTWARE IS PROVIDED BY TI AND TIS LICENSORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING,
  BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
  IN NO EVENT SHALL TI AND TIS LICENSORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
  CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
  OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
  OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
  POSSIBILITY OF SUCH DAMAGE.


 **************************************************************************************************/
package com.example.ti.ble.common;

import java.util.List;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;

// import android.util.Log;

/**
 * Service for managing connection and data communication with a GATT server
 * hosted on a given Bluetooth LE device.
 */
public class BluetoothLeService extends Service {
	static final String TAG = "BluetoothLeService";

	public final static String ACTION_GATT_CONNECTED = "com.example.ti.ble.common.ACTION_GATT_CONNECTED";
	public final static String ACTION_GATT_DISCONNECTED = "com.example.ti.ble.common.ACTION_GATT_DISCONNECTED";
	public final static String ACTION_GATT_SERVICES_DISCOVERED = "com.example.ti.ble.common.ACTION_GATT_SERVICES_DISCOVERED";
	public final static String ACTION_DATA_READ = "com.example.ti.ble.common.ACTION_DATA_READ";
	public final static String ACTION_DATA_NOTIFY = "com.example.ti.ble.common.ACTION_DATA_NOTIFY";
	public final static String ACTION_DATA_WRITE = "com.example.ti.ble.common.ACTION_DATA_WRITE";
	public final static String EXTRA_DATA = "com.example.ti.ble.common.EXTRA_DATA";
	public final static String EXTRA_UUID = "com.example.ti.ble.common.EXTRA_UUID";
	public final static String EXTRA_STATUS = "com.example.ti.ble.common.EXTRA_STATUS";
	public final static String EXTRA_ADDRESS = "com.example.ti.ble.common.EXTRA_ADDRESS";

	// BLE
	private BluetoothManager mBluetoothManager = null;
	private BluetoothAdapter mBtAdapter = null;
	private BluetoothGatt mBluetoothGatt = null;
	private static BluetoothLeService mThis = null;
	private volatile boolean mBusy = false; // Write/read pending response
	private String mBluetoothDeviceAddress;

	/**
	 * GATT client callbacks
	 */
	private BluetoothGattCallback mGattCallbacks = new BluetoothGattCallback() {

		@Override
		public void onConnectionStateChange(BluetoothGatt gatt, int status,
		    int newState) {
			if (mBluetoothGatt == null) {
				// Log.e(TAG, "mBluetoothGatt not created!");
				return;
			}

			BluetoothDevice device = gatt.getDevice();
			String address = device.getAddress();
			// Log.d(TAG, "onConnectionStateChange (" + address + ") " + newState +
			// " status: " + status);

			try {
				switch (newState) {
				case BluetoothProfile.STATE_CONNECTED:
					broadcastUpdate(ACTION_GATT_CONNECTED, address, status);
					break;
				case BluetoothProfile.STATE_DISCONNECTED:
					broadcastUpdate(ACTION_GATT_DISCONNECTED, address, status);
					break;
				default:
					// Log.e(TAG, "New state not processed: " + newState);
					break;
				}
			} catch (NullPointerException e) {
				e.printStackTrace();
			}
		}

		@Override
		public void onServicesDiscovered(BluetoothGatt gatt, int status) {
			BluetoothDevice device = gatt.getDevice();
			broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED, device.getAddress(),
			    status);
		}

		@Override
		public void onCharacteristicChanged(BluetoothGatt gatt,
		    BluetoothGattCharacteristic characteristic) {
			broadcastUpdate(ACTION_DATA_NOTIFY, characteristic,
			    BluetoothGatt.GATT_SUCCESS);
		}

		@Override
		public void onCharacteristicRead(BluetoothGatt gatt,
		    BluetoothGattCharacteristic characteristic, int status) {
			broadcastUpdate(ACTION_DATA_READ, characteristic, status);
		}

		@Override
		public void onCharacteristicWrite(BluetoothGatt gatt,
		    BluetoothGattCharacteristic characteristic, int status) {
			broadcastUpdate(ACTION_DATA_WRITE, characteristic, status);
		}

		@Override
		public void onDescriptorRead(BluetoothGatt gatt,
		    BluetoothGattDescriptor descriptor, int status) {
			mBusy = false;
		}

		@Override
		public void onDescriptorWrite(BluetoothGatt gatt,
		    BluetoothGattDescriptor descriptor, int status) {
			// Log.i(TAG, "onDescriptorWrite: " + descriptor.getUuid().toString());
			mBusy = false;
		}
	};

	private void broadcastUpdate(final String action, final String address,
	    final int status) {
		final Intent intent = new Intent(action);
		intent.putExtra(EXTRA_ADDRESS, address);
		intent.putExtra(EXTRA_STATUS, status);
		sendBroadcast(intent);
		mBusy = false;
	}

	private void broadcastUpdate(final String action,
	    final BluetoothGattCharacteristic characteristic, final int status) {
		final Intent intent = new Intent(action);
		intent.putExtra(EXTRA_UUID, characteristic.getUuid().toString());
		intent.putExtra(EXTRA_DATA, characteristic.getValue());
		intent.putExtra(EXTRA_STATUS, status);
		sendBroadcast(intent);
		mBusy = false;
	}

	private boolean checkGatt() {
		if (mBtAdapter == null) {
			// Log.w(TAG, "BluetoothAdapter not initialized");
			return false;
		}
		if (mBluetoothGatt == null) {
			// Log.w(TAG, "BluetoothGatt not initialized");
			return false;
		}

		if (mBusy) {
			// Log.w(TAG, "LeService busy");
			return false;
		}
		return true;

	}

	/**
	 * Manage the BLE service
	 */
	public class LocalBinder extends Binder {
		public BluetoothLeService getService() {
			return BluetoothLeService.this;
		}
	}

	@Override
	public IBinder onBind(Intent intent) {
		return binder;
	}

	@Override
	public boolean onUnbind(Intent intent) {
		// After using a given device, you should make sure that
		// BluetoothGatt.close() is called
		// such that resources are cleaned up properly. In this particular example,
		// close() is
		// invoked when the UI is disconnected from the Service.
		close();
		return super.onUnbind(intent);
	}

	private final IBinder binder = new LocalBinder();

	/**
	 * Initializes a reference to the local Bluetooth adapter.
	 * 
	 * @return Return true if the initialization is successful.
	 */
	public boolean initialize() {
		// For API level 18 and above, get a reference to BluetoothAdapter through
		// BluetoothManager.
		mThis = this;
		if (mBluetoothManager == null) {
			mBluetoothManager = (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE);
			if (mBluetoothManager == null) {
				// Log.e(TAG, "Unable to initialize BluetoothManager.");
				return false;
			}
		}

		mBtAdapter = mBluetoothManager.getAdapter();
		if (mBtAdapter == null) {
			// Log.e(TAG, "Unable to obtain a BluetoothAdapter.");
			return false;
		}
		return true;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		// Log.i(TAG, "Received start id " + startId + ": " + intent);
		// We want this service to continue running until it is explicitly
		// stopped, so return sticky.
		return START_STICKY;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if (mBluetoothGatt != null) {
			mBluetoothGatt.close();
			mBluetoothGatt = null;
		}
	}

	//
	// GATT API
	//
	/**
	 * Request a read on a given {@code BluetoothGattCharacteristic}. The read
	 * result is reported asynchronously through the
	 * {@code BluetoothGattCallback#onCharacteristicRead(android.bluetooth.BluetoothGatt, android.bluetooth.BluetoothGattCharacteristic, int)}
	 * callback.
	 * 
	 * @param characteristic
	 *          The characteristic to read from.
	 */
	public void readCharacteristic(BluetoothGattCharacteristic characteristic) {
		if (!checkGatt())
			return;
		mBusy = true;
		mBluetoothGatt.readCharacteristic(characteristic);
	}

	public boolean writeCharacteristic(
	    BluetoothGattCharacteristic characteristic, byte b) {
		if (!checkGatt())
			return false;

		byte[] val = new byte[1];
		val[0] = b;
		characteristic.setValue(val);

		mBusy = true;
		return mBluetoothGatt.writeCharacteristic(characteristic);
	}

	public boolean writeCharacteristic(
	    BluetoothGattCharacteristic characteristic, boolean b) {
		if (!checkGatt())
			return false;

		byte[] val = new byte[1];

		val[0] = (byte) (b ? 1 : 0);
		characteristic.setValue(val);
		mBusy = true;
		return mBluetoothGatt.writeCharacteristic(characteristic);
	}

	public boolean writeCharacteristic(BluetoothGattCharacteristic characteristic) {
		if (!checkGatt())
			return false;

		mBusy = true;
		return mBluetoothGatt.writeCharacteristic(characteristic);
	}

	/**
	 * Retrieves the number of GATT services on the connected device. This should
	 * be invoked only after {@code BluetoothGatt#discoverServices()} completes
	 * successfully.
	 * 
	 * @return A {@code integer} number of supported services.
	 */
	public int getNumServices() {
		if (mBluetoothGatt == null)
			return 0;

		return mBluetoothGatt.getServices().size();
	}

	/**
	 * Retrieves a list of supported GATT services on the connected device. This
	 * should be invoked only after {@code BluetoothGatt#discoverServices()}
	 * completes successfully.
	 * 
	 * @return A {@code List} of supported services.
	 */
	public List<BluetoothGattService> getSupportedGattServices() {
		if (mBluetoothGatt == null)
			return null;

		return mBluetoothGatt.getServices();
	}

	/**
	 * Enables or disables notification on a give characteristic.
	 * 
	 * @param characteristic
	 *          Characteristic to act on.
	 * @param enable
	 *          If true, enable notification. False otherwise.
	 */
	public boolean setCharacteristicNotification(
	    BluetoothGattCharacteristic characteristic, boolean enable) {
		if (!checkGatt())
			return false;

		boolean ok = false;
		if (mBluetoothGatt.setCharacteristicNotification(characteristic, enable)) {

			BluetoothGattDescriptor clientConfig = characteristic
			    .getDescriptor(GattInfo.CLIENT_CHARACTERISTIC_CONFIG);
			if (clientConfig != null) {

				if (enable) {
					// Log.i(TAG, "Enable notification: " +
					// characteristic.getUuid().toString());
					ok = clientConfig
					    .setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
				} else {
					// Log.i(TAG, "Disable notification: " +
					// characteristic.getUuid().toString());
					ok = clientConfig
					    .setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
				}

				if (ok) {
					mBusy = true;
					ok = mBluetoothGatt.writeDescriptor(clientConfig);
					// Log.i(TAG, "writeDescriptor: " +
					// characteristic.getUuid().toString());
				}
			}
		}

		return ok;
	}

	public boolean isNotificationEnabled(
	    BluetoothGattCharacteristic characteristic) {
		if (!checkGatt())
			return false;

		BluetoothGattDescriptor clientConfig = characteristic
		    .getDescriptor(GattInfo.CLIENT_CHARACTERISTIC_CONFIG);
		if (clientConfig == null)
			return false;

		return clientConfig.getValue() == BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE;
	}

	/**
	 * Connects to the GATT server hosted on the Bluetooth LE device.
	 * 
	 * @param address
	 *          The device address of the destination device.
	 * 
	 * @return Return true if the connection is initiated successfully. The
	 *         connection result is reported asynchronously through the
	 *         {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
	 *         callback.
	 */
	public boolean connect(final String address) {
		if (mBtAdapter == null || address == null) {
			// Log.w(TAG, "BluetoothAdapter not initialized or unspecified address.");
			return false;
		}
		final BluetoothDevice device = mBtAdapter.getRemoteDevice(address);
		int connectionState = mBluetoothManager.getConnectionState(device,
		    BluetoothProfile.GATT);

		if (connectionState == BluetoothProfile.STATE_DISCONNECTED) {

			// Previously connected device. Try to reconnect.
			if (mBluetoothDeviceAddress != null
			    && address.equals(mBluetoothDeviceAddress) && mBluetoothGatt != null) {
				// Log.d(TAG, "Re-use GATT connection");
				if (mBluetoothGatt.connect()) {
					return true;
				} else {
					// Log.w(TAG, "GATT re-connect failed.");
					return false;
				}
			}

			if (device == null) {
				// Log.w(TAG, "Device not found.  Unable to connect.");
				return false;
			}
			// We want to directly connect to the device, so we are setting the
			// autoConnect parameter to false.
			// Log.d(TAG, "Create a new GATT connection.");
			mBluetoothGatt = device.connectGatt(this, false, mGattCallbacks);
			mBluetoothDeviceAddress = address;
		} else {
			// Log.w(TAG, "Attempt to connect in state: " + connectionState);
			return false;
		}
		return true;
	}

	/**
	 * Disconnects an existing connection or cancel a pending connection. The
	 * disconnection result is reported asynchronously through the
	 * {@code BluetoothGattCallback#onConnectionStateChange(android.bluetooth.BluetoothGatt, int, int)}
	 * callback.
	 */
	public void disconnect(String address) {
		if (mBtAdapter == null) {
			// Log.w(TAG, "disconnect: BluetoothAdapter not initialized");
			return;
		}
		final BluetoothDevice device = mBtAdapter.getRemoteDevice(address);
		int connectionState = mBluetoothManager.getConnectionState(device,
		    BluetoothProfile.GATT);

		if (mBluetoothGatt != null) {
			if (connectionState != BluetoothProfile.STATE_DISCONNECTED) {
				mBluetoothGatt.disconnect();
			} else {
				// Log.w(TAG, "Attempt to disconnect in state: " + connectionState);
			}
		}
	}

	/**
	 * After using a given BLE device, the app must call this method to ensure
	 * resources are released properly.
	 */
	public void close() {
		if (mBluetoothGatt != null) {
			// Log.i(TAG, "close");
			mBluetoothGatt.close();
			mBluetoothGatt = null;
		}
  }

	public int numConnectedDevices() {
		int n = 0;

		if (mBluetoothGatt != null) {
			List<BluetoothDevice> devList;
			devList = mBluetoothManager.getConnectedDevices(BluetoothProfile.GATT);
			n = devList.size();
		}
		return n;
	}

	//
	// Utility functions
	//
	public static BluetoothGatt getBtGatt() {
		return mThis.mBluetoothGatt;
	}

	public static BluetoothManager getBtManager() {
		return mThis.mBluetoothManager;
	}

	public static BluetoothLeService getInstance() {
		return mThis;
	}

	public boolean waitIdle(int timeout) {
		timeout /= 10;
		while (--timeout > 0) {
			if (mBusy)
				try {
					Thread.sleep(10);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
			else
				break;
		}

		return timeout > 0;
	}

}
