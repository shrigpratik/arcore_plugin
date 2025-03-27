/*
 * Copyright 2020 Google LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.ar_depth_cover.rawdepth;

import android.app.Activity;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.Intent;
import android.media.Image;
import android.media.MediaRecorder;
import android.net.Uri;
import android.opengl.GLSurfaceView;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import com.example.ar_depth_cover.common.samplerender.CameraTextureShader;
import com.example.ar_depth_cover.common.samplerender.SampleRender;

import com.google.ar.core.Anchor;
import com.google.ar.core.ArCoreApk;
import com.google.ar.core.Camera;
import com.google.ar.core.CameraIntrinsics;
import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.Session;
import com.google.ar.core.TrackingState;
import com.google.ar.core.exceptions.CameraNotAvailableException;
import com.google.ar.core.exceptions.NotYetAvailableException;

import io.flutter.plugin.common.BinaryMessenger;
import io.flutter.plugin.common.MethodChannel;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Renderer for depth data using Google's SampleRender framework.
 */
public class SampleDepthRenderer implements SampleRender.Renderer {
    private static final String TAG = SampleDepthRenderer.class.getSimpleName();
    private static final String CHANNEL_NAME = "ar_depth_cover/depth_data";

    /**
     * Interface for listening to recording state changes
     */
    public interface RecordingStateListener {
        void onRecordingStateChanged(boolean isRecording);
    }
    
    // The GL Surface view used for rendering
    private final GLSurfaceView glSurfaceView;
    
    // The application context
    private final Context context;
    
    // The ARCore session
    private Session session;
    
    // Flag to track ARCore installation
    private boolean installRequested = false;
    
    // Flag to track depth data receipt
    private boolean depthReceived = false;
    
    // Lock for thread safety when accessing the Frame
    private final Object frameInUseLock = new Object();
    
    // Timestamp of the last depth data
    private long depthTimestamp = -1;
    
    // Whether to only log depth data (vs visualizing it)
    private final boolean logDepthOnly;
    
    // Shader for camera texture
    private CameraTextureShader cameraShader;
    
    // Buffers for texture coordinates
    private FloatBuffer texCoordsIn;
    private FloatBuffer texCoordsOut;
    
    // Flutter Method Channel for sending depth data to Flutter
    private MethodChannel methodChannel;
    
    // Binary Messenger to communicate with Flutter
    private BinaryMessenger binaryMessenger;

    // A reference to the current frame for depth processing
    private Frame currentFrame;
    

    // Handler for main thread operations
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private volatile String lastSavedImagePath = null;

    // Recording state listener
    private RecordingStateListener recordingStateListener;

    /**
     * Constructs a SampleDepthRenderer with the given context.
     */
    public SampleDepthRenderer(GLSurfaceView glSurfaceView, Context context, boolean logDepthOnly) {
        this.glSurfaceView = glSurfaceView;
        this.context = context;
        this.logDepthOnly = logDepthOnly;
        
        // Initialize texture coordinate buffers as direct buffers
        ByteBuffer bbIn = ByteBuffer.allocateDirect(8 * 4); // 8 floats * 4 bytes per float
        bbIn.order(ByteOrder.nativeOrder());
        texCoordsIn = bbIn.asFloatBuffer();
        texCoordsIn.put(new float[] {0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 0.0f});
        texCoordsIn.position(0);
        
        ByteBuffer bbOut = ByteBuffer.allocateDirect(8 * 4);
        bbOut.order(ByteOrder.nativeOrder());
        texCoordsOut = bbOut.asFloatBuffer();
        
        // Setup the SampleRender
        new SampleRender(glSurfaceView, this, context.getAssets());
    }
    
    /**
     * Set the binary messenger for communication with Flutter
     */
    public void setBinaryMessenger(BinaryMessenger messenger) {
        this.binaryMessenger = messenger;
        if (messenger != null) {
            methodChannel = new MethodChannel(messenger, CHANNEL_NAME);
        }
    }

