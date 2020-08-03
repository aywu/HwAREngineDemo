/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2020-2020. All rights reserved.
 */

package com.huawei.arengine.demos.java.face.rendering;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;
import android.widget.TextView;

import com.huawei.arengine.demos.common.DisplayRotationUtil;
import com.huawei.arengine.demos.common.TextDisplayUtil;
import com.huawei.arengine.demos.common.TextureRenderUtil;
import com.huawei.hiar.ARCamera;
import com.huawei.hiar.ARFace;
import com.huawei.hiar.ARFrame;
import com.huawei.hiar.ARPose;
import com.huawei.hiar.ARSession;
import com.huawei.hiar.ARTrackable.TrackingState;

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

    private static final float UPDATE_INTERVAL = 0.5f;

    private int frames = 0;

    private long lastInterval;

    private ARSession mArSession;

    private float fps;

    private Context mContext;

    private Activity mActivity;

    private TextView mTextView;

    private boolean isOpenCameraOutside = true;

    private int mTextureId = -1; // Initialize texture ID.

    private TextureRenderUtil mTextureRenderUtil = new TextureRenderUtil();

    private FaceGeometryDisplay mFaceGeometryDisplay = new FaceGeometryDisplay();

    private TextDisplayUtil mTextDisplayUtil = new TextDisplayUtil();

    private DisplayRotationUtil mDisplayRotationUtil;

    /**
     * Constructor, passing in context and activity.
     * This method will be called by {@link Activity#onCreate}.
     *
     * @param context Context
     * @param activity Activity
     */
    public RenderUtil(Context context, Activity activity) {
        mContext = context;
        mActivity = activity;
    }

    /**
     * Set AR session for updating in onDrawFrame to get the latest data.
     * This method will be called by {@link Activity#onResume}.
     *
     * @param arSession ARSession.
     */
    public void setArSession(ARSession arSession) {
        if (arSession == null) {
            Log.e(TAG, "setSession error, arSession is null!");
            return;
        }
        mArSession = arSession;
    }

    /**
     * Set the Open camera outside flag, If it is true, it means that the app
     * opens the camera by itself, and the textureid will be generated in the
     * background rendering. If it is false, it means that the camera is opened
     * externally, and the texture needs to be passed into the background rendering.
     * This method will be called by {@link Activity#onResume}.
     *
     * @param isOpenCameraOutsideFlag This flag indicates whether the camera is turned on externally or internally.
     */
    public void setOpenCameraOutsideFlag(boolean isOpenCameraOutsideFlag) {
        isOpenCameraOutside = isOpenCameraOutsideFlag;
    }

    /**
     * Set texture for render the background.
     * This method will be called by {@link Activity#onResume}.
     *
     * @param textureId Texture id.
     */
    public void setTextureId(int textureId) {
        mTextureId = textureId;
    }

    /**
     * Set displayRotationUtil, this object will be used in onSurfaceChanged and onDrawFrame.
     * This method will be called when {@link Activity#onResume}.
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
     * This method will be called when {@link Activity#onCreate}.
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

        if (isOpenCameraOutside) {
            mTextureRenderUtil.init(mTextureId);
        } else {
            mTextureRenderUtil.init();
        }
        Log.i(TAG, "[faceDemo]onSurfaceCreated textureId=" + mTextureId);

        mFaceGeometryDisplay.init(mContext);

        mTextDisplayUtil.setListener(new TextDisplayUtil.OnTextInfoChangeListener() {
            @Override
            public boolean textInfoChanged(String text, float positionX, float positionY) {
                showTextViewOnUiThread(text, positionX, positionY);
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
    private void showTextViewOnUiThread(final String text, final float positionX, final float positionY) {
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

        if (mArSession == null) {
            return;
        }
        if (mDisplayRotationUtil.getDeviceRotation()) {
            mDisplayRotationUtil.updateArSessionDisplayGeometry(mArSession);
        }

        try {
            mArSession.setCameraTextureName(mTextureRenderUtil.getExternalTextureId());
            ARFrame frame = mArSession.update();
            mTextureRenderUtil.onDrawFrame(frame);
            ARCamera camera = frame.getCamera();
            float fpsResult = doFpsCalculate();
            Collection<ARFace> faces = mArSession.getAllTrackables(ARFace.class);
            if (faces.size() == 0) {
                mTextDisplayUtil.onDrawFrame(null);
                return;
            }
            Log.d(TAG, "face number: " + faces.size());
            for (ARFace face : faces) {
                if (face.getTrackingState() == TrackingState.TRACKING) {
                    StringBuilder sb = new StringBuilder();
                    updateMessageData(sb, fpsResult, face);
                    mTextDisplayUtil.onDrawFrame(sb);
                    mFaceGeometryDisplay.onDrawFrame(camera, face);
                }
            }
        } catch (Throwable t) {
            // Avoid crashing the application due to unhandled exceptions.
            Log.e(TAG, "Exception on the OpenGL thread", t);
        }
    }

    /**
     * Update gesture related data for display.
     *
     * @param sb string buffer.
     * @param fpsResult FPS information, the calculated interval is 0.5 seconds.
     * @param face ARFace
     */
    private void updateMessageData(StringBuilder sb, float fpsResult, ARFace face) {
        sb.append("FPS=" + fpsResult + System.lineSeparator());
        ARPose pose = face.getPose();
        if (pose != null) {
            sb.append("face pose information:");
            sb.append("face pose tx:[" + pose.tx() + "]" + System.lineSeparator());
            sb.append("face pose ty:[" + pose.ty() + "]" + System.lineSeparator());
            sb.append("face pose tz:[" + pose.tz() + "]" + System.lineSeparator());
            sb.append("face pose qx:[" + pose.qx() + "]" + System.lineSeparator());
            sb.append("face pose qy:[" + pose.qy() + "]" + System.lineSeparator());
            sb.append("face pose qz:[" + pose.qz() + "]" + System.lineSeparator());
            sb.append("face pose qw:[" + pose.qw() + "]" + System.lineSeparator());
        }
        sb.append(System.lineSeparator());

        float[] textureCoordinates = face.getFaceGeometry().getTextureCoordinates().array();
        sb.append("textureCoordinates length:[ " + textureCoordinates.length + " ]");
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