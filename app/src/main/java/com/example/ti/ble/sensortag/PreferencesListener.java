/**************************************************************************************************
  Filename:       PreferencesListener.java
  Revised:        $Date: 2013-09-03 16:44:05 +0200 (ti, 03 sep 2013) $
  Revision:       $Revision: 27592 $

  Copyright (c) 2013 - 2014 Texas Instruments Incorporated

  All rights reserved not granted herein.
  Limited License. 

  Texas Instruments Incorporated grants a world-wide, royalty-free,
  non-exclusive license under copyrights and patents it now or hereafter
  owns or controls to make, have made, use, import, offer to sell and sell ("Utilize")
  this software subject to the terms herein.  With respect to the foregoing patent
  license, such license is granted  solely to the extent that any such patent is necessary
  to Utilize the software alone.  The patent license shall not apply to any combinations which
  include this software, other than combinations with devices manufactured by or for TI (“TI Devices”). 
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

  THIS SOFTWARE IS PROVIDED BY TI AND TI’S LICENSORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING,
  BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
  IN NO EVENT SHALL TI AND TI’S LICENSORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
  CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
  OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
  OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
  POSSIBILITY OF SUCH DAMAGE.


 **************************************************************************************************/
package com.example.ti.ble.sensortag;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.preference.CheckBoxPreference;
import android.preference.PreferenceFragment;

/**
 * Processing changes in preferences.
 * 
 * */
public class PreferencesListener implements SharedPreferences.OnSharedPreferenceChangeListener {

  private static final int MAX_NOTIFICATIONS = 4; // Limit on simultaneous notification in Android 4.3
  private SharedPreferences sharedPreferences;
  private PreferenceFragment preferenceFragment;
  private Context context;

  public PreferencesListener(Context context, SharedPreferences sharedPreferences, PreferenceFragment pf) {
    this.context = context;
    this.sharedPreferences = sharedPreferences;
    this.preferenceFragment = pf;
  }

  public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
  	
  	// Check operating system version
  	if (android.os.Build.VERSION.SDK_INT > android.os.Build.VERSION_CODES. JELLY_BEAN_MR2) {
      return;
  	}
  	
    Sensor sensor = getSensorFromPrefKey(key);

    boolean noCheckboxWithThatKey = sensor == null;
    if (noCheckboxWithThatKey)
      return;

    boolean turnedOn = sharedPreferences.getBoolean(key, true);

    if (turnedOn && enabledSensors().size() > MAX_NOTIFICATIONS) {
    	// Undo 
    	CheckBoxPreference cb = (CheckBoxPreference) preferenceFragment.findPreference(key);
    	cb.setChecked(false);
    	// Alert user
			alertNotifyLimitaion();
    }
  }

  private void alertNotifyLimitaion() {
  	String msg = "Android 4.3 BLE " + "allows a maximum of " + MAX_NOTIFICATIONS
  			+ " simultaneous notifications.\n";
  	
  	AlertDialog.Builder ab = new AlertDialog.Builder(context);

  	ab.setTitle("Notifications limit");
  	ab.setMessage(msg);
  	ab.setIcon(R.drawable.bluetooth);
  	ab.setNeutralButton(android.R.string.ok, new DialogInterface.OnClickListener() {
  		public void onClick(DialogInterface dialog, int which) {
  		}
  	});

  	// Showing Alert Message
  	AlertDialog alertDialog = ab.create();
  	alertDialog.show();
  }

  /**
   * String is in the format
   * 
   * pref_magnetometer_on
   * 
   * @return Sensor corresponding to checkbox key, or null if there is no corresponding sensor.
   * */
  private Sensor getSensorFromPrefKey(String key) {
    try {
      int start = "pref_".length();
      int end = key.length() - "_on".length();
      String enumName = key.substring(start, end).toUpperCase(Locale.ENGLISH);

      return Sensor.valueOf(enumName);
    } catch (IndexOutOfBoundsException e) {
      // thrown by substring
    } catch (IllegalArgumentException e) {
      // thrown by valueOf
    } catch (NullPointerException e) {
      // thrown by valueOf
    }
    return null; // If exception was thrown while parsing. DON'T replace with catch'em all exception handling.
  }

  private List<Sensor> enabledSensors() {
    List<Sensor> sensors = new ArrayList<Sensor>();
    for (Sensor sensor : Sensor.values())
      if (isEnabledByPrefs(sensor))
        sensors.add(sensor);

    return sensors;
  }

  private boolean isEnabledByPrefs(final Sensor sensor) {
  	String preferenceKeyString = "pref_" + sensor.name().toLowerCase(Locale.ENGLISH) + "_on";

  	if (sharedPreferences.contains(preferenceKeyString)) {
  		return sharedPreferences.getBoolean(preferenceKeyString, true);
  	}
  	return false;
  }
}
