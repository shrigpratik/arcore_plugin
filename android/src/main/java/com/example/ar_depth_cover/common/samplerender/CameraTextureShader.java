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
package com.example.ar_depth_cover.common.samplerender;

import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.util.Log;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/**
 * A shader that renders a texture from the camera onto the screen.
 */
public class CameraTextureShader {
    private static final String TAG = CameraTextureShader.class.getSimpleName();

    // Quad vertices
    private static final float[] QUAD_VERTICES = new float[] {
            -1.0f, -1.0f, 0.0f,
            -1.0f, 1.0f, 0.0f,
            1.0f, -1.0f, 0.0f,
            1.0f, 1.0f, 0.0f,
    };

    // Default texture coordinates (before transformation)
    private static final float[] QUAD_TEXCOORDS = new float[] {
            0.0f, 1.0f,  // bottom-left - flipped vertically (was 0.0f, 0.0f)
            0.0f, 0.0f,  // top-left - flipped vertically (was 0.0f, 1.0f)
            1.0f, 1.0f,  // bottom-right - flipped vertically (was 1.0f, 0.0f)
            1.0f, 0.0f,  // top-right - flipped vertically (was 1.0f, 1.0f)
    };

    // Vertex shader with rotation support
    private static final String VERTEX_SHADER =
            "attribute vec4 aPosition;\n" +
            "attribute vec2 aTexCoord;\n" +
            "varying vec2 vTexCoord;\n" +
            "void main() {\n" +
            "    gl_Position = aPosition;\n" +
            "    vTexCoord = aTexCoord;\n" +
            "}\n";

    // Fragment shader for the camera texture with better error handling
    private static final String FRAGMENT_SHADER =
            "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +
            "uniform samplerExternalOES uTexture;\n" +
            "varying vec2 vTexCoord;\n" +
            "void main() {\n" +
            "    vec4 color = texture2D(uTexture, vTexCoord);\n" +
            "    // Ensure non-zero alpha to avoid transparency issues\n" +
            "    gl_FragColor = vec4(color.rgb, 1.0);\n" +
            "}\n";

    private int program;
    private int positionAttrib;
    private int texCoordAttrib;
    private int textureUniform;
    private int textureId = -1;

    private final FloatBuffer quadVertices;
    private final FloatBuffer quadTexCoords;
    private FloatBuffer transformedTexCoords;
    private float rotation;

