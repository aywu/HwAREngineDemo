/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2020-2020. All rights reserved.
 */

package com.huawei.arengine.demos.java.face.rendering;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.opengl.Matrix;
import android.util.Log;

import com.huawei.arengine.demos.common.ShaderUtil;
import com.huawei.hiar.ARCamera;
import com.huawei.hiar.ARFace;
import com.huawei.hiar.ARFaceGeometry;
import com.huawei.hiar.ARPose;

import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.IntBuffer;

/**
 * Get the data of face geometry and pass it to openGLES, rendered by openGLES and displayed on the screen.
 *
 * @author HW
 * @since 2020-03-24
 */
public class FaceGeometryDisplay {
    private static final String TAG = FaceGeometryDisplay.class.getSimpleName();

    private static final String LS = System.lineSeparator();

    private static final String FACE_GEOMETRY_VERTEX =
        "attribute vec2 inTexCoord;" + LS
        + "uniform mat4 inMVPMatrix;" + LS
        + "uniform float inPointSize;" + LS
        + "attribute vec4 inPosition;" + LS
        + "uniform vec4 inColor;" + LS
        + "varying vec4 varAmbient;" + LS
        + "varying vec4 varColor;" + LS
        + "varying vec2 varCoord;" + LS
        + "void main() {" + LS
        + "    varAmbient = vec4(1.0, 1.0, 1.0, 1.0);" + LS
        + "    gl_Position = inMVPMatrix * vec4(inPosition.xyz, 1.0);" + LS
        + "    varColor = inColor;" + LS
        + "    gl_PointSize = inPointSize;" + LS
        + "    varCoord = inTexCoord;" + LS
        + "}";

    private static final String FACE_GEOMETRY_FRAGMENT =
        "precision mediump float;" + LS
        + "uniform sampler2D inTexture;" + LS
        + "varying vec4 varColor;" + LS
        + "varying vec2 varCoord;" + LS
        + "varying vec4 varAmbient;" + LS
        + "void main() {" + LS
        + "    vec4 objectColor = texture2D(inTexture, vec2(varCoord.x, 1.0 - varCoord.y));" + LS
        + "    if(varColor.x != 0.0) {" + LS
        + "        gl_FragColor = varColor * varAmbient;" + LS
        + "    }" + LS
        + "    else {" + LS
        + "        gl_FragColor = objectColor * varAmbient;" + LS
        + "    }" + LS
        + "}";

    // Bytes occupied by each 3D coordinate point.
    // Each float occupies 4 bytes, and each point has 3 dimensional coordinate components.
    private static final int BYTES_PER_POINT = 4 * 3;

    // Bytes occupied by each 2D coordinate point.
    private static final int BYTES_PER_COORD = 4 * 2;

    private static final int BUFFER_OBJECT_NUMBER = 2;

    private static final int POSITION_COMPONENTS_NUMBER = 4;

    private static final int TEXCOORD_COMPONENTS_NUMBER = 2;

    private static final float PROJECTION_MATRIX_NEAR = 0.1f;

    private static final float PROJECTION_MATRIX_FAR = 100.0f;

    private int mVerticeId;

    private int mVerticeBufferSize = 8000; // Initialize vertice VBO Size, real is 7365.

    private int mTriangleId;

    private int mTriangleBufferSize = 5000; // Initialize triangle VBO size, real is 4434.

    private int mProgram;

    private int mTextureName;

    private int mPositionAttribute;

    private int mColorUniform;

    private int mModelViewProjectionUniform;

    private int mPointSizeUniform;

    private int mTextureUniform;

    private int mTextureCoordAttribute;

    private int mPointsNum = 0;

    private int mTrianglesNum = 0;

    // The size of the matrix is 16(4 * 4).
    private float[] mModelViewProjections = new float[16];

