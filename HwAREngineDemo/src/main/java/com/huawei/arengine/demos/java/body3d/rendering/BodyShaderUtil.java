/*
 * Copyright (c) Huawei Technologies Co., Ltd. 2020-2020. All rights reserved.
 */

package com.huawei.arengine.demos.java.body3d.rendering;

import android.opengl.GLES20;
import android.util.Log;

/**
 * This class provides the shader code and program related to body rendering.
 *
 * @author hw
 * @since 2020-03-31
 */
class BodyShaderUtil {
    private static final String TAG = BodyShaderUtil.class.getSimpleName();

    private static final String LS = System.lineSeparator();

    /**
     * Body vertex shader code.
     */
    static final String BODY_VERTEX =
        "uniform vec4 inColor;" + LS
        + "attribute vec4 inPosition;" + LS
        + "uniform float inPointSize;" + LS
        + "varying vec4 varColor;" + LS
        + "uniform mat4 inMVPMatrix;" + LS
        + "uniform float inCoordinateSystem;" + LS
        + "void main() {" + LS
        + "    vec4 position = vec4(inPosition.xyz, 1.0);" + LS
        + "    if (inCoordinateSystem == 2.0) {" + LS
        + "        position = inMVPMatrix * position;" + LS
        + "    }" + LS
        + "    gl_Position = position;" + LS
        + "    varColor = inColor;" + LS
        + "    gl_PointSize = inPointSize;" + LS
        + "}";

    /**
     * Body fragment shader code.
     */
    static final String BODY_FRAGMENT =
        "precision mediump float;" + LS
        + "varying vec4 varColor;" + LS
        + "void main() {" + LS
        + "    gl_FragColor = varColor;" + LS
        + "}";

    private BodyShaderUtil() {
    }

    /**
     * Create shader program.
     *
     * @return shader program.
     */
    static int createGlProgram() {
        int vertex = loadShader(GLES20.GL_VERTEX_SHADER, BODY_VERTEX);
        if (vertex == 0) {
            return 0;
        }
        int fragment = loadShader(GLES20.GL_FRAGMENT_SHADER, BODY_FRAGMENT);
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
}