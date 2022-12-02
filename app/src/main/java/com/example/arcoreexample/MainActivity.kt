package com.example.arcoreexample

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.opengl.GLES11Ext
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import android.os.Bundle
import android.util.Log
import android.view.Surface
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import com.google.ar.core.*
import com.google.ar.core.exceptions.*
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.*
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class MainActivity : AppCompatActivity() {

    private val textures: IntArray = intArrayOf(1)

    private var session: Session? = null

    private var installRequested: Boolean = false

    private var cameraProgram: Int = 0

    private var cameraPositionAttrib: Int = 0

    private var cameraTexCoordAttrib: Int = 0

    private var cameraTextureUniform: Int = 0

    private var pointsProgram: Int = 0

    private var pointsPositionAttrib: Int = 0

    private var pointColorAttrib: Int = 0

    private var quadCoords: FloatArray = floatArrayOf(-1.0f, -1.0f, +1.0f, -1.0f, -1.0f, +1.0f, +1.0f, +1.0f)

    private var quadTexCoords: FloatArray = floatArrayOf(0.0f, 1.0f, 0.0f, 0.0f, 1.0f, 1.0f, 1.0f, 0.0f)

    private lateinit var surfaceView: GLSurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        surfaceView = findViewById(R.id.surfaceView)

        surfaceView.preserveEGLContextOnPause = true
        surfaceView.setEGLContextClientVersion(2)
        surfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        surfaceView.setRenderer(renderer)
        surfaceView.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        surfaceView.setWillNotDraw(false)
    }

    override fun onResume() {
        super.onResume()

        session ?: run {
            var exception: Exception? = null
            var message: String? = null
            try {
                when (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                    ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                        installRequested = true
                        return
                    }
                    else -> {
                        if (checkSelfPermission(Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
                            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), 0)
                            return
                        }

                        session = Session(this, EnumSet.noneOf(Session.Feature::class.java))
                        val cameraConfigFilter = CameraConfigFilter(session)
                                                    .setFacingDirection(CameraConfig.FacingDirection.FRONT)
                        val cameraConfigs = session!!.getSupportedCameraConfigs(cameraConfigFilter)
                        if (cameraConfigs.isNotEmpty()) {
                            session!!.cameraConfig = cameraConfigs[0]
                            session!!.configure(Config(session!!)
                                                    .setAugmentedFaceMode(Config.AugmentedFaceMode.MESH3D))
                        } else {
                            message = "This device does not have a front-facing (selfie) camera"
                            exception = UnavailableDeviceNotCompatibleException(message)
                        }
                    }
                }
            } catch (e: Exception) {
                when (e) {
                    is UnavailableArcoreNotInstalledException, is UnavailableUserDeclinedInstallationException -> {
                        message = "Please install ARCore"
                        exception = e
                    }
                    is UnavailableApkTooOldException -> {
                        message = "Please update ARCore"
                        exception = e
                    }
                    is UnavailableSdkTooOldException -> {
                        message = "Please update this app"
                        exception = e
                    }
                    is UnavailableDeviceNotCompatibleException -> {
                        message = "This device does not support AR"
                        exception = e
                    }
                    else -> {
                        message = "Failed to create AR session"
                        exception = e
                    }
                }
            }

            exception?.run {
                Toast.makeText(this@MainActivity, message!!, Toast.LENGTH_LONG).show()
                return
            } ?: run {
                try {
                    session!!.resume()
                    surfaceView.onResume()
                } catch (e: CameraNotAvailableException) {
                    Toast.makeText(this@MainActivity, "Camera not available. Try restarting the app.", Toast.LENGTH_LONG).show()
                    session = null
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()

        session?.run {
            surfaceView.onPause()
            session!!.pause()
        }
    }

    override fun onDestroy() {
        session?.run {
            session!!.close()
            session = null
        }

        super.onDestroy()
    }

    private val renderer = object : GLSurfaceView.Renderer {
        private val TAG: String = "GLSurfaceView.Renderer"

        override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
            GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f)

            try {
                cameraProgram = GLES20.glCreateProgram().also {
                    val vs = loadGLShader(this@MainActivity, GLES20.GL_VERTEX_SHADER, "shaders/screenquad.vert")
                    val fs = loadGLShader(this@MainActivity, GLES20.GL_FRAGMENT_SHADER, "shaders/screenquad.frag")

                    GLES20.glAttachShader(it, vs)
                    GLES20.glAttachShader(it, fs)
                    GLES20.glLinkProgram(it)
                    GLES20.glUseProgram(it)

                    cameraPositionAttrib = GLES20.glGetAttribLocation(it, "a_Position")
                    cameraTexCoordAttrib = GLES20.glGetAttribLocation(it, "a_TexCoord")
                    cameraTextureUniform = GLES20.glGetUniformLocation(it, "sTexture")

                    GLES20.glGenTextures(1, textures, 0)
                    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0])
                    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
                    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
                    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
                    GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
                }

                // Points shader
                pointsProgram = GLES20.glCreateProgram().also {
                    val vs = loadGLShader(this@MainActivity, GLES20.GL_VERTEX_SHADER, "shaders/coordinates.vert")
                    val fs = loadGLShader(this@MainActivity, GLES20.GL_FRAGMENT_SHADER, "shaders/coordinates.frag")

                    GLES20.glAttachShader(it, vs)
                    GLES20.glAttachShader(it, fs)
                    GLES20.glLinkProgram(it)
                    GLES20.glUseProgram(it)

                    pointsPositionAttrib = GLES20.glGetAttribLocation(it, "a_Position")
                    pointColorAttrib = GLES20.glGetAttribLocation(it, "a_FragColor")
                }
            } catch (e: IOException) {
                Log.e(TAG, "Failed to read an asset file", e)
            }
        }

        override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
            session!!.setDisplayGeometry(Surface.ROTATION_0, width, height)
            GLES20.glViewport(0, 0, width, height)
        }

        override fun onDrawFrame(gl: GL10?) {
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

            session ?: return

            try {
                session!!.setCameraTextureName(textures[0])

                val frame = session!!.update()
                val camera = frame.camera
                val projectionMatrix = FloatArray(16)
                val viewMatrix = FloatArray(16)

                camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100.0f)
                camera.getViewMatrix(viewMatrix, 0)

                // Draw texture
                GLES20.glUseProgram(cameraProgram).also {
                    if (frame.hasDisplayGeometryChanged()) {
                        frame.transformCoordinates2d(Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,
                                                     quadCoords,
                                                     Coordinates2d.TEXTURE_NORMALIZED,
                                                     quadTexCoords)
                    }

                    GLES20.glDepthMask(false)
                    GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
                    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textures[0])
                    GLES20.glUniform1i(cameraTextureUniform, 0)
                    GLES20.glEnableVertexAttribArray(cameraPositionAttrib)
                    GLES20.glEnableVertexAttribArray(cameraTexCoordAttrib)
                    GLES20.glVertexAttribPointer(cameraPositionAttrib, 2, GLES20.GL_FLOAT, false, 0, makeFloatBuffer(quadCoords))
                    GLES20.glVertexAttribPointer(cameraTexCoordAttrib, 2, GLES20.GL_FLOAT, false, 0, makeFloatBuffer(quadTexCoords))
                    GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
                    GLES20.glDisableVertexAttribArray(cameraPositionAttrib)
                    GLES20.glDisableVertexAttribArray(cameraTexCoordAttrib)
                    GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, 0)
                    GLES20.glUseProgram(0)
                }

                // Draw faces
                val faces = session!!.getAllTrackables(AugmentedFace::class.java)

                GLES20.glEnableVertexAttribArray(pointsPositionAttrib)
                GLES20.glEnableVertexAttribArray(pointColorAttrib)

                for (face in faces) {
                    if (face.trackingState == TrackingState.TRACKING) {
                        GLES20.glUseProgram(pointsProgram).also {
                            val faceMeshCount = face.meshVertices.limit() / 3;
                            val vertices = face.meshVertices
                            var modelMatrix = FloatArray(16)
                            face.centerPose.toMatrix(modelMatrix, 0)

                            GLES20.glDepthMask(false)
                            GLES20.glEnableVertexAttribArray(pointsPositionAttrib)
                            GLES20.glUniform3f(GLES20.glGetUniformLocation(pointsProgram, "u_Translation"), face.centerPose.tx(), face.centerPose.ty(), face.centerPose.tz())
                            GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(pointsProgram, "u_Model"), 1, false, modelMatrix, 0)
                            GLES20.glVertexAttribPointer(pointsPositionAttrib, 3, GLES20.GL_FLOAT, false, 0, vertices)
                            GLES20.glDrawArrays(GLES20.GL_POINTS, 0, faceMeshCount)
                            GLES20.glDisableVertexAttribArray(pointsPositionAttrib)
                            GLES20.glUseProgram(0)
                        }
                    }
                }


