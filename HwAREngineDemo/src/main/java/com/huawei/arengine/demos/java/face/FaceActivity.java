/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2020-2020. All rights reserved.
 */

package com.huawei.arengine.demos.java.face;

import android.app.Activity;
import android.graphics.SurfaceTexture;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.Surface;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import com.huawei.arengine.demos.R;
import com.huawei.arengine.demos.common.DisplayRotationUtil;
import com.huawei.arengine.demos.java.face.rendering.RenderUtil;
import com.huawei.hiar.ARConfigBase;
import com.huawei.hiar.AREnginesApk;
import com.huawei.hiar.AREnginesSelector;
import com.huawei.hiar.ARFaceTrackingConfig;
import com.huawei.hiar.ARSession;
import com.huawei.hiar.exceptions.ARCameraNotAvailableException;
import com.huawei.hiar.exceptions.ARUnSupportedConfigurationException;
import com.huawei.hiar.exceptions.ARUnavailableClientSdkTooOldException;
import com.huawei.hiar.exceptions.ARUnavailableDeviceNotCompatibleException;
import com.huawei.hiar.exceptions.ARUnavailableEmuiNotCompatibleException;
import com.huawei.hiar.exceptions.ARUnavailableServiceApkTooOldException;
import com.huawei.hiar.exceptions.ARUnavailableServiceNotInstalledException;
import com.huawei.hiar.exceptions.ARUnavailableUserDeclinedInstallationException;

import java.util.List;

/**
 * This Demo demonstrates Huawei AREngine's ability to recognize faces, including face contours
 * and facial expressions. In addition, this demo also demonstrates how to open the camera in the
 * application test. Currently, the arengine only supports face type to open the camera externally
 * without configuring health. If you want to use the function of external camera opening, please
 * set isOpenCameraFlag = true, isHealthScene = false.
 *
 * @author HW
 * @since 2020-03-18
 */
public class FaceActivity extends Activity {
    private static final String TAG = FaceActivity.class.getSimpleName();

    private ARSession mArSession;

    private GLSurfaceView glSurfaceView;

    private RenderUtil mRenderUtil;

    private DisplayRotationUtil mDisplayRotationUtil;

    // External open camera only supports face when health is not configured
    private boolean isOpenCameraOutside = false;

    private boolean isRemindInstall = true;

    private CameraHelper mCamera;

    private Surface mPreViewSurface;

    private Surface mVgaSurface;

    private Surface mMetaDataSurface;

    private Surface mDepthSurface;

    private ARConfigBase mArConfig;

    private TextView mTextView;

    private String message = null;

