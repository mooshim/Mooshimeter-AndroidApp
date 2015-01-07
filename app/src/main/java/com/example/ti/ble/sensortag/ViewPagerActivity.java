/**************************************************************************************************
  Filename:       ViewPagerActivity.java
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
package com.example.ti.ble.sensortag;

import java.util.ArrayList;
import java.util.List;

import android.app.ActionBar;
import android.app.Dialog;
import android.app.FragmentTransaction;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentStatePagerAdapter;
import android.support.v4.view.ViewPager;
// import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;

import com.example.ti.ble.sensortag.R;

public class ViewPagerActivity extends FragmentActivity {
  // Constants
  // private static final String TAG = "ViewPagerActivity";

  // GUI
  protected static ViewPagerActivity mThis = null;
  protected SectionsPagerAdapter mSectionsPagerAdapter;
  private ViewPager mViewPager;
  protected int mResourceFragmentPager;
  protected int mResourceIdPager;
  private int mCurrentTab = 0;
  protected Menu optionsMenu;
  private MenuItem refreshItem;
  protected boolean mBusy;

  protected ViewPagerActivity() {
    // Log.d(TAG, "construct");
    mThis = this;
    mBusy = false;
    refreshItem = null;
  }

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    // Log.d(TAG, "onCreate");
    super.onCreate(savedInstanceState);
    setContentView(mResourceFragmentPager);

    // Set up the action bar
    final ActionBar actionBar = getActionBar();
    actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);
    ImageView view = (ImageView) findViewById(android.R.id.home);
    view.setPadding(10, 0, 20, 10);

    // Set up the ViewPager with the sections adapter.
    mViewPager = (ViewPager) findViewById(mResourceIdPager);
    mViewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
      @Override
      public void onPageSelected(int n) {
        // Log.d(TAG, "onPageSelected: " + n);
        actionBar.setSelectedNavigationItem(n);
      }
    });
    // Create the adapter that will return a fragment for each section
    mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

    // Set up the ViewPager with the sections adapter.
    mViewPager.setAdapter(mSectionsPagerAdapter);
  }


  @Override
  public void onDestroy() {
    super.onDestroy();
    // Log.d(TAG, "onDestroy");
    mSectionsPagerAdapter = null;
  }

  @Override
  public void onBackPressed() {
    if (mCurrentTab != 0)
      getActionBar().setSelectedNavigationItem(0);
    else
      super.onBackPressed();
  }

  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    // Log.d(TAG, "onOptionsItemSelected");
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

  protected void showBusyIndicator(final boolean busy) {
  	if (optionsMenu != null) {
  		refreshItem = optionsMenu.findItem(R.id.opt_progress);
  		if (refreshItem != null) {
  			if (busy) {
  				refreshItem.setActionView(R.layout.frame_progress);
  			} else {
  				refreshItem.setActionView(null);
  			}
    		refreshItem.setVisible(busy);
  		} else {
    		// Log.e(TAG,"Refresh item not expanded");
  		}
  	} else {
  		// Log.e(TAG,"Options not expanded");
  	}
  	mBusy = busy;
  }
  
  protected void refreshBusyIndicator() {
  	if (refreshItem == null) {
  		runOnUiThread(new Runnable() {

  			@Override
  			public void run() {
  				showBusyIndicator(mBusy);
  			}
  		});
  	}
  }

  public class SectionsPagerAdapter extends FragmentStatePagerAdapter {
    private List<Fragment> mFragmentList;
    private List<String> mTitles;

    public SectionsPagerAdapter(FragmentManager fm) {
      super(fm);
      mFragmentList = new ArrayList<Fragment>();
      mTitles = new ArrayList<String>();
    }

    public void addSection(Fragment fragment, String title) {
      final ActionBar actionBar = getActionBar();
      mFragmentList.add(fragment);
      mTitles.add(title);
      actionBar.addTab(actionBar.newTab().setText(title).setTabListener(tabListener));
      notifyDataSetChanged();
      // Log.d(TAG, "Tab: " + title);
    }

    @Override
    public Fragment getItem(int position) {
      return mFragmentList.get(position);
    }

    @Override
    public int getCount() {
      return mTitles.size();
    }

    @Override
    public CharSequence getPageTitle(int position) {
      if (position < getCount()) {
        return mTitles.get(position);
      } else {
        return null;
      }
    }
  }

  // Create a tab listener that is called when the user changes tabs.
  ActionBar.TabListener tabListener = new ActionBar.TabListener() {

    public void onTabSelected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
      int n = tab.getPosition();
      // Log.d(TAG, "onTabSelected: " + n);
      mCurrentTab = n;
      mViewPager.setCurrentItem(n);
    }

    public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
      // int n = tab.getPosition();
      // Log.d(TAG, "onTabUnselected: " + n);
    }

    public void onTabReselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
      // int n = tab.getPosition();
      // Log.d(TAG, "onTabReselected: " + n);
    }
  };
}