    /**
     * Helper method to get the activity from a context
     */
    private Activity getActivity(Context context) {
        while (context instanceof ContextWrapper) {
            if (context instanceof Activity) {
                return (Activity) context;
            }
            context = ((ContextWrapper) context).getBaseContext();
        }
        return null;
    }

    @Override
    public void onSurfaceCreated(SampleRender render) {
        // Use 90 degrees rotation for clockwise rotation
        int rotationToTry = 90;
        
        Log.d(TAG, "Creating camera shader with rotation: " + rotationToTry);
        
        cameraShader = new CameraTextureShader(rotationToTry);
        
        // Add explicit error checking after shader creation
        if (cameraShader == null || cameraShader.getTextureId() <= 0) {
            Log.e(TAG, "Failed to create camera texture shader or invalid texture ID");
        } else {
            Log.d(TAG, "Camera shader created successfully with texture ID: " + cameraShader.getTextureId());
        }
    }

    @Override
    public void onSurfaceChanged(SampleRender render, int width, int height) {
        render.setViewport(width, height);
    }

    @Override
    public void onDrawFrame(SampleRender render) {
        if (session == null) {
            return;
        }

        synchronized (frameInUseLock) {
            try {
                // First make sure we have a valid texture
                if (cameraShader == null) {
                    Log.e(TAG, "Camera shader is null, recreating");
                    cameraShader = new CameraTextureShader(90); // Use same rotation as above
                    return;
                }
                
                // Set the camera texture and update the session
                int textureId = cameraShader.getTextureId();
                if (textureId <= 0) {
                    Log.e(TAG, "Invalid texture ID: " + textureId);
                    return;
                }
                
                // Set texture name before session update
                session.setCameraTextureName(textureId);
                
                // Update session to get latest frame
                Frame frame;
                try {
                    frame = session.update();
                } catch (Exception e) {
                    Log.e(TAG, "Exception updating session", e);
                    return;
                }
                
                // Store reference to current frame for manual processing
                currentFrame = frame;
                
                // Reset the positions of the texture coordinate buffers
                texCoordsIn.clear();
                texCoordsIn.put(new float[] {0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 0.0f});
                texCoordsIn.position(0);
                
                texCoordsOut.clear();
                texCoordsOut.position(0);
                
                // Try to transform texture coordinates
                boolean validCoords = false;
                try {
                    frame.transformDisplayUvCoords(texCoordsIn, texCoordsOut);
                    
                    // Check if we got NaN values
                    texCoordsOut.position(0);
                    boolean hasNaN = false;
                    for (int i = 0; i < 8 && i < texCoordsOut.capacity(); i++) {
                        float val = texCoordsOut.get();
                        if (Float.isNaN(val)) {
                            hasNaN = true;
                            break;
                        }
                    }
                    
                    if (hasNaN) {
                        Log.w(TAG, "Detected NaN values in transformed texture coordinates");
                    } else {
                        validCoords = true;
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Exception transforming UV coordinates", e);
                }
                
                // If we didn't get valid transformed coordinates, use default coordinates
                if (!validCoords) {
                    Log.d(TAG, "Using default texture coordinates instead of transformed ones");
                    texCoordsOut.clear();
                    
                    // Coordinates for 90 degrees clockwise rotation
                    float[] fallbackCoords = {
                        1.0f, 1.0f,  // bottom-left rotated 90° clockwise
                        0.0f, 1.0f,  // top-left rotated 90° clockwise
                        1.0f, 0.0f,  // bottom-right rotated 90° clockwise
                        0.0f, 0.0f   // top-right rotated 90° clockwise
                    };
                    
                    texCoordsOut.put(fallbackCoords);
                    texCoordsOut.position(0);
                } else {
                    // Reset position after NaN check
                    texCoordsOut.position(0);
                }
                
                // Update shader texture coordinates
                cameraShader.updateTextureCoordinates(texCoordsOut);
                
                // Clear the render target with black background
                render.clear(0f, 0f, 0f, 1f);
                
                // Draw camera background
                cameraShader.draw();
                
                // Process depth data if camera is tracking
                Camera camera = frame.getCamera();
                if (camera.getTrackingState() == TrackingState.TRACKING) {
                    // Uncomment if you want to process depth data every frame
//                     processDepthData(frame);
                }
            } catch (Exception e) {
                Log.e(TAG, "Exception on the OpenGL thread", e);
            }
        }
    }

    /**
     * Process depth data manually when called (e.g., from button click)
     * This method is intended to be called from outside the GL thread
     */
    public void processDepthDataManually() {
        synchronized (frameInUseLock) {
            if (session == null) {
                Log.e(TAG, "Cannot process depth data: session is null");
                return;
            }
            
            try {
                if (currentFrame != null) {
                    Camera camera = currentFrame.getCamera();
                    if (camera.getTrackingState() == TrackingState.TRACKING) {
                        Log.d(TAG, "Processing depth data manually");
                        
                  
                        processDepthData(currentFrame);
    

                    } else {
                        Log.w(TAG, "Cannot process depth data: camera not tracking");
                    }
                } else {
                    Log.w(TAG, "Cannot process depth data: current frame is null");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error processing depth data manually", e);
            }
        }
    }

    /**
     * Process depth data from the frame
     */
    private void processDepthData(Frame frame) {
        try (Image cameraImage = frame.acquireCameraImage();
             Image depthImage = frame.acquireDepthImage16Bits();
             Image confidenceImage = frame.acquireRawDepthConfidenceImage()) {

            if (depthTimestamp != depthImage.getTimestamp()) {
                depthTimestamp = depthImage.getTimestamp();
                depthReceived = true;
                
                // Get the camera pose matrix - this is the transformation matrix
                float[] modelMatrix = new float[16];
                frame.getCamera().getPose().toMatrix(modelMatrix, 0);
                
                // Get view matrix - another useful transformation matrix
                float[] viewMatrix = new float[16];
                frame.getCamera().getViewMatrix(viewMatrix, 0);
                
                // Get projection matrix - useful for projecting 3D points to 2D screen coordinates
                float[] projectionMatrix = new float[16];
                frame.getCamera().getProjectionMatrix(projectionMatrix, 0, 0.1f, 100.0f);
                
                CameraIntrinsics intrinsics = frame.getCamera().getTextureIntrinsics();
                Image.Plane depthImagePlane = depthImage.getPlanes()[0];
                final Camera camera = frame.getCamera();
                Anchor anchor = session.createAnchor(camera.getPose());
                anchor.getPose().toMatrix(modelMatrix, 0);
                
                // Save image asynchronously without waiting
                saveImageAsync(cameraImage);
                
                // Use the last saved image path (might be null if saving is still in progress)
                String imagePath = lastSavedImagePath;
                
                // Set the endianess to ensure we extract depth data in the correct byte order.
                ShortBuffer depthBuffer =
                        depthImagePlane.getBuffer().order(ByteOrder.nativeOrder()).asShortBuffer();

                Image.Plane confidenceImagePlane = confidenceImage.getPlanes()[0];
                ByteBuffer confidenceBuffer = confidenceImagePlane.getBuffer().order(ByteOrder.nativeOrder());
                
                // To transform 2D depth pixels into 3D points we retrieve the intrinsic camera parameters
                // corresponding to the depth miage. See more information about the depth values at
                int[] intrinsicsDimensions = intrinsics.getImageDimensions();
                int depthWidth = depthImage.getWidth();
                int depthHeight = depthImage.getHeight();
                float fx = intrinsics.getFocalLength()[0] * depthWidth / intrinsicsDimensions[0];
                float fy = intrinsics.getFocalLength()[1] * depthHeight / intrinsicsDimensions[1];
                float cx =
                        intrinsics.getPrincipalPoint()[0] * depthWidth / intrinsicsDimensions[0];
                float cy =
                        intrinsics.getPrincipalPoint()[1] * depthHeight / intrinsicsDimensions[1];
                
                // Convert raw depth images to depth in meters
                FloatBuffer depthInMeters = convertRawDepthImageToMeters(depthImage, confidenceImage);
                
                List<Float> sampledDepth = new ArrayList<>();
                int stride = 1;
                
                for (int i = 0; i < depthInMeters.capacity(); i += stride) {
                    sampledDepth.add(depthInMeters.get(i));
                }
                
                // Log depth point buffer info
                Log.d(TAG, "Depth 3D points received - capacity: " + sampledDepth.size() + 
                      ", number of points: " + sampledDepth.size());
                
                // Send the depth data to Flutter
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    sendCompleteDepthDataToFlutter(
                        sampledDepth.stream().mapToDouble(Float::doubleValue).toArray(),
                        intrinsicsDimensions,
                        depthWidth,
                        depthHeight,
                        fx,
                        fy, 
                        cx, 
                        cy,
                        confidenceImage,
                        imagePath,
                        depthTimestamp
                    );
                }
            } else {
                Log.d(TAG, "Skipping depth processing - same timestamp as before: " + depthTimestamp);
            }
        } catch (NotYetAvailableException e) {
            // Depth is not available yet
            Log.w(TAG, "Depth data not yet available");
        } catch (Exception e) {
            Log.e(TAG, "Error processing depth data", e);
        }
    }

    /**
     * Send both 2D depth data, 3D points, and raw images to Flutter via the method channel
     */
    private void sendCompleteDepthDataToFlutter(
            double[] depthArray,
            int[] intrinsicsDimensions,
            int depthWidth,
            int depthHeight,
            float fx,
            float fy,
            float cx,
            float cy,
            Image confidenceImage,
            String cameraImagePath,
            long timestamp) {
            
        if (methodChannel == null) {
            Log.w(TAG, "Method channel not available to send depth data");
            return;
        }
        
        try {
            // Create a map with all the depth data
            Map<String, Object> combinedData = new HashMap<>();
            combinedData.put("timestamp", timestamp);
            
            // Add 2D depth data
            combinedData.put("intrinsicsWidth", intrinsicsDimensions[0]);
            combinedData.put("intrinsicsHeight", intrinsicsDimensions[1]);
            combinedData.put("depthWidth", depthWidth);
            combinedData.put("depthHeight", depthHeight);
            combinedData.put("focalLengthX", fx);
            combinedData.put("focalLengthY", fy);
            combinedData.put("principalPointX", cx);
            combinedData.put("principalPointY", cy);
            combinedData.put("depthImage", depthArray);
            combinedData.put("imagePath", cameraImagePath);
            addRawImageDataToMap(combinedData, "confidenceImage", confidenceImage);

            // Add the transformation matrices if available
            Frame frame = currentFrame;
            if (frame != null) {
                float[] modelMatrix = new float[16];
                float[] viewMatrix = new float[16];
                float[] projectionMatrix = new float[16];
                
                frame.getCamera().getPose().toMatrix(modelMatrix, 0);
                frame.getCamera().getViewMatrix(viewMatrix, 0);
                frame.getCamera().getProjectionMatrix(projectionMatrix, 0, 0.1f, 100.0f);
                
                // Calculate MVP matrix (P × V × M)
                float[] mvMatrix = new float[16];
                float[] mvpMatrix = new float[16];
                android.opengl.Matrix.multiplyMM(mvMatrix, 0, viewMatrix, 0, modelMatrix, 0);
                android.opengl.Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, mvMatrix, 0);
                
                // Convert float arrays to lists for sending via method channel
                List<Float> modelMatrixList = new ArrayList<>();
                List<Float> viewMatrixList = new ArrayList<>();
                List<Float> projectionMatrixList = new ArrayList<>();
                List<Float> mvpMatrixList = new ArrayList<>();
                
                for (int i = 0; i < 16; i++) {
                    modelMatrixList.add(modelMatrix[i]);
                    viewMatrixList.add(viewMatrix[i]);
                    projectionMatrixList.add(projectionMatrix[i]);
                    mvpMatrixList.add(mvpMatrix[i]);
                }
                
                combinedData.put("modelMatrix", modelMatrixList);
                combinedData.put("viewMatrix", viewMatrixList);
                combinedData.put("projectionMatrix", projectionMatrixList);
                combinedData.put("transformMatrix", mvpMatrixList);
            }
            
            // Send the combined data to Flutter
            Activity activity = getActivity(context);
            if (activity != null) {
                activity.runOnUiThread(() -> {
                    try {
                        methodChannel.invokeMethod("onDepthDataReceived", combinedData);
                    } catch (Exception e) {
                        Log.e(TAG, "Error sending complete depth data to Flutter", e);
                    }
                });
            }
        } catch (Exception e) {
            Log.e(TAG, "Error processing complete depth data", e);
        }
    }
    
