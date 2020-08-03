/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2020-2020. All rights reserved.
 */

package com.huawei.arengine.demos.java.face;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.view.Surface;

import com.huawei.arengine.demos.common.ArDemoRuntimeException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Provide some camera services.
 *
 * @author HW
 * @since 2020-03-15
 */
public class CameraHelper {
    private static final String TAG = CameraHelper.class.getSimpleName();

    private Activity mActivity;

    private CameraDevice mCameraDevice;

    private String mCameraId;

    private Size mPreviewSize;

    private HandlerThread mCameraThread;

    private Handler mCameraHandler;

    private SurfaceTexture mSurfaceTexture;

    private CaptureRequest.Builder mCaptureRequestBuilder;

    private CameraCaptureSession mCameraCaptureSession;

    private Semaphore mCameraOpenCloseLock = new Semaphore(1);

    private Surface mPreViewSurface;

    private Surface mVgaSurface;

    private Surface mDepthSurface;

    private CameraDevice.StateCallback mStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            mCameraDevice = camera;
            Log.i(TAG, "[faceDemo]CameraDevice onOpened!");
            startPreview();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice camera) {
            mCameraOpenCloseLock.release();
            camera.close();
            Log.i(TAG, "[faceDemo]CameraDevice onDisconnected!");
            mCameraDevice = null;
        }

        @Override
        public void onError(@NonNull CameraDevice camera, int error) {
            mCameraOpenCloseLock.release();
            camera.close();
            Log.i(TAG, "faceDemo CameraDevice onError!");
            mCameraDevice = null;
        }
    };

    /**
     * Constructor.
     *
     * @param activity Activity.
     */
    CameraHelper(Activity activity) {
        mActivity = activity;
        startCameraThread();
    }

    /**
     * Get preview size and camera Id.
     *
     * @param width Width for comparison.
     * @param height Height for comparison.
     */
    void setupCamera(int width, int height) {
        CameraManager cameraManager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
        try {
            for (String id : cameraManager.getCameraIdList()) {
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
                Integer cameraLensFacing = characteristics.get(CameraCharacteristics.LENS_FACING);
                if (cameraLensFacing == null) {
                    continue;
                }
                if (cameraLensFacing != CameraCharacteristics.LENS_FACING_FRONT) {
                    continue;
                }
                StreamConfigurationMap maps =
                    characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                if (maps == null || maps.getOutputSizes(SurfaceTexture.class) == null) {
                    continue;
                }
                mPreviewSize = getOptimalSize(maps.getOutputSizes(SurfaceTexture.class), width, height);
                mCameraId = id;
                Log.i(TAG, "preview width = " + mPreviewSize.getWidth() + ", height = "
                    + mPreviewSize.getHeight() + ", cameraId = " + mCameraId);
                break;
            }
        } catch (CameraAccessException e) {
            Log.e(TAG, "setupCamera error");
        }
    }

    /**
     * Start camera thread and define camera handler.
     */
    private void startCameraThread() {
        mCameraThread = new HandlerThread("CameraThread");
        mCameraThread.start();
        mCameraHandler = new Handler(mCameraThread.getLooper());
    }

    /**
     * Stop camera thread.
     * This method will be called by {@link FaceActivity#onPause}.
     */
    void stopCameraThread() {
        mCameraThread.quitSafely();
        try {
            mCameraThread.join();
            mCameraThread = null;
            mCameraHandler = null;
        } catch (InterruptedException e) {
            Log.e(TAG, "stopCameraThread error");
        }
    }

    /**
     * Open camera.
     *
     * @return Open success or failure.
     */
    boolean openCamera() {
        Log.i(TAG, "[faceDemo]openCamera!");
        CameraManager cameraManager = null;
        if (mActivity.getSystemService(Context.CAMERA_SERVICE) instanceof CameraManager) {
            cameraManager = (CameraManager) mActivity.getSystemService(Context.CAMERA_SERVICE);
        } else {
            return false;
        }
        try {
            if (ActivityCompat.checkSelfPermission(mActivity, Manifest.permission.CAMERA)
                    != PackageManager.PERMISSION_GRANTED) {
                return false;
            }

            // 2500 is the maximum time to wait for.
            if (!mCameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                throw new ArDemoRuntimeException("Time out waiting to lock camera opening.");
            }
            cameraManager.openCamera(mCameraId, mStateCallback, mCameraHandler);
        } catch (CameraAccessException | InterruptedException e) {
            Log.e(TAG, "openCamera error");
            return false;
        }
        return true;
    }

    /**
     * This method can turn off preview and camera devices.
     */
    public void closeCamera() {
        try {
            mCameraOpenCloseLock.acquire();
            Log.i(TAG, "[faceDemo]stop CameraCaptureSession begin!");
            stopPreview();
            Log.i(TAG, "[faceDemo]stop CameraCaptureSession stopped!");
            if (mCameraDevice != null) {
                Log.i(TAG, "[faceDemo]stop Camera!");
                mCameraDevice.close();
                mCameraDevice = null;
                Log.i(TAG, "[faceDemo]stop Camera stopped!");
            }
        } catch (InterruptedException e) {
            throw new ArDemoRuntimeException("Interrupted while trying to lock camera closing.", e);
        } finally {
            mCameraOpenCloseLock.release();
        }
    }

    private Size getOptimalSize(Size[] sizeMap, int width, int height) {
        List<Size> sizeList = new ArrayList<>();
        for (Size option : sizeMap) {
            if (width > height) {
                if (option.getWidth() > width && option.getHeight() > height) {
                    sizeList.add(option);
                }
            } else {
                if (option.getWidth() > height && option.getHeight() > width) {
                    sizeList.add(option);
                }
            }
        }
        if (sizeList.size() > 0) {
            return Collections.min(sizeList, new Comparator<Size>() {
                @Override
                public int compare(Size lhs, Size rhs) {
                    return Long.signum(lhs.getWidth() * lhs.getHeight() - rhs.getWidth() * rhs.getHeight());
                }
            });
        }
        return sizeMap[0];
    }

    /**
     * Set surface texture.
     *
     * @param surfaceTexture Surface texture.
     */
    void setPreviewTexture(SurfaceTexture surfaceTexture) {
        Log.i(TAG, "[faceDemo]setPreviewTexture");
        mSurfaceTexture = surfaceTexture;
    }

    /**
     * Set preview surface.
     *
     * @param surface Surface.
     */
    void setPreViewSurface(Surface surface) {
        Log.i(TAG, "[faceDemo]setPreViewSurface");
        mPreViewSurface = surface;
    }

    /**
     * Set VGA surface.
     *
     * @param surface Surface
     */
    void setVgaSurface(Surface surface) {
        Log.i(TAG, "[faceDemo]setVGASurface");
        mVgaSurface = surface;
    }

    /**
     * Set depth surface.
     *
     * @param surface Surface.
     */
    void setDepthSurface(Surface surface) {
        Log.i(TAG, "[faceDemo]setDepthSurface");
        mDepthSurface = surface;
    }

    private void startPreview() {
        if (mSurfaceTexture == null) {
            Log.i(TAG, "[faceDemo]mSurfaceTexture is null !");
            return;
        }
        Log.i(TAG, "[faceDemo]startPreview!");
        mSurfaceTexture.setDefaultBufferSize(mPreviewSize.getWidth(), mPreviewSize.getHeight());

        if (mCameraDevice == null) {
            Log.i(TAG, "[faceDemo]mCameraDevice is null!");
            return;
        }
        try {
            mCaptureRequestBuilder = mCameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            List<Surface> surfaces = new ArrayList<Surface>();
            if (mPreViewSurface != null) {
                surfaces.add(mPreViewSurface);
            }
            if (mVgaSurface != null) {
                surfaces.add(mVgaSurface);
            }
            if (mDepthSurface != null) {
                surfaces.add(mDepthSurface);
            }
            captureSession(surfaces);
        } catch (CameraAccessException e) {
            Log.e(TAG, "startPreview error");
        }
    }

    private void captureSession(List<Surface> surfaces) {
        try {
            mCameraDevice.createCaptureSession(surfaces, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession session) {
                    try {
                        if (mCameraDevice == null) {
                            Log.w(TAG, "[faceDemo]CameraDevice stop!");
                            return;
                        }
                        if (mPreViewSurface != null) {
                            mCaptureRequestBuilder.addTarget(mPreViewSurface);
                        }
                        if (mVgaSurface != null) {
                            mCaptureRequestBuilder.addTarget(mVgaSurface);
                        }
                        if (mDepthSurface != null) {
                            mCaptureRequestBuilder.addTarget(mDepthSurface);
                        }

                        // Set the number of frames to 30.
                        Range<Integer> fpsRange = new Range<Integer>(30, 30);
                        mCaptureRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, fpsRange);
                        List<CaptureRequest> captureRequests = new ArrayList<>();
                        captureRequests.add(mCaptureRequestBuilder.build());
                        mCameraCaptureSession = session;
                        mCameraCaptureSession.setRepeatingBurst(captureRequests, null, mCameraHandler);
                        mCameraOpenCloseLock.release();
                    } catch (CameraAccessException e) {
                        Log.e(TAG, "captureSession onConfigured error");
                    }
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                }

                @Override
                public void onClosed(@NonNull CameraCaptureSession session) {
                    Log.i(TAG, "[faceDemo]CameraCaptureSession stopped!");
                }
            }, mCameraHandler);
        } catch (CameraAccessException e) {
            Log.e(TAG, "captureSession error");
        }
    }

    private void stopPreview() {
        if (mCameraCaptureSession != null) {
            mCameraCaptureSession.close();
            mCameraCaptureSession = null;
        } else {
            Log.i(TAG, "[faceDemo]mCameraCaptureSession is null!");
        }
    }
}
