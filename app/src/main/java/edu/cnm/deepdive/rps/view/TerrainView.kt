/*
 *  Copyright 2020 Deep Dive Coding/CNM Ingenuity
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package edu.cnm.deepdive.rps.view

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.util.AttributeSet
import android.view.View
import edu.cnm.deepdive.rps.model.Arena

/**
 * [View] subclass that renders the cells on the terrain of an [Arena], using a scaled
 * one-pixel-per-cell scheme.
 *
 * @author Nicholas Bennett
 */
class TerrainView : View {

    private val source = Rect()
    private val dest = Rect()
    private var bitmap: Bitmap? = null
    private var arena: Arena? = null
    private var terrain: Array<ByteArray>? = null;
    private var breedColors: IntArray? = null;
    private var updater: Updater? = null

    /**
     * Initializes by chaining to [View.View].
     */
    constructor(context: Context?) : super(context) {}

    /**
     * Initializes by chaining to [View.View].
     */
    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {}

    /**
     * Initializes by chaining to [View.View].
     */
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {}

    /**
     * Initializes by chaining to [View.View].
     */
    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {}

    /**
     * Returns dimensions based on the larger of this view's suggested height and width, so that the
     * content is square. For this to be the appropriate choice, this view should be contained within
     * a [android.widget.ScrollView], with its width set to `match_parent` and its height
     * set to `wrap_content`; or within a [android.widget.HorizontalScrollView], with its
     * width set to `wrap_content` and its height set to `match_parent`.
     *
     * @param widthMeasureSpec specification control value.
     * @param heightMeasureSpec specification control value.
     */
    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        var width = suggestedMinimumWidth
        var height = suggestedMinimumHeight
        width = resolveSizeAndState(paddingLeft + paddingRight + width, widthMeasureSpec, 0)
        height = resolveSizeAndState(paddingTop + paddingBottom + height, heightMeasureSpec, 0)
        val size = Math.max(width, height)
        setMeasuredDimension(size, size)
    }

    /**
     * Performs layout on all child elements (none), and creates a [Bitmap] to fit the specified
     * dimensions.
     */
    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        updateBitmap()
    }

    /**
     * Renders the contents of the [Arena&#39;s][Arena] terrain.
     *
     * @param canvas rendering target.
     */
    override fun onDraw(canvas: Canvas) {
        bitmap?.let { bits ->
            dest[0, 0, width] = height
            canvas.drawBitmap(bits, source, dest, null)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        stopUpdater()
        updater = Updater().apply {
            start()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        stopUpdater()
    }

    /**
     * Specifices the [Arena] instance to be rendered by this view. In general, this will most
     * simply be invoked via data binding in the layout XML.
     *
     * @param arena instance to render.
     */
    fun setArena(arena: Arena?) {
        this.arena = arena
        bitmap = null
        arena?.let {
            val numBreeds = it.numBreeds
            val size = it.arenaSize
            terrain = Array(size) { ByteArray(size) }
            bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
            source[0, 0, size] = size
            val hsv = floatArrayOf(0f, SATURATION, BRIGHTNESS)
            val hueInterval = MAX_HUE / numBreeds
            breedColors = IntArray(numBreeds.toInt()).also { colors ->
                for (i in colors.indices) {
                    colors[i] = Color.HSVToColor(hsv)
                    hsv[0] += hueInterval
                }
            }
        }
    }

    /**
     * Updates the current generation count, triggering a display refresh. Without invoking this
     * method, the cell terrain rendering will not be updated; however, if data binding is used in the
     * layout XML, this can happen automatically.
     *
     * @param generation number of generations (iterations) completed in the [Arena] simulation.
     */
    fun setGeneration(generation: Long) {
        updater?.setGeneration(generation)
    }

    private fun updateBitmap() {
        bitmap?.let { bits ->
            arena?.copyTerrain(terrain)
            terrain?.let { terr ->
                breedColors?.let { colors ->
                    for (row in terr.indices) {
                        for (col in terr[row].indices) {
                            bits.setPixel(col, row, colors[terr[row][col].toInt()])
                        }
                    }
                }
            }
        }
    }

    private fun stopUpdater() {
        if (updater != null) {
            updater!!.setRunning(false)
            updater = null
        }
    }

    private inner class Updater : Thread() {
        @Volatile
        private var running = true

        @Volatile
        private var generation: Long = 0
        fun setRunning(running: Boolean) {
            this.running = running
        }

        fun setGeneration(generation: Long) {
            this.generation = generation
        }

        override fun run() {
            var generation: Long = 0
            while (running) {
                var sleepInterval = INACTIVE_SLEEP_INTERVAL
                if (this.generation == 0L || this.generation > generation) {
                    generation = this.generation
                    updateBitmap()
                    if (generation == 0L) {
                        postInvalidate()
                    }
                    sleepInterval = ACTIVE_SLEEP_INTERVAL
                }
                try {
                    sleep(sleepInterval)
                } catch (expected: InterruptedException) {
                    // Ignore innocuous exception.
                }
            }
        }
    }

    companion object {
        private const val MAX_HUE = 360f
        private const val SATURATION = 1f
        private const val BRIGHTNESS = 0.85f
        private const val ACTIVE_SLEEP_INTERVAL: Long = 10
        private const val INACTIVE_SLEEP_INTERVAL: Long = 100
    }

    init {
        setWillNotDraw(false)
    }
}