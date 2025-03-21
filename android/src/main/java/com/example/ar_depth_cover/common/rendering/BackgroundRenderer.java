/*
 * Copyright 2017 Google LLC
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
package com.example.ar_depth_cover.common.rendering;

import android.content.Context;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.ar.core.Coordinates2d;
import com.google.ar.core.Frame;
import com.google.ar.core.Session;
import com.google.ar.core.exceptions.CameraNotAvailableException;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * This class renders the AR background from camera feed. It creates and hosts the texture given to
 * ARCore to be filled with the camera image.
 */
public class BackgroundRenderer {
  private static final String TAG = BackgroundRenderer.class.getSimpleName();

  // Shader names.
  private static final String VERTEX_SHADER_NAME = "shaders/screenquad.vert";
  private static final String FRAGMENT_SHADER_NAME = "shaders/screenquad.frag";

  private static final int COORDS_PER_VERTEX = 2;
  private static final int TEXCOORDS_PER_VERTEX = 2;
  private static final int FLOAT_SIZE = 4;

  private FloatBuffer quadVertices;
  private FloatBuffer quadTexCoord;
  private FloatBuffer quadTexCoordTransformed;

  private int quadProgram;

  private int quadPositionParam;
  private int quadTexCoordParam;
  private int textureId = -1;

  public BackgroundRenderer() {}

  public int getTextureId() {
    return textureId;
  }

  /**
   * Allocates and initializes OpenGL resources needed by the background renderer. Must be called on
   * the OpenGL thread, typically in {@link GLSurfaceView.Renderer#onSurfaceCreated(GL10,
   * EGLConfig)}.
   *
   * @param context Needed to access shader source.
   */
  public void createOnGlThread(Context context) throws IOException {
    // Generate the background texture.
    int[] textures = new int[1];
    GLES20.glGenTextures(1, textures, 0);
    textureId = textures[0];
    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
    GLES20.glTexParameteri(
        GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
    GLES20.glTexParameteri(
        GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
    GLES20.glTexParameteri(
        GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
    GLES20.glTexParameteri(
        GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);

    int numVertices = 4;
    if (numVertices != 4) {
      throw new RuntimeException("Unexpected number of vertices in BackgroundRenderer.");
    }

    ByteBuffer bbVertices = ByteBuffer.allocateDirect(COORDS_PER_VERTEX * numVertices * FLOAT_SIZE);
    bbVertices.order(ByteOrder.nativeOrder());
    quadVertices = bbVertices.asFloatBuffer();
    quadVertices.put(
        new float[] {
          -1.0f, -1.0f, -1.0f, 1.0f, 1.0f, -1.0f, 1.0f, 1.0f,
        });
    quadVertices.position(0);

    ByteBuffer bbTexCoords =
        ByteBuffer.allocateDirect(TEXCOORDS_PER_VERTEX * numVertices * FLOAT_SIZE);
    bbTexCoords.order(ByteOrder.nativeOrder());
    quadTexCoord = bbTexCoords.asFloatBuffer();
    quadTexCoord.put(
        new float[] {
          0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 0.0f,
        });
    quadTexCoord.position(0);

    ByteBuffer bbTexCoordsTransformed =
        ByteBuffer.allocateDirect(TEXCOORDS_PER_VERTEX * numVertices * FLOAT_SIZE);
    bbTexCoordsTransformed.order(ByteOrder.nativeOrder());
    quadTexCoordTransformed = bbTexCoordsTransformed.asFloatBuffer();

    // Load simple vertex and fragment shaders
    int vertexShader = ShaderUtil.loadShader(GLES20.GL_VERTEX_SHADER, getVertexShaderString());
    int fragmentShader = ShaderUtil.loadShader(GLES20.GL_FRAGMENT_SHADER, getFragmentShaderString());

    quadProgram = GLES20.glCreateProgram();
    GLES20.glAttachShader(quadProgram, vertexShader);
    GLES20.glAttachShader(quadProgram, fragmentShader);
    GLES20.glLinkProgram(quadProgram);
    GLES20.glUseProgram(quadProgram);

    ShaderUtil.checkGLError(TAG, "Program creation");

    quadPositionParam = GLES20.glGetAttribLocation(quadProgram, "a_Position");
    quadTexCoordParam = GLES20.glGetAttribLocation(quadProgram, "a_TexCoord");

    ShaderUtil.checkGLError(TAG, "Program parameters");
  }

  /**
   * Sets up the camera texture name and draws the background.
   */
  public void setupTexture(Session session) {
    if (textureId == -1) {
      return;
    }
    
    // Set up camera texture
    session.setCameraTextureName(textureId);
  }

  /**
   * Draws the camera background using the previously set texture.
   * This should be called after session.update() to get the latest camera image.
   */
  public void draw(Frame frame) {
    if (textureId == -1) {
      return;
    }
    
    // Update texture coordinates
    frame.transformDisplayUvCoords(quadTexCoord, quadTexCoordTransformed);

    // Draw camera background
    GLES20.glDisable(GLES20.GL_DEPTH_TEST);
    GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
    GLES20.glUseProgram(quadProgram);

    // Set the vertex positions
    GLES20.glVertexAttribPointer(
        quadPositionParam, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, quadVertices);
    
    // Set the texture coordinates
    GLES20.glVertexAttribPointer(
        quadTexCoordParam, TEXCOORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, quadTexCoordTransformed);

    // Enable vertex arrays
    GLES20.glEnableVertexAttribArray(quadPositionParam);
    GLES20.glEnableVertexAttribArray(quadTexCoordParam);

    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

    // Disable vertex arrays
    GLES20.glDisableVertexAttribArray(quadPositionParam);
    GLES20.glDisableVertexAttribArray(quadTexCoordParam);

    // Restore the depth test
    GLES20.glEnable(GLES20.GL_DEPTH_TEST);
    
    ShaderUtil.checkGLError(TAG, "Draw");
  }
  
  /**
   * Returns the vertex shader string for camera background rendering.
   */
  private String getVertexShaderString() {
    return "attribute vec4 a_Position;\n" +
           "attribute vec2 a_TexCoord;\n" +
           "varying vec2 v_TexCoord;\n" +
           "void main() {\n" +
           "   gl_Position = a_Position;\n" +
           "   v_TexCoord = a_TexCoord;\n" +
           "}";
  }
  
  /**
   * Returns the fragment shader string for camera background rendering.
   */
  private String getFragmentShaderString() {
    return "#extension GL_OES_EGL_image_external : require\n" +
           "precision mediump float;\n" +
           "uniform samplerExternalOES u_Texture;\n" +
           "varying vec2 v_TexCoord;\n" +
           "void main() {\n" +
           "    gl_FragColor = texture2D(u_Texture, v_TexCoord);\n" +
           "}";
  }
}
