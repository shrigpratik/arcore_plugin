package com.example.ar_depth_cover.common.helpers;

import android.os.Build;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.ArrayList;
import java.util.List;

public class MultiFrameDepthProcessor {
    private static final int MAX_DEPTH_FRAMES = 5; // Number of frames to average
    private static final float CONFIDENCE_THRESHOLD = 0.7f; // 70% confidence threshold
    private static final long MAX_FRAME_AGE_MILLIS = 1000; // 1 second

    // Circular buffer to store recent depth frames
    final private List<DepthFrame> depthFrames = new ArrayList<>();
    
    /**
     * Represents a single depth frame with its depth data and metadata
     */
    private static class DepthFrame {
        final FloatBuffer depthBuffer;
        final ByteBuffer confidenceBuffer;
        final long timestamp;
        
        DepthFrame(FloatBuffer depthBuffer, ByteBuffer confidenceBuffer, long timestamp) {
            this.depthBuffer = depthBuffer;
            this.confidenceBuffer = confidenceBuffer;
            this.timestamp = timestamp;
        }
    }
    
    /**
     * Process and average depth data across multiple frames
     * 
     * @param newDepthBuffer Raw depth buffer
     * @param newConfidenceBuffer Confidence buffer
     * @param timestamp Timestamp of the current frame
     * @return Averaged and filtered depth buffer
     */
    public FloatBuffer processMultiFrameDepth(
            ShortBuffer newDepthBuffer,
            ByteBuffer newConfidenceBuffer, 
            long timestamp,
            int width,
            int height
            ) {

        pruneOldFrames(timestamp);
        
        // Convert raw depth to meters with initial filtering
        FloatBuffer processedDepthBuffer = convertDepthToMeters(
            newDepthBuffer, 
            newConfidenceBuffer,
                width,
                height
        );
        
        // Create a new depth frame
        DepthFrame newFrame = new DepthFrame(
            processedDepthBuffer, 
            newConfidenceBuffer, 
            timestamp
        );
        
        // Add new frame to the buffer
        depthFrames.add(newFrame);
        
        // Limit buffer size
        if (depthFrames.size() > MAX_DEPTH_FRAMES) {
            depthFrames.remove(0);
        }
        
        // Compute multi-frame averaged depth
        return computeAveragedDepth(width,height);
    }
    
    /**
     * Convert raw depth data to meters with confidence filtering
     */
    private FloatBuffer convertDepthToMeters(
            ShortBuffer depthBuffer,
            ByteBuffer confidenceBuffer,
            int width,
            int height
            ) {

        FloatBuffer depthMeters = FloatBuffer.allocate(width * height);

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                int idx = y * width + x;

                // Get depth and confidence values
                int depthMillimeters = depthBuffer.get(idx);
                byte confidenceValue = confidenceBuffer.get(idx);

                // Apply confidence threshold
                float confidenceRatio = (confidenceValue & 0xFF) / 255.0f;

                if (confidenceRatio >= CONFIDENCE_THRESHOLD) {
                    // Convert to meters and store
                    depthMeters.put(idx, depthMillimeters / 1000.0f);
                } else {
                    // Mark as invalid
                    depthMeters.put(idx, Float.NaN);
                }
            }
        }

        depthMeters.rewind();
        return depthMeters;
    }
    
    /**
     * Compute averaged depth across multiple frames
     * Implements weighted averaging with recency bias
     */
    private FloatBuffer computeAveragedDepth(int width,int height) {
        if (depthFrames.isEmpty()) {
            return null;
        }
        
        // Get dimensions from first frame
        DepthFrame firstFrame = depthFrames.get(0);

        
        FloatBuffer averagedDepth = FloatBuffer.allocate(width * height);
        
        for (int idx = 0; idx < width * height; idx++) {
            float weightedDepthSum = 0f;
            float totalWeight = 0f;
            
            // Iterate through frames with recency bias
            for (int i = 0; i < depthFrames.size(); i++) {
                DepthFrame frame = depthFrames.get(i);
                float depth = frame.depthBuffer.get(idx);
                
                // Skip invalid depth values
                if (Float.isNaN(depth)) continue;
                
                // Apply recency weighting (more recent frames have higher weight)
                float weight = (i + 1.0f) / depthFrames.size();
                
                weightedDepthSum += depth * weight;
                totalWeight += weight;
            }
            
            // Compute final averaged depth
            float averagedValue = totalWeight > 0 
                ? weightedDepthSum / totalWeight 
                : Float.NaN;
            
            averagedDepth.put(idx, averagedValue);
        }
        
        averagedDepth.rewind();
        return averagedDepth;
    }
    
    /**
     * Optional: Remove old frames based on timestamp
     */
    private void pruneOldFrames(long currentTimestamp) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            depthFrames.removeIf(frame ->
                currentTimestamp - frame.timestamp > MultiFrameDepthProcessor.MAX_FRAME_AGE_MILLIS
            );
        }
    }
}