/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2020-2020. All rights reserved.
 */

package com.huawei.arengine.demos.java.hand.rendering;

import android.opengl.GLES20;
import android.util.Log;

import com.huawei.arengine.demos.common.ShaderUtil;
import com.huawei.hiar.ARHand;

import java.nio.FloatBuffer;
import java.util.Collection;

/**
 * Get the data of the joints and pass it to openGLES, rendered by OpenGLES and displayed on the screen.
 *
 * @author HW
 * @since 2020-03-16
 */
class HandSkeletonDisplay {
    private static final String TAG = HandSkeletonDisplay.class.getSimpleName();

    // Bytes occupied by each 3D coordinate point.
    // Each float occupies 4 bytes, and each point has 3 dimensional coordinate components.
    private static final int BYTES_PER_POINT = 4 * 3;

    private static final int INITIAL_POINTS_SIZE = 150;

    private int mVbo;

    private int mVboSize;

    private int mProgram;

    private int mPosition;

    private int mModelViewProjectionMatrix;

    private int mColor;

    private int mPointSize;

    private int mNumPoints = 0;

    /**
     * Created and compiler hand skeleton display shader on the OpenGL Thread.
     * This method will be called when {@link RenderUtil#onSurfaceCreated}.
     */
    void init() {
        ShaderUtil.checkGlError(TAG, "before create");
        int[] buffers = new int[1];
        GLES20.glGenBuffers(1, buffers, 0);
        mVbo = buffers[0];
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVbo);
        mVboSize = INITIAL_POINTS_SIZE * BYTES_PER_POINT;
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, mVboSize, null, GLES20.GL_DYNAMIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        ShaderUtil.checkGlError(TAG, "buffer alloc");
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
     * Update the hand bone point data and render it.
     * This method will be called when {@link RenderUtil#onDrawFrame}.
     *
     * @param hands ARHand data collection.
     * @param projectionMatrix Projection matrix(4 * 4).
     */
    void onDrawFrame(Collection<ARHand> hands, float[] projectionMatrix) {
        Log.d(TAG, "onDrawFrame <<");

        // External input verification. If the data of the hand is empty or the projection
        // matrix is empty or not 4 * 4, this rendering will not be performed.
        if (hands.isEmpty() || projectionMatrix == null || projectionMatrix.length != 16) {
            Log.e(TAG, "onDrawFrame Illegal external input!");
            return;
        }
        for (ARHand hand : hands) {
            float[] handSkeletons = hand.getHandskeletonArray();
            if (handSkeletons.length == 0) {
                continue;
            }
            updateHandSkeletonsData(handSkeletons);
            drawHandSkeletons(projectionMatrix);
        }
        Log.d(TAG, "onDrawFrame >>");
    }

    /**
     * Update data of hand bone points.
     */
    private void updateHandSkeletonsData(float[] handSkeletons) {
        ShaderUtil.checkGlError(TAG, "before update data");

        // each point has three coordinates, the number of coordinates divided by three equals the number of points
        int mPointsNum = handSkeletons.length / 3;
        FloatBuffer mSkeletonPoints = FloatBuffer.wrap(handSkeletons);
        Log.d(TAG, "ARHand HandSkeletonNumber = " + mPointsNum);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVbo);
        mNumPoints = mPointsNum;
        if (mVboSize < mNumPoints * BYTES_PER_POINT) {
            while (mVboSize < mNumPoints * BYTES_PER_POINT) {
                mVboSize *= 2; // If the VBO is not large enough to fit the new point cloud, resize it.
            }
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, mVboSize, null, GLES20.GL_DYNAMIC_DRAW);
        }
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, mNumPoints * BYTES_PER_POINT,
                mSkeletonPoints);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        ShaderUtil.checkGlError(TAG, "after update data");
    }

    /**
     * Renders the hand skeleton points.
     *
     * @param projectionMatrix Projection matrix.
     */
    private void drawHandSkeletons(float[] projectionMatrix) {
        ShaderUtil.checkGlError(TAG, "Before draw");
        GLES20.glUseProgram(mProgram);
        GLES20.glEnableVertexAttribArray(mPosition);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVbo);

        // Number of components for vertex attributes(vertex have four components).
        GLES20.glVertexAttribPointer(
                mPosition, 4, GLES20.GL_FLOAT, false, BYTES_PER_POINT, 0);

        // Set the color of the hand bone points to blue.
        GLES20.glUniform4f(mColor, 0.0f, 0.0f, 1.0f, 1.0f);
        GLES20.glUniformMatrix4fv(mModelViewProjectionMatrix, 1, false, projectionMatrix, 0);

        // Set the size of hand bone points for rendering.
        GLES20.glUniform1f(mPointSize, 30.0f);

        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, mNumPoints);
        GLES20.glDisableVertexAttribArray(mPosition);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        ShaderUtil.checkGlError(TAG, "Draw");
    }
}