    /**
     * Helper to add raw image data to the map
     */
    private void addRawImageDataToMap(Map<String, Object> map, String key, Image image) {
        try {
            Map<String, Object> imageData = new HashMap<>();
            Log.d(key, image.toString());
            imageData.put("width", image.getWidth());
            imageData.put("height", image.getHeight());
            imageData.put("format", image.getFormat());
            Log.d(key + " format", String.valueOf(image.getFormat()));
            
            List<Map<String, Object>> planes = new ArrayList<>();
            
            for (int i = 0; i < image.getPlanes().length; i++) {
                Image.Plane plane = image.getPlanes()[i];
                ByteBuffer buffer = plane.getBuffer();
                
                // Create a copy of the buffer since the original buffer may be invalid after this method returns
                ByteBuffer copy = ByteBuffer.allocate(buffer.capacity());
                copy.order(buffer.order()); // Match the byte order
                
                // Make sure we read from the beginning of the original buffer
                buffer.rewind();
                copy.put(buffer);
                copy.rewind();
                
                // Convert to byte array for sending through the method channel
                byte[] bytes = new byte[copy.remaining()];
                copy.get(bytes);
                
                Map<String, Object> planeData = new HashMap<>();
                planeData.put("bytesPerPixel", plane.getPixelStride());
                planeData.put("bytesPerRow", plane.getRowStride());
                planeData.put("data", bytes);
                
                planes.add(planeData);
            }
            
            imageData.put("planes", planes);
            map.put(key, imageData);
            
            Log.d(TAG, "Added " + key + " data: " + image.getWidth() + "x" + image.getHeight() + 
                  ", " + planes.size() + " planes");
        } catch (Exception e) {
            Log.e(TAG, "Error adding " + key + " data to map", e);
        }
    }