    // Initialization textureID
    private int textureId = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.face_activity_main);
        mTextView = findViewById(R.id.faceTextView);
        glSurfaceView = findViewById(R.id.faceSurfaceview);

        mDisplayRotationUtil = new DisplayRotationUtil(this);

        glSurfaceView.setPreserveEGLContextOnPause(true);

        // Set the OpenGLES version.
        glSurfaceView.setEGLContextClientVersion(2);

        // Set EGL config chooser, including byte size and short size.
        glSurfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);

        mRenderUtil = new RenderUtil(this,this);
        mRenderUtil.setDisplayRotationUtil(mDisplayRotationUtil);
        mRenderUtil.setTextView(mTextView);

        glSurfaceView.setRenderer(mRenderUtil);
        glSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        // Judge whether the current device supports Huawei arengine. If not, end the application.
        if (!isSupportHuaweiArEngine()) {
            String showMessage = "This device does not support Huawei AREngine!";
            Toast.makeText(this, showMessage, Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    protected void onResume() {
        Log.d(TAG, "onResume");
        super.onResume();
        mDisplayRotationUtil.registerDisplayListener();
        Exception exception = null;
        message = null;
        if (mArSession == null) {
            try {
                // Request to install arengine server. If it is already installed or
                // the user chooses to install it, it will work normally. Otherwise, set isRemindInstall to false.
                requestedInstall();

                // If the user rejects the installation, isRemindInstall is false.
                if (!isRemindInstall) {
                    return;
                }
                mArSession = new ARSession(this);

                // Configure an AR type through arconfig, and set different capabilities through the "set" interface.
                mArConfig = new ARFaceTrackingConfig(mArSession);

                mArConfig.setPowerMode(ARConfigBase.PowerMode.POWER_SAVING);

                // Currently, only face can turn on the camera externally without configuring health.
                if (isOpenCameraOutside) {
                    mArConfig.setImageInputMode(ARConfigBase.ImageInputMode.EXTERNAL_INPUT_ALL);
                }
                mArSession.configure(mArConfig);
            } catch (Exception capturedException) {
                exception = capturedException;
                setMessageWhenError(capturedException);
            }
            if (message != null) {
                stopArSession(exception);
                return;
            }
        }
        try {
            mArSession.resume();
        } catch (ARCameraNotAvailableException e) {
            Toast.makeText(this, "Camera open failed, please restart the app", Toast.LENGTH_LONG).show();
            mArSession = null;
            return;
        }
        mDisplayRotationUtil.registerDisplayListener();
        setCamera();
        mRenderUtil.setArSession(mArSession);
        mRenderUtil.setOpenCameraOutsideFlag(isOpenCameraOutside);
        mRenderUtil.setTextureId(textureId);
        glSurfaceView.onResume();
    }

    /**
     * Judge whether the current application supports Huawei arengine on the modified equipment.
     *
     * @return Return inspection results.
     */
    private boolean isSupportHuaweiArEngine() {
        AREnginesSelector.AREnginesAvaliblity enginesAvaliblity =
            AREnginesSelector.checkAllAvailableEngines(this);
        boolean isSupportHuaweiArEngine = (enginesAvaliblity.getKeyValues()
            & AREnginesSelector.AREnginesAvaliblity.HWAR_ENGINE_SUPPORTED.getKeyValues()) != 0;
        return isSupportHuaweiArEngine;
    }

    /**
     * At the time of onResume, request to install the AREngine service.
     * If the current device has been installed or the user agrees to
     * install it, it will work normally. If the device is not installed
     * and the user rejects the installation, setting isRemindInstall to
     * false means that the installation will not be prompted.
     */
    private void requestedInstall() {
        AREnginesApk.ARInstallStatus installStatus = AREnginesApk.requestInstall(this, isRemindInstall);
        switch(installStatus) {
            case INSTALL_REQUESTED:
                isRemindInstall = false;
                break;
            case INSTALLED:
                break;
        }
    }

    /**
     * When an exception occurs, set different prompt information according to the different exceptions caught.
     *
     * @param catchException exception caught by the process of creating arse.
     */
    private void setMessageWhenError(Exception catchException) {
        if (catchException instanceof ARUnavailableServiceNotInstalledException) {
            message = "Please install HuaweiARService.apk";
        } else if (catchException instanceof ARUnavailableServiceApkTooOldException) {
            message = "Please update HuaweiARService.apk";
        } else if (catchException instanceof ARUnavailableClientSdkTooOldException) {
            message = "Please update this app";
        } else if (catchException instanceof ARUnavailableDeviceNotCompatibleException) {
            message = "This device does not support Huawei AR Engine ";
        } else if (catchException instanceof ARUnavailableEmuiNotCompatibleException) {
            message = "Please update EMUI version";
        } else if (catchException instanceof ARUnavailableUserDeclinedInstallationException) {
            message = "Please agree to install!";
        } else if (catchException instanceof ARUnSupportedConfigurationException) {
            message = "The configuration is not supported by the device!";
        } else {
            message = "exception Unknown";
        }
    }

    /**
     * When some exceptions occur, the application needs to stop the session.
     *
     * @param exception Exception occurred
     */
    private void stopArSession(Exception exception) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
        Log.e(TAG, "Creating session error ", exception);
        if (mArSession != null) {
            mArSession.stop();
            mArSession = null;
        }
    }

    private void setCamera() {
        if (isOpenCameraOutside && mCamera == null) {
            Log.i(TAG, "new Camera");
            DisplayMetrics dm = new DisplayMetrics();
            mCamera = new CameraHelper(this);
            mCamera.setupCamera(dm.widthPixels, dm.heightPixels);
        }

        // TextureId initial value is -1,"if" can identify if it's the first time.
        if (isOpenCameraOutside) {
            if (textureId != -1) {
                mArSession.setCameraTextureName(textureId);
                initSurface();
            } else {
                int[] textureIds = new int[1];
                GLES20.glGenTextures(1, textureIds, 0);
                textureId = textureIds[0];
                mArSession.setCameraTextureName(textureId);
                initSurface();
            }

            SurfaceTexture surfaceTexture = new SurfaceTexture(textureId);
            mCamera.setPreviewTexture(surfaceTexture);
            mCamera.setPreViewSurface(mPreViewSurface);
            mCamera.setVgaSurface(mVgaSurface);
            mCamera.setDepthSurface(mDepthSurface);
            if (!mCamera.openCamera()) {
                String showMessage = "Open camera filed!";
                Log.e(TAG, showMessage);
                Toast.makeText(this, showMessage, Toast.LENGTH_LONG).show();
                finish();
            }
        }
    }

    private void initSurface() {
        List<ARConfigBase.SurfaceType> surfaceTypeList = mArConfig.getImageInputSurfaceTypes();
        List<Surface> surfaceList = mArConfig.getImageInputSurfaces();

        Log.i(TAG, "surfaceList size : " + surfaceList.size());
        int size = surfaceTypeList.size();
        for (int i = 0; i < size; i++) {
            ARConfigBase.SurfaceType type = surfaceTypeList.get(i);
            Surface surface = surfaceList.get(i);
            if (ARConfigBase.SurfaceType.PREVIEW.equals(type)) {
                mPreViewSurface = surface;
            } else if (ARConfigBase.SurfaceType.VGA.equals(type)) {
                mVgaSurface = surface;
            } else if (ARConfigBase.SurfaceType.METADATA.equals(type)) {
                mMetaDataSurface = surface;
            } else if (ARConfigBase.SurfaceType.DEPTH.equals(type)) {
                mDepthSurface = surface;
            }
            Log.i(TAG, "list[" + i + "] get surface : " + surface + ", type : " + type);
        }
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
        if (isOpenCameraOutside) {
            if (mCamera != null) {
                mCamera.closeCamera();
                mCamera.stopCameraThread();
                mCamera = null;
            }
        }

        if (mArSession != null) {
            mDisplayRotationUtil.unregisterDisplayListener();
            glSurfaceView.onPause();
            mArSession.pause();
            Log.i(TAG, "[faceDemo]Session paused!");
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mArSession != null) {
            Log.i(TAG, "[faceDemo]Session onDestroy!");
            mArSession.stop();
            mArSession = null;
            Log.i(TAG, "[faceDemo]Session stop!");
        }
    }

    @Override
    public void onWindowFocusChanged(boolean isHasFocus) {
        Log.d(TAG, "onWindowFocusChanged");
        super.onWindowFocusChanged(isHasFocus);
        if (isHasFocus) {
            // Standard Android full-screen functionality.
            getWindow().getDecorView().
                setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }
}