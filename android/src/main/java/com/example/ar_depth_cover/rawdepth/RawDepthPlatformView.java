package com.example.ar_depth_cover.rawdepth;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.opengl.GLSurfaceView;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.view.Gravity;
import android.util.Log;
import android.widget.Toast;
import java.util.Map;

import androidx.annotation.NonNull;

import com.example.ar_depth_cover.R;

import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.StandardMessageCodec;
import io.flutter.plugin.platform.PlatformView;
import io.flutter.plugin.platform.PlatformViewFactory;

@SuppressLint("ViewConstructor")
public class RawDepthPlatformView extends FrameLayout implements PlatformView {
    private final GLSurfaceView surfaceView;
    private final SampleDepthRenderer renderer;
    private static final String TAG = "RawDepthPlatformView";

    public RawDepthPlatformView(Context context, int viewId, Object args, BinaryMessenger messenger) {
        super(context);
        
        // Parse creation parameters
        boolean logDepthOnly = true;
        if (args instanceof Map) {
            Map<String, Object> params = (Map<String, Object>) args;
            if (params.containsKey("logDepthOnly")) {
                logDepthOnly = (boolean) params.get("logDepthOnly");
            }
        }
        
        // Create and configure GLSurfaceView
        surfaceView = new GLSurfaceView(context);
        surfaceView.setPreserveEGLContextOnPause(true);
        
        // Use OpenGL ES 2.0 instead of 3.0 for better compatibility
        surfaceView.setEGLContextClientVersion(2);
        
        // Important: Use a proper config with alpha channel for AR
        surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
        
        // Set Z order on top to ensure visibility
        surfaceView.setZOrderOnTop(false);
        surfaceView.setZOrderMediaOverlay(true);
        
        // Set proper layout params
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        );
        surfaceView.setLayoutParams(params);
        
        // Create the renderer with logDepthOnly flag
        renderer = new SampleDepthRenderer(surfaceView, context, logDepthOnly);
        
        // Set the binary messenger for communication with Flutter
        renderer.setBinaryMessenger(messenger);
        
        // Add the GLSurfaceView to this FrameLayout
        addView(surfaceView);
        
        // Create and add a circular red button
        addCircularRedButton(context);
        
        // Start AR session
        Log.d(TAG, "Starting AR session");
        renderer.resume();
    }

    @Override
    public View getView() {
        return this;
    }

    @Override
    public void dispose() {
        Log.d(TAG, "Disposing RawDepthPlatformView");
        if (surfaceView != null) {
            surfaceView.onPause();
            surfaceView.queueEvent(() -> {
                renderer.close();
            });
        }
    }

    public void onResume() {
        Log.d(TAG, "onResume called");
        if (surfaceView != null) {
            surfaceView.onResume();
        }
        if (renderer != null) {
            renderer.resume();
        }
    }

    public void onPause() {
        Log.d(TAG, "onPause called");
        if (renderer != null) {
            renderer.pause();
        }
        if (surfaceView != null) {
            surfaceView.onPause();
        }
    }

    /**
     * Adds a circular red button to the bottom right of the view
     */
    private void addCircularRedButton(Context context) {
        try {
            // Create the button
            ImageButton circularButton = new ImageButton(context);
            
            // Set the drawable background resource
            int drawableId = R.drawable.circular_red_button;
            circularButton.setBackgroundResource(drawableId);
            
            // Set button properties
            FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            );
            
            // Position at bottom right with margins
            params.gravity = Gravity.BOTTOM | Gravity.CENTER;
            params.bottomMargin = 50; // 50px from bottom
            params.rightMargin = 50;  // 50px from right
            circularButton.setLayoutParams(params);
            
            // Set click listener
            circularButton.setOnClickListener(v -> {
                Toast.makeText(context, "Processing depth data...", Toast.LENGTH_SHORT).show();
                Log.d(TAG, "Circular red button clicked - processing depth data");
                
                // Call the renderer's processDepthData method
                if (renderer != null) {
                    renderer.processDepthDataManually();
                } else {
                    Log.e(TAG, "Renderer is null, cannot process depth data");
                }
            });
            
            // Add the button to our FrameLayout (on top of the GLSurfaceView)
            addView(circularButton);
            
            Log.d(TAG, "Added circular red button successfully");
        } catch (Exception e) {
            Log.e(TAG, "Error adding circular red button", e);
        }
    }

    /**
     * Factory for creating RawDepthPlatformView instances
     */
    public static class Factory extends PlatformViewFactory {
        private Activity activity;
        private BinaryMessenger messenger;
        
        public Factory() {
            super(StandardMessageCodec.INSTANCE);
        }
        
        public void setActivity(Activity activity) {
            this.activity = activity;
        }
        
        public void setBinaryMessenger(BinaryMessenger messenger) {
            this.messenger = messenger;
        }

        @Override
        public PlatformView create(Context context, int viewId, Object args) {
            return new RawDepthPlatformView(
                activity != null ? activity : context, 
                viewId, 
                args, 
                messenger);
        }
    }
}