    public static final int FLOATS_PER_POINT = 4; // X,Y,Z,confidence.
    /**
     * Converts the raw depth image to depth values in meters
     * @param depth The depth image
     * @param confidence The confidence image
     * @return A FloatBuffer containing depth values in meters for each pixel
     */
    private static FloatBuffer
    convertRawDepthImageToMeters(Image depth, Image confidence) {
        // Java uses big endian so change the endianness to ensure
        // that the depth data is in the correct byte order.
        final Image.Plane depthImagePlane = depth.getPlanes()[0];
        ByteBuffer depthByteBufferOriginal = depthImagePlane.getBuffer();
        ByteBuffer depthByteBuffer = ByteBuffer.allocate(depthByteBufferOriginal.capacity());
        depthByteBuffer.order(ByteOrder.LITTLE_ENDIAN);
        while (depthByteBufferOriginal.hasRemaining()) {
            depthByteBuffer.put(depthByteBufferOriginal.get());
        }
        depthByteBuffer.rewind();
        ShortBuffer depthBuffer = depthByteBuffer.asShortBuffer();

        // Get confidence data
        final Image.Plane confidenceImagePlane = confidence.getPlanes()[0];
        ByteBuffer confidenceBufferOriginal = confidenceImagePlane.getBuffer();
        ByteBuffer confidenceBuffer = ByteBuffer.allocate(confidenceBufferOriginal.capacity());
        confidenceBuffer.order(ByteOrder.LITTLE_ENDIAN);
        while (confidenceBufferOriginal.hasRemaining()) {
            confidenceBuffer.put(confidenceBufferOriginal.get());
        }
        confidenceBuffer.rewind();

        // Get dimensions
        int depthWidth = depth.getWidth();
        int depthHeight = depth.getHeight();
        
        // Create output buffer for depth in meters
        FloatBuffer depthMeters = FloatBuffer.allocate(depthWidth * depthHeight);
        Log.d("DEPTH_DATA_1", String.valueOf(depth.getPlanes().length));
        Log.d("DEPTH_DATA_2", String.valueOf(depth.getPlanes()[0].getBuffer().capacity()));
//        Log.d("DEPTH_DATA_3", String.valueOf(depth.getPlanes()[0]));
        // Convert each depth value from millimeters to meters
        for (int y = 0; y < depthHeight; y++) {
            for (int x = 0; x < depthWidth; x++) {
                int idx = y * depthWidth + x;
                int depthMillimeters = depthBuffer.get(idx);
               depthMeters.put(idx, depthMillimeters / 1000.0f);}
            }

        
        depthMeters.rewind();
        return depthMeters;
    }
    
