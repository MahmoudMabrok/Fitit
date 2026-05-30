package tools.mo3ta.fitit.ui.videoenhancer

import android.graphics.Bitmap
import android.graphics.SurfaceTexture
import android.opengl.EGL14
import android.opengl.EGLConfig
import android.opengl.EGLContext
import android.opengl.EGLDisplay
import android.opengl.EGLExt
import android.opengl.EGLSurface
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLUtils
import android.view.Surface
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Wraps the encoder's input [Surface] in an EGL window surface and owns the (shared) EGL context
 * used by the whole pipeline. Based on the well-known Grafika `InputSurface` pattern.
 */
class InputSurface(private val surface: Surface) {

    private var eglDisplay: EGLDisplay = EGL14.EGL_NO_DISPLAY
    private var eglContext: EGLContext = EGL14.EGL_NO_CONTEXT
    private var eglSurface: EGLSurface = EGL14.EGL_NO_SURFACE

    init {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
        check(eglDisplay != EGL14.EGL_NO_DISPLAY) { "Unable to get EGL14 display" }
        val version = IntArray(2)
        check(EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) { "Unable to initialize EGL14" }

        val attribList = intArrayOf(
            EGL14.EGL_RED_SIZE, 8,
            EGL14.EGL_GREEN_SIZE, 8,
            EGL14.EGL_BLUE_SIZE, 8,
            EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
            EGL_RECORDABLE_ANDROID, 1,
            EGL14.EGL_NONE,
        )
        val configs = arrayOfNulls<EGLConfig>(1)
        val numConfigs = IntArray(1)
        check(
            EGL14.eglChooseConfig(eglDisplay, attribList, 0, configs, 0, configs.size, numConfigs, 0),
        ) { "Unable to find a suitable EGLConfig" }

        val contextAttribs = intArrayOf(EGL14.EGL_CONTEXT_CLIENT_VERSION, 2, EGL14.EGL_NONE)
        eglContext = EGL14.eglCreateContext(
            eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, contextAttribs, 0,
        )
        checkEglError("eglCreateContext")

        val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
        eglSurface = EGL14.eglCreateWindowSurface(eglDisplay, configs[0], surface, surfaceAttribs, 0)
        checkEglError("eglCreateWindowSurface")
    }

    fun makeCurrent() {
        check(EGL14.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) { "eglMakeCurrent failed" }
    }

    fun swapBuffers(): Boolean = EGL14.eglSwapBuffers(eglDisplay, eglSurface)

    fun setPresentationTime(nsecs: Long) {
        EGLExt.eglPresentationTimeANDROID(eglDisplay, eglSurface, nsecs)
    }

    fun release() {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            EGL14.eglMakeCurrent(eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT)
            EGL14.eglDestroySurface(eglDisplay, eglSurface)
            EGL14.eglDestroyContext(eglDisplay, eglContext)
            EGL14.eglReleaseThread()
            EGL14.eglTerminate(eglDisplay)
        }
        surface.release()
        eglDisplay = EGL14.EGL_NO_DISPLAY
        eglContext = EGL14.EGL_NO_CONTEXT
        eglSurface = EGL14.EGL_NO_SURFACE
    }

    private fun checkEglError(op: String) {
        val error = EGL14.eglGetError()
        check(error == EGL14.EGL_SUCCESS) { "$op: EGL error 0x${Integer.toHexString(error)}" }
    }

    companion object {
        private const val EGL_RECORDABLE_ANDROID = 0x3142
    }
}

/**
 * Receives decoded frames from the video decoder via a [SurfaceTexture] and exposes a [Surface] the
 * decoder renders into. Must be constructed while the [InputSurface] EGL context is current, because
 * it allocates the external OES texture in that context.
 */
class OutputSurface(private val renderer: FrameRenderer) : SurfaceTexture.OnFrameAvailableListener {

    val surface: Surface
    private val surfaceTexture: SurfaceTexture = SurfaceTexture(renderer.textureId)
    private val frameSyncObject = Object()
    private var frameAvailable = false

    init {
        surfaceTexture.setOnFrameAvailableListener(this)
        surface = Surface(surfaceTexture)
    }

    /** Blocks until the decoder has produced a new frame (or times out). */
    fun awaitNewImage() {
        synchronized(frameSyncObject) {
            val deadline = System.currentTimeMillis() + FRAME_TIMEOUT_MS
            while (!frameAvailable) {
                val remaining = deadline - System.currentTimeMillis()
                if (remaining <= 0) error("Timed out waiting for a decoded frame")
                frameSyncObject.wait(remaining)
            }
            frameAvailable = false
        }
        surfaceTexture.updateTexImage()
    }

