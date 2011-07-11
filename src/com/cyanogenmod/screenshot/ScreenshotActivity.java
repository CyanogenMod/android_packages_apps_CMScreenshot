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
import android.os.SystemProperties;
import android.util.Log;
import android.widget.Toast;

import android.view.Display;
import android.view.WindowManager;

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
            Log.d("CMScreenshot","Ran helper");
            p.waitFor();
            InputStream rawFile = new FileInputStream(mRawScreenshot);
            mBitmap = BitmapFactory.decodeStream(rawFile);
            rawFile.close();
            File tmpshot = new File(mRawScreenshot);
            tmpshot.delete();

            // valid values for ro.sf.hwrotation are 0, 90, 180 & 270
            int rot = 360-SystemProperties.getInt("ro.sf.hwrotation",0);

            Display display = ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay();

            // First round, natural device rotation
            if(rot > 0 && rot < 360){
                Log.d("CMScreenshot","rotation="+rot);
                Matrix matrix = new Matrix();
                matrix.postRotate(rot);
                mBitmap = Bitmap.createBitmap(mBitmap, 0, 0, mBitmap.getWidth(), mBitmap.getHeight(), matrix, true);
            }

            // Second round, device orientation:
            // getOrientation returns 0-3 for 0, 90, 180, 270, relative
            // to the natural position of the device
            rot = (display.getOrientation() * 90);
            rot %= 360;
            if(rot > 0){
                Log.d("CMScreenshot","rotation="+rot);
                Matrix matrix = new Matrix();
                matrix.postRotate((rot*-1));
                mBitmap = Bitmap.createBitmap(mBitmap, 0, 0, mBitmap.getWidth(), mBitmap.getHeight(), matrix, true);
            }

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
            Toast toast = Toast.makeText(ScreenshotActivity.this, getString(R.string.toast_error) + " " + ex.getMessage(), Toast.LENGTH_LONG);
            toast.show();
        }
        Toast toast = Toast.makeText(ScreenshotActivity.this, getString(R.string.toast_save_location) + " " + mScreenshotFile,Toast.LENGTH_LONG);
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
