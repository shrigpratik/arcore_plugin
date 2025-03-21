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

import android.content.res.AssetManager;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/** A SampleRender context. */
public class SampleRender {
  private static final String TAG = SampleRender.class.getSimpleName();

  private final AssetManager assetManager;

  private int viewportWidth = 1;
  private int viewportHeight = 1;

  /**
   * Constructs a SampleRender object and instantiates GLSurfaceView parameters.
   *
   * @param glSurfaceView Android GLSurfaceView
   * @param renderer Renderer implementation to receive callbacks
   * @param assetManager AssetManager for loading Android resources
   */
  public SampleRender(GLSurfaceView glSurfaceView, Renderer renderer, AssetManager assetManager) {
    this.assetManager = assetManager;
    glSurfaceView.setPreserveEGLContextOnPause(true);
    glSurfaceView.setEGLContextClientVersion(2);
    glSurfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);
    glSurfaceView.setRenderer(
        new GLSurfaceView.Renderer() {
          @Override
          public void onSurfaceCreated(GL10 gl, EGLConfig config) {
            GLES20.glEnable(GLES20.GL_BLEND);
            maybeThrowGLException("Failed to enable blending", "glEnable");
            renderer.onSurfaceCreated(SampleRender.this);
          }

          @Override
          public void onSurfaceChanged(GL10 gl, int w, int h) {
            viewportWidth = w;
            viewportHeight = h;
            renderer.onSurfaceChanged(SampleRender.this, w, h);
          }

          @Override
          public void onDrawFrame(GL10 gl) {
            clear(0f, 0f, 0f, 1f);
            renderer.onDrawFrame(SampleRender.this);
          }
        });
    glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);
    glSurfaceView.setWillNotDraw(false);
  }

  /** Clear the default framebuffer */
  public void clear(float r, float g, float b, float a) {
    GLES20.glClearColor(r, g, b, a);
    maybeThrowGLException("Failed to set clear color", "glClearColor");
    GLES20.glDepthMask(true);
    maybeThrowGLException("Failed to set depth write mask", "glDepthMask");
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);
    maybeThrowGLException("Failed to clear framebuffer", "glClear");
  }
  
  /** Set viewport dimensions */
  public void setViewport(int width, int height) {
    viewportWidth = width;
    viewportHeight = height;
    GLES20.glViewport(0, 0, width, height);
  }

  /** Interface to be implemented for rendering callbacks. */
  public interface Renderer {
    /**
     * Called when the GL render surface is created.
     */
    void onSurfaceCreated(SampleRender render);

    /**
     * Called when the GL render surface dimensions are changed.
     */
    void onSurfaceChanged(SampleRender render, int width, int height);

    /**
     * Called when a GL frame is to be rendered.
     */
    void onDrawFrame(SampleRender render);
  }

  /* package-private */
  public AssetManager getAssets() {
    return assetManager;
  }
  
  /** Checks GL error and throws exception if one occurred. */
  public static void maybeThrowGLException(String message, String function) {
    int error = GLES20.glGetError();
    if (error != GLES20.GL_NO_ERROR) {
      String errorString = getErrorString(error);
      String exceptionMessage = message + ": " + function + ": " + errorString;
      Log.e(TAG, exceptionMessage);
      throw new RuntimeException(exceptionMessage);
    }
  }

  /** Converts a GL error to a string. */
  private static String getErrorString(int error) {
    switch (error) {
      case GLES20.GL_INVALID_ENUM:
        return "GL_INVALID_ENUM";
      case GLES20.GL_INVALID_VALUE:
        return "GL_INVALID_VALUE";
      case GLES20.GL_INVALID_OPERATION:
        return "GL_INVALID_OPERATION";
      case GLES20.GL_INVALID_FRAMEBUFFER_OPERATION:
        return "GL_INVALID_FRAMEBUFFER_OPERATION";
      case GLES20.GL_OUT_OF_MEMORY:
        return "GL_OUT_OF_MEMORY";
      default:
        return "Unknown error 0x" + Integer.toHexString(error);
    }
  }
} 