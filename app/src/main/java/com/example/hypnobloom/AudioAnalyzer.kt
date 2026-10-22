package com.example.hypnobloom

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.audiofx.Visualizer
import android.util.Log
import kotlin.math.sqrt

/** Lives on the wallpaper's renderer thread. Returns zero when capture is unavailable. */
class AudioAnalyzer(private val context: Context) {
    private var visualizer: Visualizer? = null
    private val fft = ByteArray(256)
    private var smoothed = 0f

    fun start() {
        if (visualizer != null) return
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return
        try {
            visualizer = Visualizer(0).also {
                it.enabled = false
                it.captureSize = 256
                it.enabled = true
            }
        } catch (e: Exception) {
            Log.w("HypnoBloom", "Output audio visualization unavailable", e)
            stop()
        }
    }

    fun level(): Float {
        val v = visualizer ?: return 0f
        try {
            if (v.getFft(fft) != Visualizer.SUCCESS) return 0f
            var energy = 0f
            // FFT pairs represent real/imaginary components. Focus on bass + lower mids.
            for (bin in 2..24) {
                val re = fft[bin * 2].toFloat()
                val im = fft[bin * 2 + 1].toFloat()
                energy += sqrt(re * re + im * im)
            }
            val normalized = (energy / (23f * 85f)).coerceIn(0f, 1f)
            smoothed = smoothed * 0.82f + normalized * 0.18f
            return smoothed
        } catch (e: Exception) {
            Log.w("HypnoBloom", "Audio read failed", e)
            stop()
            return 0f
        }
    }

    fun stop() {
        try { visualizer?.release() } catch (_: Exception) { }
        visualizer = null
        smoothed = 0f
    }
}