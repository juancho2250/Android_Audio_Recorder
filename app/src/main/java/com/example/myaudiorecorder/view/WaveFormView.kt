package com.example.myaudiorecorder.view

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.View


class WaveFormView(context: Context?, attrs: AttributeSet) : View(context, attrs) {

    private var paint = Paint()
    private var amplitudes = ArrayList<Float>()
    private var spikes = ArrayList<RectF>()
    private var radius = 2f
    private var w = 4f
    private var sw = 0f
    private var sh = 400f
    private var d = 6f
    private var maxSpikes = 0

    init {
        paint.color = Color.rgb(168, 50, 39)
        sw = resources.displayMetrics.widthPixels.toFloat()
        maxSpikes = (sw / (w + d)).toInt()
    }
    fun addAmplitud(amp: Float) {
        var norm = Math.min(amp.toInt() / 9, 400).toFloat()
        amplitudes.add(norm)
        spikes.clear()
        var amps = amplitudes.takeLast(maxSpikes)
        for (i in amps.indices) {
            var left = sw - i * (sw / maxSpikes)
            var top = sh / 2 - amps[i] / 2
            var right = left + w
            val bottom = top + amps[i]
            spikes.add(RectF(left, top, right, bottom))
        }
        invalidate()
    }
    fun clearData() {
        amplitudes.clear()
        spikes.clear()
        invalidate()
    }
    fun reset() {
        clearAnimation()
    }
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        spikes.forEach {
            canvas.drawRoundRect(it, radius, radius, paint)
        }
    }
}