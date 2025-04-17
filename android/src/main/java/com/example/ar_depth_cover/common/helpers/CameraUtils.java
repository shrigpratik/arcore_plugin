package com.example.ar_depth_cover.common.helpers;

import android.util.Log;
import android.util.Pair;

import com.google.ar.core.Coordinates2d;
import com.google.ar.core.Frame;

public class CameraUtils {
   /* Transform coordinates from camera image to depth image space
 * @param frame Current ARCore frame
 * @param cameraImageX X coordinate in camera image space
 * @param cameraImageY Y coordinate in camera image space
 * @param depthWidth Width of depth image
 * @param depthHeight Height of depth image
 * @return Pair of (x, y) coordinates in depth image space, or null if not valid
 */
    private android.util.Pair<Integer, Integer> transformCameraToDepthCoordinates(
            Frame frame, float cameraImageX, float cameraImageY, int depthWidth, int depthHeight) {

        // Step 1: Convert camera image coordinates to float array
        float[] cpuCoordinates = new float[] {cameraImageX, cameraImageY};
        float[] textureCoordinates = new float[2];

        // Step 2: Transform from IMAGE_PIXELS to TEXTURE_NORMALIZED
        try {
            frame.transformCoordinates2d(
                    Coordinates2d.IMAGE_PIXELS,
                    cpuCoordinates,
                    Coordinates2d.TEXTURE_NORMALIZED,
                    textureCoordinates);
        } catch (Exception e) {
            Log.e("ALIGNMENT_CONVERSION", "Error transforming coordinates", e);
            return null;
        }

        // Step 3: Check if coordinates are valid
        if (textureCoordinates[0] < 0 || textureCoordinates[1] < 0 ||
                textureCoordinates[0] > 1 || textureCoordinates[1] > 1) {
            // Invalid coordinates - outside the normalized range
            return null;
        }

        // Step 4: Convert normalized texture coordinates to depth image pixel coordinates
        int depthX = (int) (textureCoordinates[0] * depthWidth);
        int depthY = (int) (textureCoordinates[1] * depthHeight);

        // Clamp to depth image bounds
        depthX = Math.max(0, Math.min(depthWidth - 1, depthX));
        depthY = Math.max(0, Math.min(depthHeight - 1, depthY));

        return new android.util.Pair<>(depthX, depthY);
    }
}
