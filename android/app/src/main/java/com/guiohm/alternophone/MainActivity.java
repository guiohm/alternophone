package com.guiohm.alternophone;

import android.Manifest;
import android.app.Activity;
import android.content.ContentUris;
import android.content.Intent;
import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.media.AudioManager;
import android.net.Uri;
import android.os.Environment;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.DocumentsContract;
import android.provider.MediaStore;
import android.util.Log;

import java.io.IOException;

import io.flutter.app.FlutterActivity;
import io.flutter.plugins.GeneratedPluginRegistrant;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.common.PluginRegistry;
import io.flutter.plugin.common.PluginRegistry.Registrar;

public class MainActivity extends FlutterActivity {
  private static final String CHANNEL = "com.guiohm.audioplayer";

  public static final int REQUEST_PICKER_CODE = 1;
  public static final int REQUEST_PERMISSIONS_CODE = 2;

  // Flutter main activity.
  private Activity activity;

  // To handle position updates.
  private final Handler handler = new Handler();

  // Flutter result.
  private Result pendingResult;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    GeneratedPluginRegistrant.registerWith(this);

    // Get the device's sample rate and buffer size to enable
    // low-latency Android audio output, if available.
    String samplerateString = null, buffersizeString = null;
    if (Build.VERSION.SDK_INT >= 17) {
      AudioManager audioManager = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
      if (audioManager != null) {
        samplerateString = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE);
        buffersizeString = audioManager.getProperty(AudioManager.PROPERTY_OUTPUT_FRAMES_PER_BUFFER);
      }
    }
    if (samplerateString == null)
      samplerateString = "48000";
    if (buffersizeString == null)
      buffersizeString = "480";
    int samplerate = Integer.parseInt(samplerateString);
    int buffersize = Integer.parseInt(buffersizeString);

    // Files under res/raw are not zipped, just copied into the APK.
    // Get the offset and length to know where our file is located.
    AssetFileDescriptor fd = getResources().openRawResourceFd(R.raw.track);
    int fileOffset = (int) fd.getStartOffset();
    int fileLength = (int) fd.getLength();
    try {
      fd.getParcelFileDescriptor().close();
    } catch (IOException e) {
      Log.e("PlayerExample", "Close error.");
    }
    String path = getPackageResourcePath(); // get path to APK package
    System.loadLibrary("PlayerExample"); // load native library
    StartAudio(samplerate, buffersize); // start audio engine
    OpenFile(path, fileOffset, fileLength); // open audio file from APK

    new MethodChannel(getFlutterView(), CHANNEL).setMethodCallHandler(new MethodCallHandler() {
      @Override
      public void onMethodCall(MethodCall call, Result result) {
        switch (call.method) {
        case "togglePlayPause":
          result.success(togglePlayPause());
        case "filePicker":
          if (pendingResult != null) {
            pendingResult.error("MULTIPLE_REQUESTS", "Cannot make multiple requests.", null);
            pendingResult = null;
          }
          pendingResult = result;
          picker();
          break;
        default:
          result.notImplemented();
        }
      }
    });
    togglePlayPause();
  }

  private void picker() {
    // Request permissions.
    activity.requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_PERMISSIONS_CODE);
  }

  // Handle Play/Pause button toggle.
  public boolean togglePlayPause() {
    TogglePlayback();
    playing = !playing;
    return playing;
  }

  //@Override
  public void onActivityResult(int requestCode, int resultCode, Intent data) {
    switch (requestCode) {
      case REQUEST_PICKER_CODE:
        if (resultCode == Activity.RESULT_OK) {
          Uri uri = data.getData();
          String path = getPath(uri);

          // Return metadata to the library.
          pendingResult.success(AudioTrack.toJson(path));
          pendingResult = null;

          // return true;
        } else {
          pendingResult.error("NO_TRACK_SELECTED", "No track has been selected.", null);
          pendingResult = null;

          // return false;
        }

      default:
        // return false;
    }
  }

  @Override
  public void onPause() {
    super.onPause();
    onBackground();
  }

  @Override
  public void onResume() {
    super.onResume();
    onForeground();
  }

  protected void onDestroy() {
    super.onDestroy();
    Cleanup();
  }

  /*
   * Credits: https://stackoverflow.com/a/36129285/3238070
   */
  private String getPath(Uri uri) {
    // DocumentProvider.
    if (DocumentsContract.isDocumentUri(activity, uri)) {
      final String documentId = DocumentsContract.getDocumentId(uri);
      final String[] split = documentId.split(":");
      // final String type = split[0];

      Uri contentUri;

      switch (uri.getAuthority()) {
        // ExternalStorageProvider
        case "com.android.externalstorage.documents":
          return Environment.getExternalStorageDirectory() + "/" + split[1];

        // DownloadsProvider.
        case "com.android.providers.downloads.documents":
          // Treat 'raw' files. Don't know if that's the best way to do this, consider it as a temporary fix.
          if (documentId != null && documentId.startsWith("raw:")) {
            return documentId.substring("raw:".length());
          }
          contentUri = ContentUris.withAppendedId(Uri.parse("content://downloads/public_downloads"), Long.valueOf(documentId));

          return getDataColumn(contentUri, null, null);

        // MediaProvider.
        case "com.android.providers.media.documents":
          contentUri = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI;
          final String selection = "_id=?";
          final String[] selectionArgs = new String[]{split[1]};

          return getDataColumn(contentUri, selection, selectionArgs);

        default:
          return null;
      }
    }
    // Media Store.
    else if (uri.getScheme().equals("content")) {
      return getDataColumn(uri, null, null);
    }
    // File.
    else if (uri.getScheme().equals("file")) {
      return uri.getPath();
    }

    return null;
  }

  private String getDataColumn(Uri uri, String selection, String[] selectionArgs) {
    Cursor cursor = null;
    final String column = "_data";
    final String[] projection = new String[]{column};

    try {
      cursor = activity.getContentResolver().query(uri, projection, selection, selectionArgs, null);
      if (cursor != null && cursor.moveToFirst()) {
        return cursor.getString(cursor.getColumnIndexOrThrow(column));
      }
    } finally {
      if (cursor != null) {
        cursor.close();
      }
    }

    return null;
  }

  //@Override
  public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] resultCodes) {
    switch(requestCode) {
      case REQUEST_PERMISSIONS_CODE:
        // Permission granted.
        if (resultCodes[0] == 0) {
          Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
          intent.setType("audio/*");

          activity.startActivityForResult(Intent.createChooser(intent, "Open audio file"), REQUEST_PICKER_CODE);

          // return true;
        } else {
          pendingResult.error("STORAGE_PERMISSION_DENIED", "EXTERNAL_STORAGE permission denied by user.", null);
          pendingResult = null;
        }
    }

    // return false;
  }


  // Functions implemented in the native library.
  private native void StartAudio(int samplerate, int buffersize);
  private native void OpenFile(String path, int offset, int length);
  private native void TogglePlayback();
  private native void onForeground();
  private native void onBackground();
  private native void Cleanup();

  private boolean playing = false;
}
