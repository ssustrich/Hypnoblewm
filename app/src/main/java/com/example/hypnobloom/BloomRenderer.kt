package com.example.hypnobloom

import android.opengl.GLES20
import android.os.SystemClock
import java.nio.ByteBuffer
import java.nio.ByteOrder

class BloomRenderer {
    private val vertexShader = """
        attribute vec2 aPosition;
        varying vec2 vUv;
        void main() {
            vUv = aPosition * 0.5 + 0.5;
            gl_Position = vec4(aPosition, 0.0, 1.0);
        }
    """.trimIndent()

    private val fragmentShader = """
        precision highp float;
        varying vec2 vUv;
        uniform vec2 uResolution;
        uniform float uTime;
        uniform float uAudio;
        const float TAU = 6.28318530718;

        void main() {
            vec2 p = (gl_FragCoord.xy - 0.5 * uResolution) / min(uResolution.x, uResolution.y);
            float r = length(p);
            float a = atan(p.y, p.x);
            float breath = 0.08 * sin(uTime * 0.75);
            float twist = 1.2 * r + 0.19 * sin(5.0 * r - uTime * 0.7);
            a += twist + uTime * 0.18 + uAudio * 0.35;
            float radius = r * (19.0 + 2.0 * uAudio)
                - uTime * (0.75 + uAudio * 1.8)
                + 0.18 * sin(9.0 * a + uTime * 0.6)
                + breath;
            float spokes = a * 12.0 / TAU;
            // Alternating curved checker cells in polar coordinates.
            float radialCheck = step(0.5, fract(radius));
            float angularCheck = step(0.5, fract(spokes));
            float checker = abs(radialCheck - angularCheck);
            float edgeR = abs(fract(radius) - 0.5);
            float edgeA = abs(fract(spokes) - 0.5);
            float seam = 1.0 - smoothstep(0.44, 0.50, max(edgeR, edgeA));

            float huePhase = 2.5 * r - uTime * 0.16 + 0.12 * sin(a * 3.0);
            vec3 jewel = vec3(
                0.48 + 0.45 * sin(huePhase + 0.0),
                0.48 + 0.45 * sin(huePhase + 2.1),
                0.48 + 0.45 * sin(huePhase + 4.2)
            );
            vec3 dark = jewel * 0.08;
            vec3 bright = jewel * (0.9 + 0.35 * uAudio);
            vec3 color = mix(dark, bright, checker);
            color += seam * jewel * 0.05;
            color *= 1.0 - 0.28 * smoothstep(0.4, 1.3, r);
            gl_FragColor = vec4(color, 1.0);
        }
    """.trimIndent()

    private val vertices = ByteBuffer.allocateDirect(8 * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
            put(floatArrayOf(-1f, -1f, 1f, -1f, -1f, 1f, 1f, 1f)); position(0)
        }
    private var program = 0
    private var position = -1
    private var resolution = -1
    private var time = -1
    private var audio = -1
    private val startNanos = SystemClock.elapsedRealtimeNanos()

    fun create() {
        val vs = compile(GLES20.GL_VERTEX_SHADER, vertexShader)
        val fs = compile(GLES20.GL_FRAGMENT_SHADER, fragmentShader)
        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glLinkProgram(program)
        val linked = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0)
        if (linked[0] == 0) error("Program link: ${GLES20.glGetProgramInfoLog(program)}")
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        position = GLES20.glGetAttribLocation(program, "aPosition")
        resolution = GLES20.glGetUniformLocation(program, "uResolution")
        time = GLES20.glGetUniformLocation(program, "uTime")
        audio = GLES20.glGetUniformLocation(program, "uAudio")
    }

    fun draw(width: Int, height: Int, level: Float) {
        GLES20.glViewport(0, 0, width, height)
        GLES20.glUseProgram(program)
        GLES20.glUniform2f(resolution, width.toFloat(), height.toFloat())
        GLES20.glUniform1f(time, (SystemClock.elapsedRealtimeNanos() - startNanos) / 1e9f)
        GLES20.glUniform1f(audio, level)
        vertices.position(0)
        GLES20.glEnableVertexAttribArray(position)
        GLES20.glVertexAttribPointer(position, 2, GLES20.GL_FLOAT, false, 0, vertices)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
        GLES20.glDisableVertexAttribArray(position)
    }

    fun destroy() {
        if (program != 0) GLES20.glDeleteProgram(program)
        program = 0
    }

    private fun compile(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val status = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0)
        if (status[0] == 0) error("Shader compile: ${GLES20.glGetShaderInfoLog(shader)}")
        return shader
    }
}