    /**
     * Constructor for CameraTextureShader.
     * 
     * @param rotation Rotation in degrees to apply to the texture.
     */
    public CameraTextureShader(float rotation) {
        this.rotation = rotation;
        Log.d(TAG, "Creating CameraTextureShader with rotation: " + rotation);
        
        // Initialize vertex buffers
        quadVertices = ByteBuffer.allocateDirect(QUAD_VERTICES.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        quadVertices.put(QUAD_VERTICES).position(0);

        quadTexCoords = ByteBuffer.allocateDirect(QUAD_TEXCOORDS.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        quadTexCoords.put(QUAD_TEXCOORDS).position(0);

        transformedTexCoords = ByteBuffer.allocateDirect(QUAD_TEXCOORDS.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        transformedTexCoords.put(QUAD_TEXCOORDS).position(0);

        // Apply rotation to texture coordinates
        applyRotationToTexCoords();

        // Compile shaders and create program
        setupShaders();
        
        // Create camera texture
        createCameraTexture();
    }

    // Apply rotation to texture coordinates based on the rotation parameter
    private void applyRotationToTexCoords() {
        float[] rotatedCoords = new float[8];
        
        // Copy original coordinates
        System.arraycopy(QUAD_TEXCOORDS, 0, rotatedCoords, 0, 8);
        
        // Apply rotation transform based on rotation parameter
        // These rotations match standard Android device orientation changes,
        // taking into account that the base coordinates are already flipped vertically
        if (Math.abs(rotation - 90) < 0.1f) {
            // 90 degrees CLOCKWISE rotation (not counter-clockwise)
            // For clockwise rotation: (x,y) -> (y, 1-x)
            rotatedCoords[0] = QUAD_TEXCOORDS[6]; // bottom-left -> bottom-right
            rotatedCoords[1] = QUAD_TEXCOORDS[7]; 
            
            rotatedCoords[2] = QUAD_TEXCOORDS[0]; // top-left -> bottom-left
            rotatedCoords[3] = QUAD_TEXCOORDS[1];
            
            rotatedCoords[4] = QUAD_TEXCOORDS[4]; // bottom-right -> top-right
            rotatedCoords[5] = QUAD_TEXCOORDS[5];
            
            rotatedCoords[6] = QUAD_TEXCOORDS[2]; // top-right -> top-left
            rotatedCoords[7] = QUAD_TEXCOORDS[3];
        } else if (Math.abs(rotation - 180) < 0.1f) {
            // 180 degrees
            rotatedCoords[0] = QUAD_TEXCOORDS[6]; // bottom-left -> top-right
            rotatedCoords[1] = QUAD_TEXCOORDS[7]; 
            
            rotatedCoords[2] = QUAD_TEXCOORDS[4]; // top-left -> bottom-right
            rotatedCoords[3] = QUAD_TEXCOORDS[5];
            
            rotatedCoords[4] = QUAD_TEXCOORDS[2]; // bottom-right -> top-left
            rotatedCoords[5] = QUAD_TEXCOORDS[3];
            
            rotatedCoords[6] = QUAD_TEXCOORDS[0]; // top-right -> bottom-left
            rotatedCoords[7] = QUAD_TEXCOORDS[1];
        } else if (Math.abs(rotation - 270) < 0.1f) {
            // 270 degrees counter-clockwise (or 90 degrees clockwise)
            rotatedCoords[0] = QUAD_TEXCOORDS[4]; // bottom-left -> bottom-right
            rotatedCoords[1] = QUAD_TEXCOORDS[5]; 
            
            rotatedCoords[2] = QUAD_TEXCOORDS[0]; // top-left -> bottom-left
            rotatedCoords[3] = QUAD_TEXCOORDS[1];
            
            rotatedCoords[4] = QUAD_TEXCOORDS[6]; // bottom-right -> top-right
            rotatedCoords[5] = QUAD_TEXCOORDS[7];
            
            rotatedCoords[6] = QUAD_TEXCOORDS[2]; // top-right -> top-left
            rotatedCoords[7] = QUAD_TEXCOORDS[3];
        }
        
        // Update the quadTexCoords buffer with rotated coordinates
        quadTexCoords.clear();
        quadTexCoords.put(rotatedCoords);
        quadTexCoords.position(0);
        
        // Log the applied rotation
        StringBuilder sb = new StringBuilder("Applied rotation ");
        sb.append(rotation).append(" degrees. Texture coordinates: ");
        for (int i = 0; i < 8; i++) {
            sb.append(rotatedCoords[i]).append(", ");
        }
        Log.d(TAG, sb.toString());
    }

    /**
     * Set up the shader program and attributes.
     */
    private void setupShaders() {
        try {
            int vertexShader = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER);
            int fragmentShader = compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);

            program = GLES20.glCreateProgram();
            if (program == 0) {
                Log.e(TAG, "Failed to create program");
                return;
            }

            GLES20.glAttachShader(program, vertexShader);
            GLES20.glAttachShader(program, fragmentShader);
            GLES20.glLinkProgram(program);

            int[] linkStatus = new int[1];
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
            if (linkStatus[0] != GLES20.GL_TRUE) {
                Log.e(TAG, "Error linking program: " + GLES20.glGetProgramInfoLog(program));
                GLES20.glDeleteProgram(program);
                program = 0;
                return;
            }

            // Cleanup shaders as they're linked now
            GLES20.glDeleteShader(vertexShader);
            GLES20.glDeleteShader(fragmentShader);

            // Get attribute and uniform locations
            positionAttrib = GLES20.glGetAttribLocation(program, "aPosition");
            texCoordAttrib = GLES20.glGetAttribLocation(program, "aTexCoord");
            textureUniform = GLES20.glGetUniformLocation(program, "uTexture");
            
            Log.d(TAG, "Shader program created successfully: " + program);
        } catch (Exception e) {
            Log.e(TAG, "Error setting up shaders", e);
        }
    }

    /**
     * Create a camera texture.
     */
    private void createCameraTexture() {
        try {
            int[] textures = new int[1];
            GLES20.glGenTextures(1, textures, 0);
            textureId = textures[0];
            
            if (textureId <= 0) {
                Log.e(TAG, "Failed to generate texture ID");
                return;
            }
            
            Log.d(TAG, "Camera texture created with ID: " + textureId);
            
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
            
            // Set texture parameters
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            
            // Unbind the texture
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0);
            
            // Check for errors
            int error = GLES20.glGetError();
            if (error != GLES20.GL_NO_ERROR) {
                Log.e(TAG, "Error creating camera texture: " + error);
            }
        } catch (Exception e) {
            Log.e(TAG, "Error creating camera texture", e);
            textureId = -1;
        }
    }
    
