package com.koushikdutta.screenshot;

import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Enumeration;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Bitmap.CompressFormat;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Parcel;
import android.util.Log;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ImageView;
import android.widget.Toast;

public class ScreenshotActivity extends Activity
{
	static Bitmap mBitmap = null;
	ImageView mImage;
	Handler mHander = new Handler();
	String mScreenshotFile;

	static Runtime mRuntime = Runtime.getRuntime();
	final static String mAppRoot = "/data/data/com.koushikdutta.screenshot";
	final static int BUFSIZE = 10000;
	final static String APK_PATH = "/data/app/com.koushikdutta.screenshot.apk";
	final static String ZIP_FILTER = "assets";
	final static String LOGTAG = "ScreenshotActivity";

	void unzipAssets()
	{
		try
		{
			File zipFile = new File(APK_PATH);
			long zipLastModified = zipFile.lastModified();
			ZipFile zip = new ZipFile(APK_PATH);
			Vector<ZipEntry> files = pluginsFilesFromZip(zip);
			int zipFilterLength = ZIP_FILTER.length();

			Enumeration entries = files.elements();
			while (entries.hasMoreElements())
			{
				ZipEntry entry = (ZipEntry) entries.nextElement();
				String path = entry.getName().substring(zipFilterLength);
				File outputFile = new File(mAppRoot, path);
				outputFile.getParentFile().mkdirs();

				if (outputFile.exists() && entry.getSize() == outputFile.length() && zipLastModified < outputFile.lastModified())
				{
					Log(outputFile.getName() + " already extracted.");
				}
				else
				{
					FileOutputStream fos = new FileOutputStream(outputFile);
					Log("Copied " + entry + " to " + mAppRoot + "/" + path);
					copyStreams(zip.getInputStream(entry), fos);
					String curPath = outputFile.getAbsolutePath();
					do
					{
						mRuntime.exec("/system/bin/chmod 755 " + curPath);
						curPath = new File(curPath).getParent();
					}
					while (!curPath.equals(mAppRoot));
				}
			}
		}
		catch (IOException e)
		{
			Log("Error: " + e.getMessage());
		}
	}

	public Vector<ZipEntry> pluginsFilesFromZip(ZipFile zip)
	{
		Vector<ZipEntry> list = new Vector<ZipEntry>();
		Enumeration entries = zip.entries();
		while (entries.hasMoreElements())
		{
			ZipEntry entry = (ZipEntry) entries.nextElement();
			if (entry.getName().startsWith(ZIP_FILTER))
			{
				list.add(entry);
			}
		}
		return list;
	}

	void Log(String string)
	{
		Log.v(LOGTAG, string);
	}

	void copyStreams(InputStream is, FileOutputStream fos)
	{
		BufferedOutputStream os = null;
		try
		{
			byte data[] = new byte[BUFSIZE];
			int count;
			os = new BufferedOutputStream(fos, BUFSIZE);
			while ((count = is.read(data, 0, BUFSIZE)) != -1)
			{
				os.write(data, 0, count);
			}
			os.flush();
		}
		catch (IOException e)
		{
			Log("Exception while copying: " + e);
		}
		finally
		{
			try
			{
				if (os != null)
				{
					os.close();
				}
			}
			catch (IOException e2)
			{
				Log("Exception while closing the stream: " + e2);
			}
		}
	}

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		unzipAssets();

		mImage = (ImageView) findViewById(R.id.ImageView01);

		if (mBitmap != null)
			mImage.setImageBitmap(mBitmap);

		Toast toast = Toast.makeText(ScreenshotActivity.this, "Tip: Hotkey this application from the home screen to take screenshots from anywhere easily!", Toast.LENGTH_LONG);
		toast.show();
		toast = Toast.makeText(ScreenshotActivity.this, "Tip: Use the camera button to take screenshots!", Toast.LENGTH_LONG);
		toast.show();