    /**
     * Allocates and initializes OpenGL resources needed by the plane renderer.
     * This method will be called by {@link RenderUtil#onSurfaceCreated}.
     *
     * @param context Needed to access shader source.
     */
    void init(Context context) {
        int[] texNames = new int[1];
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glGenTextures(1, texNames, 0);
        mTextureName = texNames[0];

        int[] buffers = new int[BUFFER_OBJECT_NUMBER];
        GLES20.glGenBuffers(BUFFER_OBJECT_NUMBER, buffers, 0);
        mVerticeId = buffers[0];
        mTriangleId = buffers[1];

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVerticeId);
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, mVerticeBufferSize * BYTES_PER_POINT, null, GLES20.GL_DYNAMIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, mTriangleId);

        // The bytes of each float is 4.
        GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, mTriangleBufferSize * 4, null,
            GLES20.GL_DYNAMIC_DRAW);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
        ShaderUtil.checkGlError(TAG, "buffer alloc");
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureName);

        createProgram();

        // Read the texture.
        Bitmap textureBitmap;
        try (InputStream inputStream = context.getAssets().open("face_geometry.png")) {
            textureBitmap = BitmapFactory.decodeStream(inputStream);
        } catch (IllegalArgumentException | IOException e) {
            Log.e(TAG, "open bitmap failed!");
            return;
        }

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, textureBitmap, 0);
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);
        ShaderUtil.checkGlError(TAG, "texture loading");
    }

    private void createProgram() {
        mProgram = createGlProgram();
        ShaderUtil.checkGlError(TAG, "program");
        mPositionAttribute = GLES20.glGetAttribLocation(mProgram, "inPosition");
        mColorUniform = GLES20.glGetUniformLocation(mProgram, "inColor");
        mModelViewProjectionUniform = GLES20.glGetUniformLocation(mProgram, "inMVPMatrix");
        mPointSizeUniform = GLES20.glGetUniformLocation(mProgram, "inPointSize");
        mTextureUniform = GLES20.glGetUniformLocation(mProgram, "inTexture");
        mTextureCoordAttribute = GLES20.glGetAttribLocation(mProgram, "inTexCoord");
        ShaderUtil.checkGlError(TAG, "program params");
    }

    private static int createGlProgram() {
        int vertex = loadShader(GLES20.GL_VERTEX_SHADER, FACE_GEOMETRY_VERTEX);
        if (vertex == 0) {
            return 0;
        }
        int fragment = loadShader(GLES20.GL_FRAGMENT_SHADER, FACE_GEOMETRY_FRAGMENT);
        if (fragment == 0) {
            return 0;
        }
        int program = GLES20.glCreateProgram();
        if (program != 0) {
            GLES20.glAttachShader(program, vertex);
            GLES20.glAttachShader(program, fragment);
            GLES20.glLinkProgram(program);
            int[] linkStatus = new int[1];
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0);
            if (linkStatus[0] != GLES20.GL_TRUE) {
                Log.e(TAG, "Could not link program " + GLES20.glGetProgramInfoLog(program));
                GLES20.glDeleteProgram(program);
                program = 0;
            }
        }
        return program;
    }

    private static int loadShader(int shaderType, String source) {
        int shader = GLES20.glCreateShader(shaderType);
        if (0 != shader) {
            GLES20.glShaderSource(shader, source);
            GLES20.glCompileShader(shader);
            int[] compiled = new int[1];
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
            if (compiled[0] == 0) {
                Log.e(TAG, "glError: Could not compile shader " + shaderType);
                Log.e(TAG, "GLES20 Error: " + GLES20.glGetShaderInfoLog(shader));
                GLES20.glDeleteShader(shader);
                shader = 0;
            }
        }
        return shader;
    }

    /**
     * Updating face geometry data in buffer.
     * This method will be called by {@link RenderUtil#onDrawFrame}.
     *
     * @param camera ARCamera.
     * @param face ARFace.
     */
    public void onDrawFrame(ARCamera camera, ARFace face) {
        ARFaceGeometry faceGeometry = face.getFaceGeometry();
        updateFaceGeometryData(faceGeometry);
        updateModelViewProjectionData(camera, face);
        drawFaceGeometry();
        faceGeometry.release();
    }

    /**
     * Update rendered face data, including face geometry vertices and triangle indices.
     *
     * @param faceGeometry ARFaceGeometry
     */
    private void updateFaceGeometryData(ARFaceGeometry faceGeometry) {
        ShaderUtil.checkGlError(TAG, "before update data");
        FloatBuffer faceVertices = faceGeometry.getVertices();

        // Each 3D point has 3 coordinates.
        mPointsNum = faceVertices.limit() / 3;

        FloatBuffer textureCoordinates = faceGeometry.getTextureCoordinates();

        // Each 2D point has 2 coordinates.
        int texNum = textureCoordinates.limit() / 2;
        Log.d(TAG, "updateData: texture coordinates size:" + texNum);

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVerticeId);
        if (mVerticeBufferSize < (mPointsNum + texNum) * BYTES_PER_POINT) {
            while (mVerticeBufferSize < (mPointsNum + texNum) * BYTES_PER_POINT) {
                mVerticeBufferSize *= 2; // If vertice VBO size is not big enough, double it.
            }
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, mVerticeBufferSize, null, GLES20.GL_DYNAMIC_DRAW);
        }
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, 0, mPointsNum * BYTES_PER_POINT, faceVertices);

        // Coordinate offset use mPointsNum * BYTES_PER_POINT.
        GLES20.glBufferSubData(GLES20.GL_ARRAY_BUFFER, mPointsNum * BYTES_PER_POINT, texNum * BYTES_PER_COORD,
                textureCoordinates);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);

        mTrianglesNum = faceGeometry.getTriangleCount();
        IntBuffer faceTriangleIndices = faceGeometry.getTriangleIndices();
        Log.d(TAG, "updateData: faceTriangleIndices.size:" + faceTriangleIndices.limit());

        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, mTriangleId);
        if (mTriangleBufferSize < mTrianglesNum * BYTES_PER_POINT) {
            while (mTriangleBufferSize < mTrianglesNum * BYTES_PER_POINT) {
                mTriangleBufferSize *= 2; // If triangle VBO size is not big enough ,double it.
            }
            GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, mTriangleBufferSize, null, GLES20.GL_DYNAMIC_DRAW);
        }
        GLES20.glBufferSubData(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0, mTrianglesNum * BYTES_PER_POINT, faceTriangleIndices);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
        ShaderUtil.checkGlError(TAG, "after update data");
    }

    /**
     * Update model view projection data, every frame is called.
     *
     * @param camera ARCamera
     * @param face ARFace
     */
    private void updateModelViewProjectionData(ARCamera camera, ARFace face) {
        // The size of the matrix is 16(4 * 4).
        float[] projectionMatrixs = new float[16];
        camera.getProjectionMatrix(projectionMatrixs, 0, PROJECTION_MATRIX_NEAR, PROJECTION_MATRIX_FAR);
        ARPose facePose = face.getPose();

        // The size of the matrix is 16(4 * 4).
        float[] facePoseViewMatrixs = new float[16];

        facePose.toMatrix(facePoseViewMatrixs, 0);
        Matrix.multiplyMM(mModelViewProjections, 0, projectionMatrixs, 0, facePoseViewMatrixs, 0);
    }

    /**
     * Rendering face geometry, every frame is called.
     */
    private void drawFaceGeometry() {
        ShaderUtil.checkGlError(TAG, "Before draw");
        Log.d(TAG, "draw: mPointsNum:" + mPointsNum + " mTrianglesNum:" + mTrianglesNum);

        // Attach the object texture.
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureName);
        GLES20.glUniform1i(mTextureUniform, 0);
        ShaderUtil.checkGlError(TAG, "init texture");

        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glEnable(GLES20.GL_CULL_FACE);

        // Draw point.
        GLES20.glUseProgram(mProgram);
        GLES20.glEnableVertexAttribArray(mPositionAttribute);
        GLES20.glEnableVertexAttribArray(mTextureCoordAttribute);
        GLES20.glEnableVertexAttribArray(mColorUniform);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, mVerticeId);
        GLES20.glVertexAttribPointer(mPositionAttribute, POSITION_COMPONENTS_NUMBER, GLES20.GL_FLOAT, false,
            BYTES_PER_POINT, 0);
        GLES20.glVertexAttribPointer(mTextureCoordAttribute, TEXCOORD_COMPONENTS_NUMBER, GLES20.GL_FLOAT, false,
            BYTES_PER_COORD, 0);
        GLES20.glUniform4f(mColorUniform, 1.0f, 0.0f, 0.0f, 1.0f);
        GLES20.glUniformMatrix4fv(mModelViewProjectionUniform, 1, false, mModelViewProjections, 0);
        GLES20.glUniform1f(mPointSizeUniform, 5.0f); // Set the size of Point to 5.
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, mPointsNum);
        GLES20.glDisableVertexAttribArray(mColorUniform);
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
        ShaderUtil.checkGlError(TAG, "Draw point");

        // Draw triangles.
        GLES20.glEnableVertexAttribArray(mColorUniform);

        // Clear color, draw trangles use texture color.
        GLES20.glUniform4f(mColorUniform, 0.0f, 0.0f, 0.0f, 0.0f);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, mTriangleId);

        // Each triangle has three vertices.
        GLES20.glDrawElements(GLES20.GL_TRIANGLES, mTrianglesNum * 3, GLES20.GL_UNSIGNED_INT, 0);
        GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
        GLES20.glDisableVertexAttribArray(mColorUniform);
        ShaderUtil.checkGlError(TAG, "Draw triangles");

        GLES20.glDisableVertexAttribArray(mTextureCoordAttribute);
        GLES20.glDisableVertexAttribArray(mPositionAttribute);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0);

        GLES20.glDisable(GLES20.GL_DEPTH_TEST);
        GLES20.glDisable(GLES20.GL_CULL_FACE);
        ShaderUtil.checkGlError(TAG, "Draw after");
    }
}