    /**
     * Initialize and resume the AR session
     */
    public void resume() {
        if (session == null) {
            try {
                Activity activity = getActivity(context);
                if (activity == null) {
                    Log.e(TAG, "Cannot start AR session - no activity available");
                    return;
                }

                // Check if ARCore is installed
                switch (ArCoreApk.getInstance().requestInstall(activity, !installRequested)) {
                    case INSTALL_REQUESTED:
                        installRequested = true;
                        return;
                    case INSTALLED:
                        break;
                }

                // Create and configure session
                session = new Session(context);
                if (!session.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                    Log.e(TAG, "This device does not support 3D measuring with raw depth");
                    // Don't set session to null, let's try to continue with a regular ARCore session
                }
                
                Config config = session.getConfig();
                config.setDepthMode(Config.DepthMode.AUTOMATIC);
                
                // Try updating to different focus modes if applicable
                config.setFocusMode(Config.FocusMode.AUTO);
                
                // Configure light estimation if needed
                config.setLightEstimationMode(Config.LightEstimationMode.AMBIENT_INTENSITY);
                
                session.configure(config);
                
                // Resume the session
                try {
                    session.resume();
                    Log.d(TAG, "AR session successfully resumed");
                } catch (CameraNotAvailableException e) {
                    Log.e(TAG, "Camera not available. Try reopening the app.", e);
                    session = null;
                }
            } catch (Exception e) {
                Log.e(TAG, "Failed to create AR session", e);
                session = null;
            }
        } else {
            try {
                session.resume();
            } catch (CameraNotAvailableException e) {
                Log.e(TAG, "Camera not available during resume", e);
                session = null;
            }
        }
        
        // Resume the GL surface view
        if (glSurfaceView != null) {
            glSurfaceView.onResume();
        }
    }