		IntentFilter filter = new IntentFilter("android.intent.action.CAMERA_BUTTON");
		registerReceiver(new BroadcastReceiver()
		{
			@Override
			public void onReceive(Context arg0, Intent arg1)
			{
				takeScreenshot();
			}
		}, filter);
	}
	
	static String getUserForPackage(String packageName) throws Exception
	{
		// parse the uid given a package name (which should be visible in ps)
		Process p = Runtime.getRuntime().exec("ps");
		InputStream reader = p.getInputStream();
		Thread.sleep(200);
		byte[] buff = new byte[10000];
		int read = reader.read(buff, 0, buff.length);
		String str = new String(buff);
		String pattern = String.format("(app_\\d+).*?%s", packageName);
		Pattern regex = Pattern.compile(pattern);
		Matcher match = regex.matcher(str);
		if (match.find())
			return match.group(1);

		throw new Exception("Unable to determine uid for package");
	}

	static int getUidForPackage(String packageName) throws Exception
	{
		// parse the uid given a package name (which should be visible in ps)
		Process p = Runtime.getRuntime().exec("ps");
		InputStream reader = p.getInputStream();
		Thread.sleep(200);
		byte[] buff = new byte[10000];
		int read = reader.read(buff, 0, buff.length);
		String str = new String(buff);
		String pattern = String.format("app_(\\d+).*?%s", packageName);
		Pattern regex = Pattern.compile(pattern);
		Matcher match = regex.matcher(str);
		if (match.find())
			return Integer.parseInt(match.group(1)) + 10000;

		throw new Exception("Unable to determine uid for package");
	}

	static void writeCommand(OutputStream os, String command) throws Exception
	{
		os.write((command + "\n").getBytes("ASCII"));
	}

	@Override
	public boolean onKeyDown(int keyCode, KeyEvent event)
	{
		if (keyCode == 4)
			return true;
		return super.onKeyDown(keyCode, event);
	}

	void takeScreenshot()
	{
		try
		{
			Process sh = Runtime.getRuntime().exec("su -c sh");

			OutputStream os = sh.getOutputStream();

			final String file = "/data/data/com.koushikdutta.screenshot/screenshot.raw";

			int screenshotUid = getUidForPackage("com.koushikdutta.screenshot");
			String screenshotUser = getUserForPackage("com.koushikdutta.screenshot");
			writeCommand(os, "rm " + file);
			writeCommand(os, "mkdir /sdcard/dcim");
			writeCommand(os, "mkdir /sdcard/dcim/Screenshot");
			writeCommand(os, "/data/data/com.koushikdutta.screenshot/screenshot");
			writeCommand(os, "chown root." + screenshotUser + " " + file);
			writeCommand(os, "chmod 660 " + file);
			writeCommand(os, "exit");
			os.flush();

			boolean success = false;
			for (int i = 0; i < 10; i++)
			{
				try
				{
					Thread.sleep(1000);
					// if we can successfully get the exit value, then that means the process exited.
					sh.exitValue();
					success = true;
					break;
				}
				catch (Exception ex)
				{
				}
			}
			if (!success)
				throw new Exception("Unable to take screenshot");

			File screenshot = new File(file);
			if (!screenshot.exists())
				throw new Exception("screenshot.raw file not found!");

			mHander.post(new Runnable()
			{
				public void run()
				{
					Toast toast = Toast.makeText(ScreenshotActivity.this, "Screen captured!", Toast.LENGTH_LONG);
					toast.show();
				}
			});

			FileInputStream fs = new FileInputStream(file);
			DataInputStream ds = new DataInputStream(fs);
			byte[] bytes = new byte[fs.available()];
			ds.readFully(bytes);
			Parcel parcel = Parcel.obtain();
			parcel.writeByteArray(bytes);
			parcel.setDataPosition(0);
			int size = parcel.readInt();
			mBitmap = Bitmap.CREATOR.createFromParcel(parcel);
			mScreenshotFile = String.format("/sdcard/dcim/Screenshot/screenshot%d.png", System.currentTimeMillis());
			try
			{
				FileOutputStream fout = new FileOutputStream(mScreenshotFile);
				mBitmap.compress(CompressFormat.PNG, 100, fout);
				fout.close();
			}
			catch (Exception ex)
			{
			}
			mHander.post(new Runnable()
			{
				public void run()
				{
					mImage.setImageBitmap(mBitmap);
				}
			});
		}
		catch (Exception ex)
		{
			Toast toast = Toast.makeText(ScreenshotActivity.this, "Error: " + ex.getMessage(), Toast.LENGTH_LONG);
			toast.show();
		}
	}

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

	static final int DIALOG_TIMED = 1;
	int mDelayIndex = 0;

	@Override
	protected Dialog onCreateDialog(int id)
	{
		switch (id)
		{
		case DIALOG_TIMED:
			CharSequence[] entries = new CharSequence[] { "5", "10", "15", "20", "25", "30" };
			AlertDialog.Builder builder = new AlertDialog.Builder(this);
			builder.setTitle("Screenshot Time Delay");
			builder.setCancelable(true);
			builder.setSingleChoiceItems(entries, mDelayIndex, new DialogInterface.OnClickListener()
			{
				public void onClick(DialogInterface dialog, int which)
				{
					mDelayIndex = which;
				}
			});

			builder.setPositiveButton("OK", new Dialog.OnClickListener()
			{
				public void onClick(DialogInterface dialog, int which)
				{
					takeScreenshot((mDelayIndex + 1) * 5);
					dialog.dismiss();
				}
			});
			return builder.create();
		}
		return super.onCreateDialog(id);
	}

	@Override
	protected void onNewIntent(Intent intent)
	{
		super.onNewIntent(intent);
		try
		{
			if (intent != null && intent.getExtras().containsKey("com.android.settings.quicklaunch.SHORTCUT"))
				takeScreenshot();
		}
		catch (Exception ex)
		{
		}
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		if (item == mTimedScreenshot)
		{
			showDialog(DIALOG_TIMED);
			return true;
		}
		else if (item == mClose)
		{
			finish();
			return true;
		}
		else if (item == mSendEmail)
		{
			if (mScreenshotFile == null)
				return true;
			Intent sendIntent = new Intent(Intent.ACTION_SEND); 
			sendIntent.putExtra(Intent.EXTRA_SUBJECT, "Screenshot"); 
			sendIntent.putExtra(Intent.EXTRA_STREAM, Uri.parse ("file://"+mScreenshotFile)); 
			sendIntent.setType("text/csv");
			startActivity(sendIntent);
			return true;
		}
		return super.onOptionsItemSelected(item);
	}
	
	@Override
	public boolean onPrepareOptionsMenu(Menu menu)
	{
		mSendEmail.setVisible(mScreenshotFile != null);
		return super.onPrepareOptionsMenu(menu);
	}

	MenuItem mTimedScreenshot;
	MenuItem mClose;
	MenuItem mSendEmail;

	@Override
	public boolean onCreateOptionsMenu(Menu menu)
	{
		mTimedScreenshot = menu.add("Timed");
		mTimedScreenshot.setIcon(android.R.drawable.ic_menu_agenda);
		mSendEmail = menu.add("Email");
		mSendEmail.setIcon(android.R.drawable.ic_menu_send);
		mClose = menu.add("Exit");
		mClose.setIcon(android.R.drawable.ic_menu_close_clear_cancel);
		return super.onCreateOptionsMenu(menu);
	}
}