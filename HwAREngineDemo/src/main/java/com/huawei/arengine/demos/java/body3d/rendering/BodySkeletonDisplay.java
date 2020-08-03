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
 * Get the data of the joints and pass it to openGLES, rendered by OpenGLES and displayed on the screen.
 *
 * @author HW
 * @since 2020-03-27
 */
public class BodySkeletonDisplay {
    private static final String TAG = BodySkeletonDisplay.class.getSimpleName();

    // Bytes occupied by each 3D coordinate point.
    // Each float occupies 4 bytes, and each point has 3 dimensional coordinate components.
    private static final int BYTES_PER_POINT = 4 * 3;

    private static final int INITIAL_POINTS_SIZE = 150;

    private static final float DRAW_COORDINATE = 2.0f;

    private int mVbo;

    private int mVboSize;

    private int mProgram;

    private int mPosition;

    private int mModelViewProjectionMatrix;

    private int mColor;

    private int mPointSize;

    private int mCoordinateSystem;

    private int mNumPoints = 0;

    private int mPointsNum = 0;

    private FloatBuffer mSkeletonPoints;

    /**
     * Allocates and initializes OpenGL resources needed by the plane renderer. Must be
     * called on the OpenGL thread, typically in
     * This method will be called when {@link RenderUtil#onSurfaceCreated}.
     */
    public void init() {
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
        mProgram = BodyShaderUtil.createGlProgram();
        ShaderUtil.checkGlError(TAG, "program");
        mColor = GLES20.glGetUniformLocation(mProgram, "inColor");
        mPosition = GLES20.glGetAttribLocation(mProgram, "inPosition");
        mPointSize = GLES20.glGetUniformLocation(mProgram, "inPointSize");
        mModelViewProjectionMatrix = GLES20.glGetUniformLocation(mProgram, "inMVPMatrix");
        mCoordinateSystem = GLES20.glGetUniformLocation(mProgram, "inCoordinateSystem");
        ShaderUtil.checkGlError(TAG, "program params");
    }

    private void updateBodySkeleton() {
        ShaderUtil.checkGlError(TAG, "before updateBodySkeleton");

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVbo);
        mNumPoints = mPointsNum;

        if (mVboSize < mNumPoints * BYTES_PER_POINT) {
            while (mVboSize < mNumPoints * BYTES_PER_POINT) {
                mVboSize *= 2; // If the VBO is not large enough to fit the new point cloud, resize it.
            }
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, mVboSize, null, GLES20.GL_DYNAMIC_DRAW);
        }
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, mNumPoints * BYTES_PER_POINT, mSkeletonPoints);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        ShaderUtil.checkGlError(TAG, "after updateBodySkeleton");
    }

    /**
     * Update data of articulation points in buffers.
     * This method will be called when {@link RenderUtil#onDrawFrame}.
     *
     * @param bodies Body data.
     * @param projectionMatrix projection matrix.
     */
    public void onDrawFrame(Collection<ARBody> bodies, float[] projectionMatrix) {
        for (ARBody body : bodies) {
            if (body.getTrackingState() == ARTrackable.TrackingState.TRACKING) {
                float coordinate = 1.0f;
                if (body.getCoordinateSystemType() == ARCoordinateSystemType.COORDINATE_SYSTEM_TYPE_3D_CAMERA) {
                    coordinate = DRAW_COORDINATE;
                }
                findValidSkeletonPoints(body);
                updateBodySkeleton();
                drawBodySkeleton(coordinate, projectionMatrix);
            }
        }
    }

    /**
     * Renders the body skeleton.
     *
     * @param coordinate Coordinate.
     * @param projectionMatrix Projection matrix.
     */
    private void drawBodySkeleton(float coordinate, float[] projectionMatrix) {
        ShaderUtil.checkGlError(TAG, "Before draw");

        GLES20.glUseProgram(mProgram);
        GLES20.glEnableVertexAttribArray(mPosition);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVbo);

        // Number of components for vertex attributes(vertex have four components).
        GLES20.glVertexAttribPointer(
                mPosition, 4, GLES20.GL_FLOAT, false, BYTES_PER_POINT, 0);
        GLES20.glUniform4f(mColor, 0.0f, 0.0f, 1.0f, 1.0f);
        GLES20.glUniformMatrix4fv(mModelViewProjectionMatrix, 1, false, projectionMatrix, 0);

        // Set the size of hand bone points for rendering.
        GLES20.glUniform1f(mPointSize, 30.0f);
        GLES20.glUniform1f(mCoordinateSystem, coordinate);

        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, mNumPoints);
        GLES20.glDisableVertexAttribArray(mPosition);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        ShaderUtil.checkGlError(TAG, "Draw");
    }

    private void findValidSkeletonPoints(ARBody arBody) {
        int index = 0;
        int[] isExists;
        int validPointNum = 0;
        float[] points;
        float[] skeletonPoints;

        // Each point has three coordinates, and an array of three times
        // the number of points is constructed to place the coordinates.
        if (arBody.getCoordinateSystemType() == ARCoordinateSystemType.COORDINATE_SYSTEM_TYPE_3D_CAMERA) {
            isExists = arBody.getSkeletonPointIsExist3D();
            points = new float[isExists.length * 3];
            skeletonPoints = arBody.getSkeletonPoint3D();
        } else {
            isExists = arBody.getSkeletonPointIsExist2D();
            points = new float[isExists.length * 3];
            skeletonPoints = arBody.getSkeletonPoint2D();
        }

        // Save the three coordinates of each joint point(each point has three coordinates).
        for (int i = 0; i < isExists.length; i++) {
            if (isExists[i] != 0) {
                points[index++] = skeletonPoints[3 * i];
                points[index++] = skeletonPoints[3 * i + 1];
                points[index++] = skeletonPoints[3 * i + 2];
                validPointNum++;
            }
        }
        mSkeletonPoints = FloatBuffer.wrap(points);
        mPointsNum = validPointNum;
    }
}