/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2020-2020. All rights reserved.
 */

package com.huawei.arengine.demos.java.body3d;

import android.app.Activity;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import com.huawei.arengine.demos.R;
import com.huawei.arengine.demos.common.DisplayRotationUtil;
import com.huawei.arengine.demos.java.body3d.rendering.RenderUtil;
import com.huawei.hiar.ARBodyTrackingConfig;
import com.huawei.hiar.ARConfigBase;
import com.huawei.hiar.AREnginesApk;
import com.huawei.hiar.AREnginesSelector;
import com.huawei.hiar.ARSession;
import com.huawei.hiar.exceptions.ARCameraNotAvailableException;
import com.huawei.hiar.exceptions.ARUnSupportedConfigurationException;
import com.huawei.hiar.exceptions.ARUnavailableClientSdkTooOldException;
import com.huawei.hiar.exceptions.ARUnavailableDeviceNotCompatibleException;
import com.huawei.hiar.exceptions.ARUnavailableEmuiNotCompatibleException;
import com.huawei.hiar.exceptions.ARUnavailableServiceApkTooOldException;
import com.huawei.hiar.exceptions.ARUnavailableServiceNotInstalledException;
import com.huawei.hiar.exceptions.ARUnavailableUserDeclinedInstallationException;

/**
 * This Demo demonstrates Huawei AREngine's ability to recognize body joints and bones,
 * and outputs advanced human features such as limb endpoints, body posture, and human skeleton.
 *
 * @author HW
 * @since 2020-04-01
 */
public class BodyActivity extends Activity {
    private static final String TAG = BodyActivity.class.getSimpleName();

    private ARSession mArSession;

    private GLSurfaceView mSurfaceView;

    private RenderUtil mRenderUtil;

    private DisplayRotationUtil mDisplayRotationUtil;

    // Tap handling and UI.
    private TextView mTextView;

    private boolean isRemindInstall = true;

    private String message = null;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.body3d_activity_main);
        mTextView = findViewById(R.id.bodyTextView);
        mSurfaceView = findViewById(R.id.bodySurfaceview);
        mDisplayRotationUtil = new DisplayRotationUtil(this);

        // Set up renderer.
        mSurfaceView.setPreserveEGLContextOnPause(true);

        // Set the OpenGLES version.
        mSurfaceView.setEGLContextClientVersion(2);

        // Set EGL config chooser, including byte size and short size.
        mSurfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0);

        mRenderUtil = new RenderUtil(this);
        mRenderUtil.setDisplayRotationUtil(mDisplayRotationUtil);
        mRenderUtil.setTextView(mTextView);

        mSurfaceView.setRenderer(mRenderUtil);
        mSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

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
        Exception exception = null;
        message = null;
        if (mArSession == null) {
            try {
                // Request to install AREngine server. If it is already installed or
                // the user chooses to install it, it will work normally. Otherwise, set isRemindInstall to false.
                requestedInstall();

                // If the user rejects the installation, isRemindInstall is false.
                if (!isRemindInstall) {
                    return;
                }

                mArSession = new ARSession(this);

                ARBodyTrackingConfig config = new ARBodyTrackingConfig(mArSession);
                config.setEnableItem(ARConfigBase.ENABLE_DEPTH | ARConfigBase.ENABLE_MASK);
                mArSession.configure(config);
                mRenderUtil.setArSession(mArSession);
            } catch (Exception capturedException) {
                exception = capturedException;
                setMessageWhenError(capturedException);
            }
            if (message != null) {
                Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                Log.e(TAG, "Creating session", exception);
                if (mArSession != null) {
                    mArSession.stop();
                    mArSession = null;
                }
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
        mSurfaceView.onResume();
        mDisplayRotationUtil.registerDisplayListener();
    }

    /**
     * Judge whether the current application supports Huawei arengine on the modified equipment.
     *
     * @return Return inspection results.
     */
    private boolean isSupportHuaweiArEngine() {
        AREnginesSelector.AREnginesAvaliblity enginesAvaliblity =
            AREnginesSelector.checkAllAvailableEngines(this);
        return (enginesAvaliblity.getKeyValues()
            & AREnginesSelector.AREnginesAvaliblity.HWAR_ENGINE_SUPPORTED.getKeyValues()) != 0;
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
            message = "exception throwed";
        }
    }

    @Override
    protected void onPause() {
        Log.d(TAG, "onPause");
        super.onPause();
        if (mArSession != null) {
            mDisplayRotationUtil.unregisterDisplayListener();
            mSurfaceView.onPause();
            mArSession.pause();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mArSession != null) {
            mArSession.stop();
            mArSession = null;
        }
    }

    @Override
    public void onWindowFocusChanged(boolean isHasFocus) {
        Log.d(TAG, "onWindowFocusChanged");
        super.onWindowFocusChanged(isHasFocus);
        if (isHasFocus) {
            // Standard Android full-screen functionality.
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }
}