    /**
     * Recreate texture if it has been lost or is invalid
     */
    public void ensureValidTexture() {
        if (textureId <= 0) {
            Log.d(TAG, "Recreating invalid camera texture");
            release();
            createCameraTexture();
        }
    }

    /**
     * Update the texture coordinates for the camera display.
     *
     * @param texCoords FloatBuffer containing transformed texture coordinates.
     */
    public void updateTextureCoordinates(FloatBuffer texCoords) {
        if (texCoords == null) {
            Log.e(TAG, "Texture coordinates buffer is null");
            return;
        }
        
        try {
            // Make sure buffers are properly positioned
            texCoords.position(0);
            transformedTexCoords.clear();  // Clear instead of just setting position to 0
            
            // Make sure we have valid transformed coordinates
            if (texCoords.capacity() < 8) {
                Log.e(TAG, "Texture coordinates buffer too small: " + texCoords.capacity());
                return;
            }
            
            // Check for NaN values and replace them if necessary
            float[] safeCoords = new float[8];
            boolean hadNaN = false;
            
            // Copy and check coordinates
            for (int i = 0; i < 8; i++) {
                float val = texCoords.get(i);
                if (Float.isNaN(val)) {
                    hadNaN = true;
                    
                    // Replace NaN with appropriate fallback values
                    // Use default texture coordinate mapping for the position
                    // Updated for 90-degree clockwise rotation
                    switch (i) {
                        case 0: safeCoords[i] = 1.0f; break; // bottom-left x (rotated 90° clockwise)
                        case 1: safeCoords[i] = 1.0f; break; // bottom-left y (rotated 90° clockwise)
                        case 2: safeCoords[i] = 0.0f; break; // top-left x (rotated 90° clockwise)
                        case 3: safeCoords[i] = 1.0f; break; // top-left y (rotated 90° clockwise)
                        case 4: safeCoords[i] = 1.0f; break; // bottom-right x (rotated 90° clockwise)
                        case 5: safeCoords[i] = 0.0f; break; // bottom-right y (rotated 90° clockwise)
                        case 6: safeCoords[i] = 0.0f; break; // top-right x (rotated 90° clockwise)
                        case 7: safeCoords[i] = 0.0f; break; // top-right y (rotated 90° clockwise)
                    }
                } else {
                    safeCoords[i] = val;
                }
            }
            
            if (hadNaN) {
                Log.w(TAG, "Fixed NaN values in texture coordinates");
                
                // Log the fixed coordinates
                StringBuilder sb = new StringBuilder("Fixed texture coordinates: ");
                for (int i = 0; i < 8; i++) {
                    sb.append(safeCoords[i]).append(", ");
                }
                Log.d(TAG, sb.toString());
                
                // Use the fixed coordinates
                transformedTexCoords.put(safeCoords);
            } else {
                // Log the incoming coordinates for debugging
                StringBuilder sb = new StringBuilder("Received texture coordinates: ");
                texCoords.position(0);
                for (int i = 0; i < 8; i++) {
                    sb.append(texCoords.get()).append(", ");
                }
                Log.d(TAG, sb.toString());
                
                // Reset position after reading
                texCoords.position(0);
                
                // Copy the transformed coordinates to our buffer
                transformedTexCoords.put(texCoords);
            }
            
            transformedTexCoords.position(0);
        } catch (Exception e) {
            Log.e(TAG, "Error updating texture coordinates", e);
            
            // Fallback to default texture coordinates if an error occurs
            transformedTexCoords.clear();
            transformedTexCoords.put(new float[] {
                1.0f, 1.0f,  // bottom-left rotated 90° clockwise
                0.0f, 1.0f,  // top-left rotated 90° clockwise
                1.0f, 0.0f,  // bottom-right rotated 90° clockwise
                0.0f, 0.0f   // top-right rotated 90° clockwise
            });
            transformedTexCoords.position(0);
        }
    }

