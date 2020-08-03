/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2020-2020. All rights reserved.
 */

package com.huawei.arengine.demos.java.body3d.rendering;

import android.opengl.GLES20;

import com.huawei.arengine.demos.common.ShaderUtil;
import com.huawei.hiar.ARBody;
import com.huawei.hiar.ARCoordinateSystemType;
import com.huawei.hiar.ARTrackable;

import java.nio.FloatBuffer;
import java.util.Collection;

/**
 * Get the joint connection data and pass it to openGLES, rendered by OpenGLES and displayed on the screen.
 *
 * @author HW
 * @since 2020-03-31
 */
public class BodySkeletonLineDisplay {
    private static final String TAG = BodySkeletonLineDisplay.class.getSimpleName();

    // Bytes occupied by each 3D coordinate point.
    // Each float occupies 4 bytes, and each point has 3 dimensional coordinate components.
    private static final int BYTES_PER_POINT = 4 * 3;

    private static final int INITIAL_BUFFER_POINTS = 150;

    private static final float COORDINATE_SYSTEM_TYPE_3D_FLAG = 2.0f;

    private static final int LINE_POINT_RATIO = 6;

    private int mVbo;

    private int mVboSize;

    private int mProgram;

    private int mPosition;

    private int mModelViewProjectionMatrix;

    private int mColor;

    private int mPointSize;

    private int mCoordinateSystem;

    private int mNumPoints = 0;

    private int mPointsLineNum = 0;

    private FloatBuffer mLinePoints;

    /**
     * Constructor.
     */
    BodySkeletonLineDisplay() {
    }

    /**
     * Created and compiler hand skeleton line shader On GL thread.
     * This method will be called by {@link RenderUtil#onSurfaceCreated}.
     */
    public void init() {
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
        mProgram = BodyShaderUtil.createGlProgram();
        ShaderUtil.checkGlError(TAG, "program");
        mPosition = GLES20.glGetAttribLocation(mProgram, "inPosition");
        mColor = GLES20.glGetUniformLocation(mProgram, "inColor");
        mPointSize = GLES20.glGetUniformLocation(mProgram, "inPointSize");
        mModelViewProjectionMatrix = GLES20.glGetUniformLocation(mProgram, "inMVPMatrix");
        mCoordinateSystem = GLES20.glGetUniformLocation(mProgram, "inCoordinateSystem");
        ShaderUtil.checkGlError(TAG, "program params");
    }

    /**
     * Renders the body skeleton.
     *
     * @param coordinate Coordinate.
     * @param projectionMatrix projection matrix.
     */
    public void drawSkeletonLine(float coordinate, float[] projectionMatrix) {
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
        GLES20.glUniform4f(mColor, 1.0f, 0.0f, 0.0f, 1.0f);
        GLES20.glUniformMatrix4fv(mModelViewProjectionMatrix, 1, false, projectionMatrix, 0);

        // Set the size of painted joint points.
        GLES20.glUniform1f(mPointSize, 100.0f);
        GLES20.glUniform1f(mCoordinateSystem, coordinate);

        GLES20.glDrawArrays(GLES20.GL_LINES, 0, mNumPoints);
        GLES20.glDisableVertexAttribArray(mPosition);
        GLES20.glDisableVertexAttribArray(mColor);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        ShaderUtil.checkGlError(TAG, "Draw");
    }

    /**
     * Rendering link lines between body bones.
     * This method will be called by {@link RenderUtil#onDrawFrame}.
     *
     * @param bodies Bodies data.
     * @param projectionMatrix projection matrix.
     */
    public void onDrawFrame(Collection<ARBody> bodies, float[] projectionMatrix) {
        for (ARBody body : bodies) {
            if (body.getTrackingState() == ARTrackable.TrackingState.TRACKING) {
                float coordinate = 1.0f;
                if (body.getCoordinateSystemType() == ARCoordinateSystemType.COORDINATE_SYSTEM_TYPE_3D_CAMERA) {
                    coordinate = COORDINATE_SYSTEM_TYPE_3D_FLAG;
                }
                updateBodySkeletonLineData(body);
                drawSkeletonLine(coordinate, projectionMatrix);
            }
        }
    }

    /**
     * Update body connection data.
     */
    private void updateBodySkeletonLineData(ARBody body) {
        findValidConnectionSkeletonLines(body);
        ShaderUtil.checkGlError(TAG, "before updateBodyConnection");
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVbo);
        mNumPoints = mPointsLineNum;
        if (mVboSize < mNumPoints * BYTES_PER_POINT) {
            while (mVboSize < mNumPoints * BYTES_PER_POINT) {
                // If this storage space is not enough, double it every time.
                mVboSize *= 2;
            }
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, mVboSize, null, GLES20.GL_DYNAMIC_DRAW);
        }
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, mNumPoints * BYTES_PER_POINT, mLinePoints);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        ShaderUtil.checkGlError(TAG, "after updateBodyConnection");
    }

    /**
     * Find valid connections.
     *
     * @param arBody ARBody.
     */
    private void findValidConnectionSkeletonLines(ARBody arBody) {
        mPointsLineNum = 0;
        int[] connections = arBody.getBodySkeletonConnection();
        float[] linePoints = new float[LINE_POINT_RATIO * connections.length];
        float[] coors;
        int[] isExists;

        if (arBody.getCoordinateSystemType() == ARCoordinateSystemType.COORDINATE_SYSTEM_TYPE_3D_CAMERA) {
            coors = arBody.getSkeletonPoint3D();
            isExists = arBody.getSkeletonPointIsExist3D();
        } else {
            coors = arBody.getSkeletonPoint2D();
            isExists = arBody.getSkeletonPointIsExist2D();
        }

        // Store three-dimensional coordinates of adjacent nodes for drawing
        // connection:[p0,p1;p0,p3;p0,p5;p1,p2],in connection, every data represent the point index,
        // two index can get a group of joint points,so j = j + 2.
        // This loop takes the associated coordinates(three dimensions for each point) out and conserves them in turn.
        for (int j = 0; j < connections.length; j += 2) {
            if (isExists[connections[j]] != 0 && isExists[connections[j + 1]] != 0) {
                linePoints[mPointsLineNum * 3] = coors[3 * connections[j]];
                linePoints[mPointsLineNum * 3 + 1] = coors[3 * connections[j] + 1];
                linePoints[mPointsLineNum * 3 + 2] = coors[3 * connections[j] + 2];
                linePoints[mPointsLineNum * 3 + 3] = coors[3 * connections[j + 1]];
                linePoints[mPointsLineNum * 3 + 4] = coors[3 * connections[j + 1] + 1];
                linePoints[mPointsLineNum * 3 + 5] = coors[3 * connections[j + 1] + 2];
                mPointsLineNum += 2;
            }
        }
        mLinePoints = FloatBuffer.wrap(linePoints);
    }
}