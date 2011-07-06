/*
**
** Copyright 2010, Koushik Dutta
** Copyright 2011, The CyanogenMod Project 
**
** Licensed under the Apache License, Version 2.0 (the "License"); 
** you may not use this file except in compliance with the License. 
** You may obtain a copy of the License at 
**
**       http://www.apache.org/licenses/LICENSE-2.0 
**
** Unless required by applicable law or agreed to in writing, software 
** distributed under the License is distributed on an "AS IS" BASIS, 
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. 
** See the License for the specific language governing permissions and 
** limitations under the License.
*/

package com.cyanogenmod.screenshot;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.IOException;
import java.io.InputStream;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Bitmap.CompressFormat;
import android.graphics.Matrix;
import android.media.MediaScannerConnection;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Environment;
import android.util.Log;
import android.widget.Toast;

public class ScreenshotActivity extends Activity
{
    static Bitmap mBitmap = null;
    Handler mHander = new Handler();
    String mScreenshotFile;

    private static final String SCREENSHOT_BUCKET_NAME =
        Environment.getExternalStorageDirectory().toString()
        + "/DCIM/Screenshots";

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        mConnection = new MediaScannerConnection(ScreenshotActivity.this, mMediaScannerConnectionClient);
        mConnection.connect();

        takeScreenshot(1);
    }

    void takeScreenshot()
    {
        String mRawScreenshot = String.format("%s/tmpshot.bmp", Environment.getExternalStorageDirectory().toString());
        try
        {
            Process p = Runtime.getRuntime().exec("/system/bin/screenshot");
            Log.d("Screenshot","Ran helper");
            p.waitFor();
            mBitmap = BitmapFactory.decodeStream(new FileInputStream(mRawScreenshot));
            File tmpshot = new File(mRawScreenshot);
            tmpshot.delete();

            int rot = 0;
            try {
                Process pRot = Runtime.getRuntime().exec("/system/bin/getprop ro.sf.hwrotation");
                BufferedReader br = new BufferedReader(new InputStreamReader(pRot.getInputStream()));
                rot = Integer.parseInt(br.readLine());
                if(rot > 0){
		    Log.d("Screenshot","rotation="+rot);
                    Matrix matrix = new Matrix();
                    matrix.postRotate(rot);
                    mBitmap = Bitmap.createBitmap(mBitmap, 0, 0, mBitmap.getWidth(), mBitmap.getHeight(), matrix, true);
                }
            } catch (NumberFormatException nfe) {}

            try
            {
                File dir = new File(SCREENSHOT_BUCKET_NAME);
                if (!dir.exists()) dir.mkdirs();
                mScreenshotFile = String.format("%s/screenshot-%d.png", SCREENSHOT_BUCKET_NAME, System.currentTimeMillis());
                FileOutputStream fout = new FileOutputStream(mScreenshotFile);
                mBitmap.compress(CompressFormat.PNG, 100, fout);
                fout.close();
            }
            catch (Exception ex)
            {
                finish();
                throw new Exception("Unable to save screenshot: "+ex);
            }

        }
        catch (Exception ex)
        {
            Toast toast = Toast.makeText(ScreenshotActivity.this, "Error: " + ex.getMessage(), Toast.LENGTH_LONG);
            toast.show();
        }
        Toast toast = Toast.makeText(ScreenshotActivity.this, "Screenshot is saved at " + mScreenshotFile,Toast.LENGTH_LONG);
        toast.show();

        mConnection.scanFile(mScreenshotFile, null);
        mConnection.disconnect();
        finish();
    }

    MediaScannerConnection mConnection;
    MediaScannerConnection.MediaScannerConnectionClient mMediaScannerConnectionClient = new MediaScannerConnection.MediaScannerConnectionClient() {
        public void onScanCompleted(String path, Uri uri) {
            mConnection.disconnect();
            finish();
        }
        public void onMediaScannerConnected() {
        }
    };

    void takeScreenshot(final int delay)
    {
        mHander.postDelayed(new Runnable()
        {
            public void run()
            {
                takeScreenshot();
            }
        }, delay * 1000);
    }

}
