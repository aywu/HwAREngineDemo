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
 * Get the joint connection data and pass it to openGLES, rendered by OpenGLES and displayed on the screen.
 *
 * @author HW
 * @since 2020-03-09
 */
class HandSkeletonLineDisplay {
    private static final String TAG = HandSkeletonLineDisplay.class.getSimpleName();

    // Bytes occupied by each 3D coordinate point.
    // Each float occupies 4 bytes, and each point has 3 dimensional coordinate components.
    private static final int BYTES_PER_POINT = 4 * 3;

    private static final int INITIAL_BUFFER_POINTS = 150;

    private static final float JOINT_POINT_SIZE = 100f;

    private int mVbo;

    private int mVboSize;

    private int mProgram;

    private int mPosition;

    private int mModelViewProjectionMatrix;

    private int mColor;

    private int mPointSize;

    private int mPointsNum = 0;

    /**
     * Created and compiler hand skeleton line shader On GL thread.
     * This method will be called by {@link RenderUtil#onSurfaceCreated}.
     */
    void init() {
        ShaderUtil.checkGlError(TAG, "before create");

        int[] buffers = new int[1];
        GLES20.glGenBuffers(1, buffers, 0);
        mVbo = buffers[0];
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVbo);

        mVboSize = INITIAL_BUFFER_POINTS * BYTES_PER_POINT;
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
     * Rendering link lines between hand bones.
     * This method will be called by {@link RenderUtil#onDrawFrame}.
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
            int[] handSkeletonConnections = hand.getHandSkeletonConnection();
            if (handSkeletons.length == 0 || handSkeletonConnections.length == 0) {
                continue;
            }
            updateHandSkeletonLinesData(handSkeletons, handSkeletonConnections);
            drawHandSkeletonLine(projectionMatrix);
        }
        Log.d(TAG, "onDrawFrame >>");
    }

    /**
     * Every frame is called to update the connection data between bone points
     *
     * @param handSkeletons Bone point data of hand.
     * @param handSkeletonConnection Data of connection between bone points of hand.
     */
    private void updateHandSkeletonLinesData(float[] handSkeletons, int[] handSkeletonConnection) {
        int pointsLineNum = 0;

        // Each point has three dimensions and each line has two points.
        float[] linePoint = new float[handSkeletonConnection.length * 3 * 2];

        // HandSkeletonConnection:[p0,p1;p0,p3;p0,p5;p1,p2], in handSkeletonConnection,
        // every data represent the point index.
        // two index can get a group of joined points, so j = j + 2.
        // This loop takes the associated coordinates(three dimensions for each point) out and conserves them in turn.
        for (int j = 0; j < handSkeletonConnection.length; j += 2) {
            linePoint[pointsLineNum * 3] = handSkeletons[3 * handSkeletonConnection[j]];
            linePoint[pointsLineNum * 3 + 1] = handSkeletons[3 * handSkeletonConnection[j] + 1];
            linePoint[pointsLineNum * 3 + 2] = handSkeletons[3 * handSkeletonConnection[j] + 2];
            linePoint[pointsLineNum * 3 + 3] = handSkeletons[3 * handSkeletonConnection[j + 1]];
            linePoint[pointsLineNum * 3 + 4] = handSkeletons[3 * handSkeletonConnection[j + 1] + 1];
            linePoint[pointsLineNum * 3 + 5] = handSkeletons[3 * handSkeletonConnection[j + 1] + 2];
            pointsLineNum += 2;
        }
        ShaderUtil.checkGlError(TAG, "before update");
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVbo);
        mPointsNum = pointsLineNum;

        // If this storage space is not enough, double it every time.
        if (mVboSize < mPointsNum * BYTES_PER_POINT) {
            while (mVboSize < mPointsNum * BYTES_PER_POINT) {
                mVboSize *= 2;
            }
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, mVboSize, null, GLES20.GL_DYNAMIC_DRAW);
        }
        FloatBuffer linePoints = FloatBuffer.wrap(linePoint);
        Log.d(TAG, "skeleton.getSkeletonLinePointsNum()" + mPointsNum);
        Log.d(TAG, "Skeleton Line Points: " + linePoints.toString());
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, mPointsNum * BYTES_PER_POINT,
            linePoints);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        ShaderUtil.checkGlError(TAG, "after update");
    }

    /**
     * Renders the hand skeleton line.
     *
     * @param projectionMatrix Projection matrix(4 * 4).
     */
    private void drawHandSkeletonLine(float[] projectionMatrix) {
        ShaderUtil.checkGlError(TAG, "Before draw");
        GLES20.glUseProgram(mProgram);
        GLES20.glEnableVertexAttribArray(mPosition);
        GLES20.glEnableVertexAttribArray(mColor);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVbo);

        // Set the width of the rendered line.
        GLES20.glLineWidth(18.0f);

        // In shader, each point is defined as four dimensions.
        GLES20.glVertexAttribPointer(
                mPosition, 4, GLES20.GL_FLOAT, false, BYTES_PER_POINT, 0);
        GLES20.glUniform4f(mColor, 0.0f, 0.0f, 0.0f, 1.0f);
        GLES20.glUniformMatrix4fv(mModelViewProjectionMatrix, 1, false, projectionMatrix, 0);

        //
        GLES20.glUniform1f(mPointSize, JOINT_POINT_SIZE);

        GLES20.glDrawArrays(GLES20.GL_LINES, 0, mPointsNum);
        GLES20.glDisableVertexAttribArray(mPosition);
        GLES20.glDisableVertexAttribArray(mColor);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        ShaderUtil.checkGlError(TAG, "Draw");
    }
}