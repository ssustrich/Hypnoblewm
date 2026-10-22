package com.example.hypnobloom

import android.os.Handler
import android.os.HandlerThread
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.SurfaceHolder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext
import javax.microedition.khronos.egl.EGLDisplay
import javax.microedition.khronos.egl.EGLSurface

class HypnoWallpaperService : WallpaperService() {
    override fun onCreateEngine(): Engine = BloomEngine()

    inner class BloomEngine : Engine() {
        private val worker = HandlerThread("HypnoBloom-GL").also { it.start() }
        private val handler = Handler(worker.looper)
        private var visible = false
        private var ready = false
        private var width = 1
        private var height = 1
        private var renderer: BloomRenderer? = null
        private val analyzer = AudioAnalyzer(applicationContext)
        private var egl: EGL10? = null
        private var display: EGLDisplay? = null
        private var surface: EGLSurface? = null
        private var context: EGLContext? = null

        private val frame = object : Runnable {
            override fun run() {
                if (!visible || !ready) return
                try {
                    renderer?.draw(width, height, analyzer.level())
                    if (egl?.eglSwapBuffers(display, surface) != true) {
                        Log.w("HypnoBloom", "eglSwapBuffers failed")
                        ready = false
                        analyzer.stop()
                        return
                    }
                } catch (e: Exception) {
                    Log.e("HypnoBloom", "Render failed", e)
                    ready = false
                    analyzer.stop()
                    return
                }
                handler.postDelayed(this, 33L)
            }
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            handler.post {
                tearDown()
                try {
                    initialize(holder)
                    ready = true
                    updateRunning()
                } catch (e: Exception) {
                    Log.e("HypnoBloom", "GL initialization failed", e)
                    tearDown()
                }
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, w: Int, h: Int) {
            super.onSurfaceChanged(holder, format, w, h)
            handler.post { width = w.coerceAtLeast(1); height = h.coerceAtLeast(1) }
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            super.onVisibilityChanged(isVisible)
            handler.post { visible = isVisible; updateRunning() }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            // Make sure EGL releases the window before the platform destroys it.
            val latch = CountDownLatch(1)
            handler.post { tearDown(); latch.countDown() }
            latch.await(2, TimeUnit.SECONDS)
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            val latch = CountDownLatch(1)
            handler.post { tearDown(); latch.countDown() }
            latch.await(2, TimeUnit.SECONDS)
            worker.quitSafely()
            super.onDestroy()
        }

        private fun updateRunning() {
            handler.removeCallbacks(frame)
            if (visible && ready) {
                analyzer.start()
                handler.post(frame)
            } else {
                analyzer.stop()
            }
        }

        private fun initialize(holder: SurfaceHolder) {
            val e = EGLContext.getEGL() as EGL10
            val d = e.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY)
            check(d != EGL10.EGL_NO_DISPLAY) { "No EGL display" }
            check(e.eglInitialize(d, IntArray(2))) { "EGL initialize failed" }
            egl = e
            display = d
            val attrs = intArrayOf(
                EGL10.EGL_RED_SIZE, 8, EGL10.EGL_GREEN_SIZE, 8,
                EGL10.EGL_BLUE_SIZE, 8, EGL10.EGL_ALPHA_SIZE, 8,
                EGL10.EGL_RENDERABLE_TYPE, 4, // OpenGL ES 2.0
                EGL10.EGL_SURFACE_TYPE, EGL10.EGL_WINDOW_BIT,
                EGL10.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val count = IntArray(1)
            check(e.eglChooseConfig(d, attrs, configs, 1, count) && count[0] > 0) {
                "No compatible EGL config"
            }
            val config = configs[0]!!
            val c = e.eglCreateContext(d, config, EGL10.EGL_NO_CONTEXT,
                intArrayOf(0x3098, 2, EGL10.EGL_NONE)) // EGL_CONTEXT_CLIENT_VERSION
            check(c != EGL10.EGL_NO_CONTEXT) { "Could not create GL context" }
            context = c
            val s = e.eglCreateWindowSurface(d, config, holder, null)
            check(s != EGL10.EGL_NO_SURFACE) { "Could not create GL surface" }
            surface = s
            check(e.eglMakeCurrent(d, s, s, c)) { "eglMakeCurrent failed" }
            renderer = BloomRenderer().also { it.create() }
        }

        private fun tearDown() {
            handler.removeCallbacks(frame)
            analyzer.stop()
            ready = false
            val e = egl
            val d = display
            if (e != null && d != null) {
                // Delete GL resources while the context remains current.
                renderer?.destroy()
                renderer = null
                e.eglMakeCurrent(d, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT)
                surface?.let { e.eglDestroySurface(d, it) }
                context?.let { e.eglDestroyContext(d, it) }
                e.eglTerminate(d)
            }
            surface = null
            context = null
            display = null
            egl = null
        }
    }
}