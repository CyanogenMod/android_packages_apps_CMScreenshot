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
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.Date;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
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
import android.provider.Settings;
import android.text.format.DateFormat;
import android.util.Log;
import android.view.Display;
import android.view.WindowManager;
import android.widget.Toast;

public class ScreenshotActivity extends Activity
{
    private static final String TAG = "CMScreenshot";

    private static final String SCREENSHOT_BUCKET_NAME =
        Environment.getExternalStorageDirectory().toString()
        + "/DCIM/Screenshots";

    private static final String DATEFORMAT_12 = "yyyyMMdd-hhmmssaa";
    private static final String DATEFORMAT_24 = "yyyyMMdd-kkmmss";

    Handler mHander = new Handler();
    String mScreenshotFile;
    MediaScannerConnection mConnection;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (!(Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED))) {
            Toast toast = Toast.makeText(ScreenshotActivity.this, getString(R.string.not_mounted), Toast.LENGTH_LONG);
            toast.show();
            finish();
        }

        mConnection = new MediaScannerConnection(ScreenshotActivity.this, mMediaScannerConnectionClient);
        takeScreenshot(1);
    }

    void takeScreenshot() {
        String rawScreenshot = String.format("%s/tmpshot.bmp", Environment.getExternalStorageDirectory().toString());

        try {
            Process p = Runtime.getRuntime().exec("/system/bin/screenshot");
            Log.d(TAG, "Ran helper");
            p.waitFor();

            Bitmap bitmap = BitmapFactory.decodeFile(rawScreenshot);
            File tmpshot = new File(rawScreenshot);
            tmpshot.delete();

            if (bitmap == null) {
                throw new Exception("Unable to save screenshot");
            }

            // valid values for ro.sf.hwrotation are 0, 90, 180 & 270
            int hwRotation = (360 - SystemProperties.getInt("ro.sf.hwrotation",0)) % 360;

            Display display = ((WindowManager) getSystemService(WINDOW_SERVICE)).getDefaultDisplay();
            int deviceRotation = (display.getOrientation() * 90) % 360;

            int finalRotation = hwRotation - deviceRotation;
            Log.d(TAG, "Hardware rotation " + hwRotation + ", device rotation " +
                    deviceRotation + " -> rotate by " + finalRotation);

            if (finalRotation != 0) {
                Matrix matrix = new Matrix();
                matrix.postRotate(finalRotation);
                bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            }

            File dir = new File(SCREENSHOT_BUCKET_NAME);
            if (!dir.exists()) dir.mkdirs();

            CharSequence date = DateFormat.format(
                    DateFormat.is24HourFormat(this) ? DATEFORMAT_24 : DATEFORMAT_12, new Date());

            mScreenshotFile = String.format("%s/screenshot-%s.png", SCREENSHOT_BUCKET_NAME, date);
            FileOutputStream fout = new FileOutputStream(mScreenshotFile);
            bitmap.compress(CompressFormat.PNG, 100, fout);
            fout.close();

            boolean shareScreenshot = (Settings.System.getInt(getContentResolver(),
                    Settings.System.SHARE_SCREENSHOT, 0)) == 1;

            if (shareScreenshot) {
                try {
                    Intent intent = new Intent(Intent.ACTION_SEND);
                    intent.setType("image/png");

                    intent.putExtra(Intent.EXTRA_STREAM, Uri.fromFile(new File(mScreenshotFile)));

                    startActivity(Intent.createChooser(intent, getString(R.string.share_message)));
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(ScreenshotActivity.this, R.string.no_way_to_share, Toast.LENGTH_SHORT).show();
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Couldn't save screenshot", e);
            Toast toast = Toast.makeText(ScreenshotActivity.this, getString(R.string.toast_error), Toast.LENGTH_LONG);
            toast.show();
            finish();
            return;
        }

        Toast toast = Toast.makeText(ScreenshotActivity.this,
                getString(R.string.toast_save_location) + " " + mScreenshotFile,Toast.LENGTH_LONG);
        toast.show();

        mConnection.connect();
    }

    MediaScannerConnection.MediaScannerConnectionClient mMediaScannerConnectionClient = new MediaScannerConnection.MediaScannerConnectionClient() {
        public void onScanCompleted(String path, Uri uri) {
            Log.d(TAG, "Scan of " + path + " completed -> uri " + uri);
            mConnection.disconnect();
            finish();
        }
        public void onMediaScannerConnected() {
            Log.d(TAG, "Connected to media scanner, scanning " + mScreenshotFile);
            mConnection.scanFile(mScreenshotFile, null);
        }
    };

    void takeScreenshot(final int delay) {
        mHander.postDelayed(new Runnable() {
            @Override
            public void run() {
                takeScreenshot();
            }
        }, delay * 1000);
    }
}