    /**
     * Pause the AR session
     */
    public void pause() {
        if (session != null) {
            if (glSurfaceView != null) {
                glSurfaceView.onPause();
            }
            session.pause();
        }
    }

    /**
     * Saves Image to disk asynchronously and returns immediately
     * The result will be available in lastSavedImagePath
     */
    private void saveImageAsync(Image imageToSave) {
        // Create a copy of the image data since the original image might be invalidated
        final Image.Plane[] planes = imageToSave.getPlanes();
        final ByteBuffer[] buffers = new ByteBuffer[3];
        final int[] strides = new int[3];
        final int[] pixelStrides = new int[3];
        
        for (int i = 0; i < 3; i++) {
            ByteBuffer originalBuffer = planes[i].getBuffer();
            buffers[i] = ByteBuffer.allocate(originalBuffer.capacity());
            buffers[i].order(originalBuffer.order());
            buffers[i].put(originalBuffer);
            buffers[i].rewind();
            strides[i] = planes[i].getRowStride();
            pixelStrides[i] = planes[i].getPixelStride();
        }
        
        final int width = imageToSave.getWidth();
        final int height = imageToSave.getHeight();
        
        executorService.execute(() -> {
            try {
                String result = saveImageInBackground(
                    buffers, strides, pixelStrides, width, height);
                lastSavedImagePath = result;
                Log.d(TAG, "Image saved asynchronously at: " + result);
            } catch (Exception e) {
                Log.e(TAG, "Error saving image asynchronously", e);
                lastSavedImagePath = null;
            }
        });
    }

