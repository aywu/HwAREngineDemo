/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2020-2020. All rights reserved.
 */

package com.huawei.arengine.demos.java.body3d.rendering;

import android.app.Activity;
import android.graphics.Color;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;
import android.widget.TextView;

import com.huawei.arengine.demos.common.DisplayRotationUtil;
import com.huawei.arengine.demos.common.TextDisplayUtil;
import com.huawei.arengine.demos.common.TextureRenderUtil;
import com.huawei.hiar.ARBody;
import com.huawei.hiar.ARCamera;
import com.huawei.hiar.ARFrame;
import com.huawei.hiar.ARSession;
import com.huawei.hiar.ARTrackable;

import java.util.Collection;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * This class shows how to render the data obtained through AREngine.
 *
 * @author HW
 * @since 2020-03-21
 */
public class RenderUtil implements GLSurfaceView.Renderer {
    private static final String TAG = RenderUtil.class.getSimpleName();

    private static final int PROJ_MATRIX_OFFSET = 0;

    private static final float PROJ_MATRIX_NEAR = 0.1f;

    private static final float PROJ_MATRIX_FAR = 100.0f;

    private static final float UPDATE_INTERVAL = 0.5f;

    private int frames = 0;

    private long lastInterval;

    private ARSession mSession;

    private float fps;

    private Activity mActivity;

    private TextView mTextView;

    private TextureRenderUtil mTextureRenderUtil = new TextureRenderUtil();

    private TextDisplayUtil mTextDisplayUtil = new TextDisplayUtil();

    private BodySkeletonDisplay mBodySkeletonDisplay = new BodySkeletonDisplay();

    private BodySkeletonLineDisplay mBodySkeletonLineDisplay = new BodySkeletonLineDisplay();

    private DisplayRotationUtil mDisplayRotationUtil;

    /**
     * Constructor, passing in context and activity.
     * This method will be called by {@link Activity#onCreate}.
     *
     * @param activity Activity
     */
    public RenderUtil(Activity activity) {
        mActivity = activity;
    }

    /**
     * Set AR session for updating in onDrawFrame to get the latest data.
     *
     * @param arSession ARSession.
     */
    public void setArSession(ARSession arSession) {
        if (arSession == null) {
            Log.e(TAG, "setSession error, arSession is null!");
            return;
        }
        mSession = arSession;
    }

    /**
     * Set displayRotationUtil, this object will be used in onSurfaceChanged and onDrawFrame.
     *
     * @param displayRotationUtil DisplayRotationUtil.
     */
    public void setDisplayRotationUtil(DisplayRotationUtil displayRotationUtil) {
        if (displayRotationUtil == null) {
            Log.e(TAG, "setDisplayRotationUtil error, displayRotationUtil is null!");
            return;
        }
        mDisplayRotationUtil = displayRotationUtil;
    }

    /**
     * Set TextView, this object will be invoked in the UI thread to display data correctly.
     *
     * @param textView TextView.
     */
    public void setTextView(TextView textView) {
        if (textView == null) {
            Log.e(TAG, "setTextView error, textView is null!");
            return;
        }
        mTextView = textView;
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        // Clear color, set window color.
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);
        mBodySkeletonDisplay.init();
        mBodySkeletonLineDisplay.init();
        mTextureRenderUtil.init();
        mTextDisplayUtil.setListener(new TextDisplayUtil.OnTextInfoChangeListener() {
            @Override
            public boolean textInfoChanged(String text, float positionX, float positionY) {
                showBodyTypeTextView(text, positionX, positionY);
                return true;
            }
        });
    }

    /**
     * Create a thread for UI display text, which will be used by gesture gesture rendering callback.
     *
     * @param text Gesture information for display on screen
     * @param positionX The left padding in pixels.
     * @param positionY The left padding in pixels
     */
    private void showBodyTypeTextView(final String text, final float positionX, final float positionY) {
        mActivity.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mTextView.setTextColor(Color.WHITE);

                // Set the size of the font used for display.
                mTextView.setTextSize(10f);
                if (text != null) {
                    mTextView.setText(text);
                    mTextView.setPadding((int) positionX, (int) positionY, 0, 0);
                } else {
                    mTextView.setText("");
                }
            }
        });
    }

    @Override
    public void onSurfaceChanged(GL10 unused, int width, int height) {
        mTextureRenderUtil.onSurfaceChanged(width, height);
        GLES20.glViewport(0, 0, width, height);
        mDisplayRotationUtil.updateViewportRotation(width, height);
    }

    @Override
    public void onDrawFrame(GL10 unused) {
        // Clear screen to notify driver it should not load any pixels from previous frame.
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        if (mSession == null) {
            return;
        }
        if (mDisplayRotationUtil.getDeviceRotation()) {
            mDisplayRotationUtil.updateArSessionDisplayGeometry(mSession);
        }

        try {
            mSession.setCameraTextureName(mTextureRenderUtil.getExternalTextureId());
            ARFrame frame = mSession.update();
            ARCamera camera = frame.getCamera();
            mTextureRenderUtil.onDrawFrame(frame);

            // The size of projection matrix is 4 * 4.
            float[] projectionMatrix = new float[16];

            // Obtain the projection matrix of AR camera.
            camera.getProjectionMatrix(projectionMatrix, PROJ_MATRIX_OFFSET, PROJ_MATRIX_NEAR, PROJ_MATRIX_FAR);

            Collection<ARBody> bodies = mSession.getAllTrackables(ARBody.class);
            if (bodies.size() == 0) {
                mTextDisplayUtil.onDrawFrame(null);
                return;
            }
            for (ARBody body : bodies) {
                if (body.getTrackingState() != ARTrackable.TrackingState.TRACKING) {
                    continue;
                }

                // Update information about the hand used for screen show.
                StringBuilder sb = new StringBuilder();
                updateMessageData(sb, body);

                // Show the updated hand related information on the screen.
                mTextDisplayUtil.onDrawFrame(sb);
            }
            mBodySkeletonDisplay.onDrawFrame(bodies, projectionMatrix);
            mBodySkeletonLineDisplay.onDrawFrame(bodies, projectionMatrix);
        } catch (Throwable t) {
            // Avoid crashing the application due to unhandled exceptions.
            Log.e(TAG, "Exception on the OpenGL thread", t);
        }
    }

    /**
     * Update gesture related data for display.
     *
     * @param sb string buffer.
     * @param body ARBody
     */
    private void updateMessageData(StringBuilder sb, ARBody body) {
        float fpsResult = doFpsCalculate();
        sb.append("FPS=" + fpsResult + System.lineSeparator());
        int bodyAction = body.getBodyAction();
        sb.append("bodyAction=" + bodyAction + System.lineSeparator());
    }

    private float doFpsCalculate() {
        ++frames;
        long timeNow = System.currentTimeMillis();

        // Convert milliseconds to seconds.
        if (((timeNow - lastInterval) / 1000.0f) > UPDATE_INTERVAL) {
            fps = frames / ((timeNow - lastInterval) / 1000.0f);
            frames = 0;
            lastInterval = timeNow;
        }
        return fps;
    }
}