    /**
     * Draw the camera texture to the screen.
     */
    public void draw() {
        try {
            // Ensure we have a valid texture
            ensureValidTexture();
            
            if (program == 0 || textureId <= 0) {
                Log.e(TAG, "Invalid shader program or texture ID for drawing");
                return;
            }
            
            // Clear any previous errors
            int previousError = GLES20.glGetError();
            if (previousError != GLES20.GL_NO_ERROR) {
                Log.d(TAG, "Cleared existing GL error before drawing: " + previousError);
            }
            
            // Use the shader program
            GLES20.glUseProgram(program);
            
            // Bind the texture
            GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
            GLES20.glUniform1i(textureUniform, 0);
            
            // Check for errors after binding texture
            int error = GLES20.glGetError();
            if (error != GLES20.GL_NO_ERROR) {
                Log.e(TAG, "Error after binding texture: " + error);
                return;
            }
            
            // Set up vertex attributes
            quadVertices.position(0);
            GLES20.glVertexAttribPointer(positionAttrib, 3, GLES20.GL_FLOAT, false, 0, quadVertices);
            GLES20.glEnableVertexAttribArray(positionAttrib);
            
            // Use transformed texture coordinates
            transformedTexCoords.position(0);
            GLES20.glVertexAttribPointer(texCoordAttrib, 2, GLES20.GL_FLOAT, false, 0, transformedTexCoords);
            GLES20.glEnableVertexAttribArray(texCoordAttrib);
            
            // Log the texture coordinates being used for debugging
            if (transformedTexCoords != null && transformedTexCoords.capacity() >= 8) {
                transformedTexCoords.position(0);
                float[] coords = new float[8];
                for (int i = 0; i < 8; i++) {
                    coords[i] = transformedTexCoords.get();
                }
                Log.d(TAG, String.format("Drawing with texture coords: (%.2f,%.2f), (%.2f,%.2f), (%.2f,%.2f), (%.2f,%.2f)",
                        coords[0], coords[1], coords[2], coords[3], coords[4], coords[5], coords[6], coords[7]));
                transformedTexCoords.position(0);
            }
            
            // Draw the quad
            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
            
            // Check for drawing errors
            error = GLES20.glGetError();
            if (error != GLES20.GL_NO_ERROR) {
                Log.e(TAG, "Error after drawing: " + error);
            }
            
            // Disable vertex arrays
            GLES20.glDisableVertexAttribArray(positionAttrib);
            GLES20.glDisableVertexAttribArray(texCoordAttrib);
        } catch (Exception e) {
            Log.e(TAG, "Error in draw method", e);
        }
    }

    /**
     * Compile a shader from source code.
     *
     * @param type Shader type (vertex or fragment).
     * @param source Shader source code.
     * @return Shader ID.
     */
    private int compileShader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        if (shader == 0) {
            Log.e(TAG, "Failed to create shader");
            return 0;
        }

        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);

        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            Log.e(TAG, "Could not compile shader " + type + ": " + GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            return 0;
        }

        return shader;
    }

    /**
     * Get the texture ID for the camera texture.
     *
     * @return The texture ID.
     */
    public int getTextureId() {
        // Check if texture is valid before returning
        if (textureId <= 0) {
            Log.d(TAG, "Invalid texture ID, attempting to recreate");
            createCameraTexture();
        }
        return textureId;
    }

    /**
     * Release resources used by the shader.
     */
    public void release() {
        try {
            if (program != 0) {
                GLES20.glDeleteProgram(program);
                program = 0;
            }
            
            if (textureId > 0) {
                int[] textures = {textureId};
                GLES20.glDeleteTextures(1, textures, 0);
                textureId = -1;
            }
            
            Log.d(TAG, "Camera texture shader resources released");
        } catch (Exception e) {
            Log.e(TAG, "Error releasing shader resources", e);
        }
    }
} 