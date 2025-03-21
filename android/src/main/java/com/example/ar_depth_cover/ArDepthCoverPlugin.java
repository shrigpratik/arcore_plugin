package com.example.ar_depth_cover;

import android.app.Activity;
import androidx.annotation.NonNull;

import io.flutter.embedding.engine.plugins.FlutterPlugin;
import io.flutter.embedding.engine.plugins.activity.ActivityAware;
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding;
import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodCall;
import io.flutter.plugin.common.MethodChannel;
import io.flutter.plugin.common.MethodChannel.MethodCallHandler;
import io.flutter.plugin.common.MethodChannel.Result;
import io.flutter.plugin.platform.PlatformViewRegistry;

import com.example.ar_depth_cover.rawdepth.RawDepthPlatformView;

/** ArDepthCoverPlugin */
public class ArDepthCoverPlugin implements FlutterPlugin, MethodCallHandler, ActivityAware {
  private static final String VIEW_TYPE = "ar_depth_view";
  /// The MethodChannel that will the communication between Flutter and native Android
  ///
  /// This local reference serves to register the plugin with the Flutter Engine and unregister it
  /// when the Flutter Engine is detached from the Activity
  private MethodChannel channel;
  private RawDepthPlatformView.Factory viewFactory;
  private Activity activity;
  private BinaryMessenger binaryMessenger;

  @Override
  public void onAttachedToEngine(@NonNull FlutterPluginBinding flutterPluginBinding) {
    channel = new MethodChannel(flutterPluginBinding.getBinaryMessenger(), "ar_depth_cover");
    
    channel.setMethodCallHandler(this);
    
    // Store the binary messenger
    binaryMessenger = flutterPluginBinding.getBinaryMessenger();

    // Create the factory and store it
    viewFactory = new RawDepthPlatformView.Factory();
    if (binaryMessenger != null) {
      viewFactory.setBinaryMessenger(binaryMessenger);
    }
    
    // Register the platform view factory
    PlatformViewRegistry registry = flutterPluginBinding.getPlatformViewRegistry();
    registry.registerViewFactory(VIEW_TYPE, viewFactory);
  }

  @Override
  public void onMethodCall(@NonNull MethodCall call, @NonNull Result result) {
    if (call.method.equals("getPlatformVersion")) {
      result.success("Android " + android.os.Build.VERSION.RELEASE);
    } else {
      result.notImplemented();
    }
  }

  @Override
  public void onDetachedFromEngine(@NonNull FlutterPluginBinding binding) {
    channel.setMethodCallHandler(null);
    binaryMessenger = null;
  }

  @Override
  public void onAttachedToActivity(@NonNull ActivityPluginBinding binding) {
    activity = binding.getActivity();
    if (viewFactory != null) {
      viewFactory.setActivity(activity);
    }
  }

  @Override
  public void onDetachedFromActivityForConfigChanges() {
    activity = null;
  }

  @Override
  public void onReattachedToActivityForConfigChanges(@NonNull ActivityPluginBinding binding) {
    activity = binding.getActivity();
    if (viewFactory != null) {
      viewFactory.setActivity(activity);
    }
  }

  @Override
  public void onDetachedFromActivity() {
    activity = null;
  }
}
