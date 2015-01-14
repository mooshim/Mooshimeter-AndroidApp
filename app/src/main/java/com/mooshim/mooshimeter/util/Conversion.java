/**************************************************************************************************
  Filename:       Conversion.java
  Revised:        $Date: 2013-08-30 12:02:37 +0200 (fr, 30 aug 2013) $
  Revision:       $Revision: 27470 $

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
package com.mooshim.mooshimeter.util;

import java.util.Formatter;

/* This class encapsulates utility functions */
public class Conversion {

  public static byte loUint16(short v) {
    return (byte) (v & 0xFF);
  }

  public static byte hiUint16(short v) {
    return (byte) (v >> 8);
  }

  public static short buildUint16(byte hi, byte lo) {
    return (short) ((hi << 8) + (lo & 0xff));
  }

  public static String BytetohexString(byte[] b, int len) {
    StringBuilder sb = new StringBuilder(b.length * (2 + 1));
    Formatter formatter = new Formatter(sb);

    for (int i = 0; i < len; i++) {
      if (i < len - 1)
        formatter.format("%02X:", b[i]);
      else
        formatter.format("%02X", b[i]);

    }
    formatter.close();

    return sb.toString();
  }

  static String BytetohexString(byte[] b, boolean reverse) {
    StringBuilder sb = new StringBuilder(b.length * (2 + 1));
    Formatter formatter = new Formatter(sb);

    if (!reverse) {
      for (int i = 0; i < b.length; i++) {
        if (i < b.length - 1)
          formatter.format("%02X:", b[i]);
        else
          formatter.format("%02X", b[i]);

      }
    } else {
      for (int i = (b.length - 1); i >= 0; i--) {
        if (i > 0)
          formatter.format("%02X:", b[i]);
        else
          formatter.format("%02X", b[i]);

      }
    }
    formatter.close();

    return sb.toString();
  }

  // Convert hex String to Byte
  public static int hexStringtoByte(String sb, byte[] results) {

    int i = 0;
    boolean j = false;

    if (sb != null) {
      for (int k = 0; k < sb.length(); k++) {
        if (((sb.charAt(k)) >= '0' && (sb.charAt(k) <= '9')) || ((sb.charAt(k)) >= 'a' && (sb.charAt(k) <= 'f'))
            || ((sb.charAt(k)) >= 'A' && (sb.charAt(k) <= 'F'))) {
          if (j) {
            results[i] += (byte) (Character.digit(sb.charAt(k), 16));
            i++;
          } else {
            results[i] = (byte) (Character.digit(sb.charAt(k), 16) << 4);
          }
          j = !j;
        }
      }
    }
    return i;
  }

  public static boolean isAsciiPrintable(String str) {
    if (str == null) {
      return false;
    }
    int sz = str.length();
    for (int i = 0; i < sz; i++) {
      if (isAsciiPrintable(str.charAt(i)) == false) {
        return false;
      }
    }
    return true;
  }

  private static boolean isAsciiPrintable(char ch) {
    return ch >= 32 && ch < 127;
  }

}