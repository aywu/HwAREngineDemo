/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2020-2020. All rights reserved.
 */

package com.huawei.arengine.demos.java.hand.rendering;

import android.opengl.GLES20;
import android.util.Log;

import com.huawei.arengine.demos.common.MatrixUtil;
import com.huawei.arengine.demos.common.ShaderUtil;
import com.huawei.hiar.ARHand;
import com.huawei.hiar.ARTrackable;

import java.nio.FloatBuffer;
import java.util.Collection;

/**
 * This class shows how to get maximum surrounding rectangle of the hand in the tracking
 * state, through this class, the maximum rectangle can be presented on the screen.
 *
 * @author HW
 * @since 2020-03-16
 */
class HandBoxDisplay {
    private static final String TAG = HandBoxDisplay.class.getSimpleName();

    // Bytes occupied by each 3D coordinate point.
    // Each float occupies 4 bytes, and each point has 3 dimensional coordinate components.
    private static final int BYTES_PER_POINT = 4 * 3;
    private static final int INITIAL_BUFFER_POINTS = 150;
    private static final int COORDINATE_DIMENSION = 3;

    private int mVbo;

    private int mVboSize;

    private int mProgram;

    private int mPosition;

    private int mColor;

    private int mModelViewProjectionMatrix;

    private int mPointSize;

    private int mNumPoints = 0;

    private float[] mMVPMatrixs;

    /**
     * Created and compiler hand gesture display shader on the OpenGL Thread.
     * This method will be called by {@link RenderUtil#onSurfaceCreated}.
     */
    void init() {
        ShaderUtil.checkGlError(TAG, "before create");
        mMVPMatrixs = MatrixUtil.getOriginalMatrix();
        int[] buffers = new int[1];
        GLES20.glGenBuffers(1, buffers, 0);
        mVbo = buffers[0];
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVbo);

        mVboSize = INITIAL_BUFFER_POINTS * BYTES_PER_POINT;
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, mVboSize, null, GLES20.GL_DYNAMIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        createProgram();
    }

    private void createProgram() {
        mProgram = HandShaderUtil.createGlProgram();
        ShaderUtil.checkGlError(TAG, "program");
        mPosition = GLES20.glGetAttribLocation(mProgram, "inPosition");
        mColor = GLES20.glGetUniformLocation(mProgram, "inColor");
        mPointSize = GLES20.glGetUniformLocation(mProgram, "inPointSize");
        mModelViewProjectionMatrix = GLES20.glGetUniformLocation(mProgram, "inMVPMatrix");
        ShaderUtil.checkGlError(TAG, "program params");
    }

    /**
     * Rendering the largest rectangular box of gestures and hand related information.
     * This method will be called by {@link RenderUtil#onDrawFrame}.
     *
     * @param hands Hand data.
     */
    void onDrawFrame(Collection<ARHand> hands) {
        if (hands.size() == 0) {
            return;
        }
        for (ARHand hand : hands) {
            float[] gestureHandBoxPoints = hand.getGestureHandBox();
            if (hand.getTrackingState() == ARTrackable.TrackingState.TRACKING) {
                // Update the maximum box coordinate point data surrounding the hand.
                updateHandBoxData(gestureHandBoxPoints);

                // Draw the largest rectangular box around the hand.
                drawHandBox();
            }
        }
    }

    /**
     * Update the coordinate data of the largest bounding box used to draw the hand.
     *
     * @param gesturePoints Gesture hand box data.
     */
    private void updateHandBoxData(float[] gesturePoints) {
        float[] glGesturePoints = {
            // Get the coordinates of four points of the rectangular frame surrounding the palm.
            gesturePoints[0], gesturePoints[1], gesturePoints[2],
            gesturePoints[3], gesturePoints[1], gesturePoints[2],
            gesturePoints[3], gesturePoints[4], gesturePoints[5],
            gesturePoints[0], gesturePoints[4], gesturePoints[5],
        };
        int gesturePointsNum = glGesturePoints.length / COORDINATE_DIMENSION;
        FloatBuffer mVertices = FloatBuffer.wrap(glGesturePoints);
        ShaderUtil.checkGlError(TAG, "before update");

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVbo);

        mNumPoints = gesturePointsNum;
        if (mVboSize < mNumPoints * BYTES_PER_POINT) {
            while (mVboSize < mNumPoints * BYTES_PER_POINT) {
                mVboSize *= 2; // If the VBO is not large enough to fit the new point cloud, resize it.
            }
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, mVboSize, null, GLES20.GL_DYNAMIC_DRAW);
        }
        Log.d(TAG, "gesture.getGestureHandPointsNum()" + mNumPoints);
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, mNumPoints * BYTES_PER_POINT,
                mVertices);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        ShaderUtil.checkGlError(TAG, "after update");
    }

    /**
     * Transfer color, point size, line width and other data to shader, and draw hand gesture.
     */
    private void drawHandBox() {
        ShaderUtil.checkGlError(TAG, "Before draw");
        GLES20.glUseProgram(mProgram);
        GLES20.glEnableVertexAttribArray(mPosition);
        GLES20.glEnableVertexAttribArray(mColor);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVbo);
        GLES20.glVertexAttribPointer(
                mPosition, COORDINATE_DIMENSION, GLES20.GL_FLOAT, false, BYTES_PER_POINT, 0);
        GLES20.glUniform4f(mColor, 1.0f, 0.0f, 0.0f, 1.0f);

        GLES20.glUniformMatrix4fv(mModelViewProjectionMatrix, 1, false, mMVPMatrixs, 0);

        // Set the size of rendered points.
        GLES20.glUniform1f(mPointSize, 50.0f);

        // Set the width of the rendered line.
        GLES20.glLineWidth(18.0f);
        GLES20.glDrawArrays(GLES20.GL_LINE_LOOP, 0, mNumPoints);
        GLES20.glDisableVertexAttribArray(mPosition);
        GLES20.glDisableVertexAttribArray(mColor);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        ShaderUtil.checkGlError(TAG, "Draw");
    }
}
