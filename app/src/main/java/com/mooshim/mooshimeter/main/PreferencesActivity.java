/**************************************************************************************************
  Filename:       PreferencesActivity.java
  Revised:        $Date: 2013-08-30 11:43:54 +0200 (fr, 30 aug 2013) $
  Revision:       $Revision: 27452 $

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

  BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED.
  IN NO EVENT SHALL TI AND TI�S LICENSORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
  CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA,
  OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
  OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
  POSSIBILITY OF SUCH DAMAGE.


 **************************************************************************************************/
package com.mooshim.mooshimeter.main;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Toast;

import com.mooshim.mooshimeter.R;
import com.mooshim.mooshimeter.common.*;

public class PreferencesActivity extends FragmentActivity {

    public MooshimeterDevice mMeter;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
      super.onCreate(savedInstanceState);
      final PreferencesActivity mThis = this;
      mMeter = MooshimeterDevice.getInstance();
      setContentView(R.layout.meter_preference_view);
      final EditText name_editor = (EditText) findViewById(R.id.meter_rename_edit);
      name_editor.setText("Mooshimeter V.1");
      if(mMeter.meter_name.name != null) {
          name_editor.setText(mMeter.meter_name.name);
      } else {
          name_editor.setText("Mooshimeter V.1");
      }
      name_editor.setOnKeyListener(new View.OnKeyListener() {
          public boolean onKey(View v, int keyCode, KeyEvent event) {
              if ((event.getAction() == KeyEvent.ACTION_DOWN) && (keyCode == KeyEvent.KEYCODE_ENTER)) {
                  mMeter.meter_name.name = String.valueOf(name_editor.getText());
                  mMeter.meter_name.send(new Runnable() {
                      @Override
                      public void run() {
                          Log.d(null, "Name sent");
                          Toast.makeText(mThis, "Name Sent", Toast.LENGTH_SHORT).show();
                      }
                  });
                  return true;
              }
              return false;
          }
      });
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    // Handle presses on the action bar items
    switch (item.getItemId()) {
    // Respond to the action bar's Up/Home button
    case android.R.id.home:
      onBackPressed();
      return true;
    default:
      return super.onOptionsItemSelected(item);
    }
  }

    public void hibernateOnClick(View v) {
        Log.d(null,"Hibernate clicked");
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(this);

        // set title
        alertDialogBuilder.setTitle("Confirm Hibernation");

        // set dialog message
        alertDialogBuilder
                .setMessage("Once in hibernation, you will not be able to connect to the meter until you short out the Ω input to wake the meter up.")
                .setCancelable(false)
                .setPositiveButton("Hibernate",new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,int id) {
                        mMeter.meter_settings.target_meter_state = MooshimeterDevice.METER_HIBERNATE;
                        mMeter.meter_settings.send(null);
                    }
                })
                .setNegativeButton("Cancel",new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog,int id) {
                        // if this button is clicked, just close
                        // the dialog box and do nothing
                        dialog.cancel();
                    }
                });

        // create alert dialog
        AlertDialog alertDialog = alertDialogBuilder.create();

        // show it
        alertDialog.show();
    }

}