    /**
     * Saves Image to disk in a background thread
     * This method contains the actual image saving logic
     */
    private String saveImageInBackground(
            ByteBuffer[] buffers, int[] strides, int[] pixelStrides,
            int width, int height) throws IOException {
        // Use Downloads directory which is more accessible to users
        File mediaStorageDir = new File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
                "ar_images");

        if (!mediaStorageDir.exists()) {
            if (!mediaStorageDir.mkdirs()) {
                Log.e(TAG, "Failed to create directory for image storage");
                // Try fallback to internal storage
                mediaStorageDir = new File(context.getFilesDir(), "ar_images");
                if (!mediaStorageDir.exists() && !mediaStorageDir.mkdirs()) {
                    Log.e(TAG, "Failed to create fallback directory for image storage");
                    return null;
                }
            }
        }

        // Create unique filename with timestamp
        String timeStamp = String.valueOf(System.currentTimeMillis());
        File imageFile = new File(mediaStorageDir, "IMG_" + timeStamp + ".jpg");
        String imageOutputPath = imageFile.getAbsolutePath();
        
        // Create data arrays for processing
        byte[] yData = new byte[buffers[0].capacity()];
        byte[] uData = new byte[buffers[1].capacity()];
        byte[] vData = new byte[buffers[2].capacity()];
        
        // Copy data from buffers
        buffers[0].get(yData);
        buffers[1].get(uData);
        buffers[2].get(vData);
        
        // Create bitmap for RGB output
        android.graphics.Bitmap bitmap = android.graphics.Bitmap.createBitmap(height, width, android.graphics.Bitmap.Config.ARGB_8888);
        
        // Convert YUV to RGB and rotate 90 degrees counterclockwise
        for (int x = 0; x < width; x++) {
            for (int y = 0; y < height; y++) {
                // Calculate indices for YUV data
                int yIndex = y * strides[0] + x;
                int uvIndex = (y / 2) * strides[1] + (x / 2) * pixelStrides[1];
                
                // Get YUV values
                int yValue = yData[yIndex] & 0xFF;
                int uValue = uData[uvIndex] & 0xFF;
                int vValue = vData[uvIndex] & 0xFF;
                
                // Convert YUV to RGB
                uValue = uValue - 128;
                vValue = vValue - 128;
                
                int r = (int)(yValue + 1.402f * vValue);
                int g = (int)(yValue - 0.344f * uValue - 0.714f * vValue);
                int b = (int)(yValue + 1.772f * uValue);
                
                // Clamp RGB values
                r = r > 255 ? 255 : (r < 0 ? 0 : r);
                g = g > 255 ? 255 : (g < 0 ? 0 : g);
                b = b > 255 ? 255 : (b < 0 ? 0 : b);
                
                // Create ARGB color
                int color = android.graphics.Color.argb(255, r, g, b);
                
                // Set pixel in rotated position (90 degrees counterclockwise)
                bitmap.setPixel(height - 1 - y, x, color);
            }
        }
        
        // Save the bitmap as JPEG
        try (java.io.FileOutputStream out = new java.io.FileOutputStream(imageFile)) {
            bitmap.compress(android.graphics.Bitmap.CompressFormat.JPEG, 100, out);
        } finally {
            bitmap.recycle();
        }
        
        return imageOutputPath;
    }

    /**
     * Clean up resources used by the renderer
     */
    public void close() {
        if (session != null) {
            session.close();
            session = null;
        }
        
        if (cameraShader != null) {
            cameraShader.release();
            cameraShader = null;
        }

        // Shutdown the executor service
        executorService.shutdown();
        try {
            if (!executorService.awaitTermination(1, TimeUnit.SECONDS)) {
                executorService.shutdownNow();
            }
        } catch (InterruptedException e) {
            executorService.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
    

} 