//                GLES20.glUseProgram(pointsProgram).also {
//                    val faces = session!!.getAllTrackables(AugmentedFace::class.java)
//
//                    GLES20.glEnableVertexAttribArray(pointsPositionAttrib)
//                    GLES20.glEnableVertexAttribArray(pointColorAttrib)
//
//                    for (face in faces) {
//                        if (face.trackingState == TrackingState.TRACKING) {
//                            val faceMeshCount = face.meshVertices.limit() / 3;
//                            val vertices = face.meshVertices
//                            val lightDirection = floatArrayOf(0.0f, 1.0f, 0.0f, 0.0f)
//                            var modelMatrix = FloatArray(16)
//                            var modelViewMatrix = FloatArray(16)
//                            var modelViewProjectionMatrix = FloatArray(16)
//                            var tmpMatrix = FloatArray(16)
//                            var viewLightDirection = FloatArray(4)
//
//                            face.centerPose.toMatrix(modelMatrix, 0)
//                            Matrix.multiplyMM(tmpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
//                            Matrix.multiplyMM(modelViewProjectionMatrix, 0, tmpMatrix, 0, modelMatrix, 0)
//                            Matrix.multiplyMM(modelViewMatrix, 0, viewMatrix, 0, modelMatrix, 0)
//                            Matrix.multiplyMV(viewLightDirection, 0, modelViewMatrix, 0, lightDirection, 0)
//                            normalizeVec3(viewLightDirection)
//
//                            GLES20.glDepthMask(false)
//                            GLES20.glUniform3f(GLES20.glGetUniformLocation(pointsProgram, "u_Translation"), face.centerPose.tx(), face.centerPose.ty(), face.centerPose.tz())
//                            GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(pointsProgram, "u_Model"), 1, false, modelViewMatrix, 0)
//                            GLES20.glVertexAttribPointer(pointsPositionAttrib, 3, GLES20.GL_FLOAT, false, 0, vertices)
//                            GLES20.glDrawArrays(GLES20.GL_POINTS, 0, faceMeshCount)
//                            GLES20.glDisableVertexAttribArray(pointsPositionAttrib)
//                            GLES20.glUseProgram(0)
//                        }
//                    }
//                }
            } catch (t: Throwable) {
                Log.e(TAG, "Exception on the OpenGL thread", t)
            } finally {
                GLES20.glDepthMask(true)
            }
        }
    }

    companion object {
        @Throws(IOException::class)
        private fun loadGLShader(context: Context, type: Int, filename: String): Int {
            var code: String
            context.assets.open(filename).use { inputStream ->
                code = inputStream.bufferedReader().use { it.readText() }
            }

            var shader = GLES20.glCreateShader(type)
            GLES20.glShaderSource(shader, code)
            GLES20.glCompileShader(shader)

            val compileStatus = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0)

            if (compileStatus[0] == 0) {
                Log.e("loadGLShader", "Error compiling shader: " + GLES20.glGetShaderInfoLog(shader))
                GLES20.glDeleteShader(shader)
                shader = 0
            }
            if (shader == 0) {
                throw RuntimeException("Error creating shader.")
            }
            return shader
        }

        private fun makeFloatBuffer(arr: FloatArray): FloatBuffer {
            val bb = ByteBuffer.allocateDirect(arr.size * 4)
            bb.order(ByteOrder.nativeOrder())
            return bb.asFloatBuffer().apply {
                put(arr)
                position(0)
            }
        }

        private fun normalizeVec3(v: FloatArray) {
            val reciprocalLength = 1.0f / Math.sqrt((v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).toDouble()).toFloat()
            v[0] *= reciprocalLength
            v[1] *= reciprocalLength
            v[2] *= reciprocalLength
        }
    }

}