    /** Renders the most recent frame onto the currently-bound (encoder) surface. */
    fun drawImage() = renderer.drawFrame(surfaceTexture)

    override fun onFrameAvailable(st: SurfaceTexture) {
        synchronized(frameSyncObject) {
            check(!frameAvailable) { "onFrameAvailable called before previous frame was consumed" }
            frameAvailable = true
            frameSyncObject.notifyAll()
        }
    }

    fun release() {
        surface.release()
        surfaceTexture.release()
    }

    companion object {
        private const val FRAME_TIMEOUT_MS = 10_000L
    }
}

/**
 * Draws a full-screen quad sampling the decoder's external OES texture, applying an unsharp-mask
 * sharpen plus contrast/saturation grading in the fragment shader.
 */
class FrameRenderer(
    private val sharpen: Float,
    private val saturation: Float,
    private val contrast: Float,
    private val sourceWidth: Int,
    private val sourceHeight: Int,
    private val outputWidth: Int,
    private val outputHeight: Int,
) {
    var textureId: Int = -1
        private set

    private var program = 0
    private var aPositionLoc = 0
    private var aTextureCoordLoc = 0
    private var uTexMatrixLoc = 0
    private var uTexelSizeLoc = 0
    private var uSharpenLoc = 0
    private var uSaturationLoc = 0
    private var uContrastLoc = 0

    private val texMatrix = FloatArray(16)

    private val quad: FloatBuffer = ByteBuffer
        .allocateDirect(QUAD.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply { put(QUAD); position(0) }

    init {
        program = buildProgram(VERTEX_SHADER, FRAGMENT_SHADER)
        aPositionLoc = GLES20.glGetAttribLocation(program, "aPosition")
        aTextureCoordLoc = GLES20.glGetAttribLocation(program, "aTextureCoord")
        uTexMatrixLoc = GLES20.glGetUniformLocation(program, "uTexMatrix")
        uTexelSizeLoc = GLES20.glGetUniformLocation(program, "uTexelSize")
        uSharpenLoc = GLES20.glGetUniformLocation(program, "uSharpen")
        uSaturationLoc = GLES20.glGetUniformLocation(program, "uSaturation")
        uContrastLoc = GLES20.glGetUniformLocation(program, "uContrast")

        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }

    fun drawFrame(st: SurfaceTexture) {
        st.getTransformMatrix(texMatrix)

        GLES20.glViewport(0, 0, outputWidth, outputHeight)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)

        quad.position(0)
        GLES20.glEnableVertexAttribArray(aPositionLoc)
        GLES20.glVertexAttribPointer(aPositionLoc, 2, GLES20.GL_FLOAT, false, STRIDE, quad)

        quad.position(2)
        GLES20.glEnableVertexAttribArray(aTextureCoordLoc)
        GLES20.glVertexAttribPointer(aTextureCoordLoc, 2, GLES20.GL_FLOAT, false, STRIDE, quad)

        GLES20.glUniformMatrix4fv(uTexMatrixLoc, 1, false, texMatrix, 0)
        GLES20.glUniform2f(uTexelSizeLoc, 1f / sourceWidth, 1f / sourceHeight)
        GLES20.glUniform1f(uSharpenLoc, sharpen)
        GLES20.glUniform1f(uSaturationLoc, saturation)
        GLES20.glUniform1f(uContrastLoc, contrast)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(aPositionLoc)
        GLES20.glDisableVertexAttribArray(aTextureCoordLoc)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
    }

    private fun buildProgram(vertexSrc: String, fragmentSrc: String): Int {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vertexSrc)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSrc)
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vs)
        GLES20.glAttachShader(prog, fs)
        GLES20.glLinkProgram(prog)
        val linked = IntArray(1)
        GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, linked, 0)
        check(linked[0] == GLES20.GL_TRUE) { "Program link failed: ${GLES20.glGetProgramInfoLog(prog)}" }
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)
        return prog
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, src)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        check(compiled[0] == GLES20.GL_TRUE) { "Shader compile failed: ${GLES20.glGetShaderInfoLog(shader)}" }
        return shader
    }

    companion object {
        private const val STRIDE = 4 * 4 // 4 floats per vertex

        // x, y, u, v — full-screen triangle strip.
        private val QUAD = floatArrayOf(
            -1f, -1f, 0f, 0f,
            1f, -1f, 1f, 0f,
            -1f, 1f, 0f, 1f,
            1f, 1f, 1f, 1f,
        )

        private const val VERTEX_SHADER = """
            uniform mat4 uTexMatrix;
            attribute vec4 aPosition;
            attribute vec4 aTextureCoord;
            varying vec2 vTextureCoord;
            void main() {
                gl_Position = aPosition;
                vTextureCoord = (uTexMatrix * aTextureCoord).xy;
            }
        """

        // Unsharp-mask sharpen (3x3 cross kernel) + contrast + saturation grading.
        private const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform samplerExternalOES sTexture;
            uniform vec2 uTexelSize;
            uniform float uSharpen;
            uniform float uSaturation;
            uniform float uContrast;
            void main() {
                vec3 center = texture2D(sTexture, vTextureCoord).rgb;
                vec3 up    = texture2D(sTexture, vTextureCoord + vec2(0.0,  uTexelSize.y)).rgb;
                vec3 down  = texture2D(sTexture, vTextureCoord + vec2(0.0, -uTexelSize.y)).rgb;
                vec3 left  = texture2D(sTexture, vTextureCoord + vec2(-uTexelSize.x, 0.0)).rgb;
                vec3 right = texture2D(sTexture, vTextureCoord + vec2( uTexelSize.x, 0.0)).rgb;

                vec3 color = center * (1.0 + 4.0 * uSharpen) - (up + down + left + right) * uSharpen;

                // Contrast around mid-grey.
                color = (color - 0.5) * uContrast + 0.5;

                // Saturation relative to luma.
                float luma = dot(color, vec3(0.299, 0.587, 0.114));
                color = mix(vec3(luma), color, uSaturation);

                gl_FragColor = vec4(clamp(color, 0.0, 1.0), 1.0);
            }
        """
    }
}

/**
 * Draws an upscaled [Bitmap] (produced by the ML super-resolution pass) onto the encoder's input
 * surface via a plain 2D texture. The bitmap is stretched across the whole output viewport, so the
 * encoder must be configured at the bitmap's dimensions (rounded to even) to avoid distortion.
 */
class BitmapRenderer(private val outputWidth: Int, private val outputHeight: Int) {

    private val program: Int
    private val aPositionLoc: Int
    private val aTextureCoordLoc: Int
    private val textureId: Int

    private val quad: FloatBuffer = ByteBuffer
        .allocateDirect(QUAD.size * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply { put(QUAD); position(0) }

    init {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        program = GLES20.glCreateProgram()
        GLES20.glAttachShader(program, vs)
        GLES20.glAttachShader(program, fs)
        GLES20.glLinkProgram(program)
        val linked = IntArray(1)
        GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linked, 0)
        check(linked[0] == GLES20.GL_TRUE) { "Program link failed: ${GLES20.glGetProgramInfoLog(program)}" }
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)

        aPositionLoc = GLES20.glGetAttribLocation(program, "aPosition")
        aTextureCoordLoc = GLES20.glGetAttribLocation(program, "aTextureCoord")

        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        textureId = textures[0]
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
    }

    fun draw(bitmap: Bitmap) {
        GLES20.glViewport(0, 0, outputWidth, outputHeight)
        GLES20.glClearColor(0f, 0f, 0f, 1f)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0)

        quad.position(0)
        GLES20.glEnableVertexAttribArray(aPositionLoc)
        GLES20.glVertexAttribPointer(aPositionLoc, 2, GLES20.GL_FLOAT, false, STRIDE, quad)

        quad.position(2)
        GLES20.glEnableVertexAttribArray(aTextureCoordLoc)
        GLES20.glVertexAttribPointer(aTextureCoordLoc, 2, GLES20.GL_FLOAT, false, STRIDE, quad)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        GLES20.glDisableVertexAttribArray(aPositionLoc)
        GLES20.glDisableVertexAttribArray(aTextureCoordLoc)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
    }

    private fun compileShader(type: Int, src: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, src)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        check(compiled[0] == GLES20.GL_TRUE) { "Shader compile failed: ${GLES20.glGetShaderInfoLog(shader)}" }
        return shader
    }

    companion object {
        private const val STRIDE = 4 * 4

        // x, y, u, v — full-screen triangle strip. The V coordinate is flipped because Android
        // bitmaps are top-left origin whereas GL samples bottom-left.
        private val QUAD = floatArrayOf(
            -1f, -1f, 0f, 1f,
            1f, -1f, 1f, 1f,
            -1f, 1f, 0f, 0f,
            1f, 1f, 1f, 0f,
        )

        private const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec4 aTextureCoord;
            varying vec2 vTextureCoord;
            void main() {
                gl_Position = aPosition;
                vTextureCoord = aTextureCoord.xy;
            }
        """

        private const val FRAGMENT_SHADER = """
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform sampler2D sTexture;
            void main() {
                gl_FragColor = texture2D(sTexture, vTextureCoord);
            }
        """
    }
}
