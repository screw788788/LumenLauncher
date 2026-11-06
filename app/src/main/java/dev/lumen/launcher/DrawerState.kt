package dev.lumen.launcher

import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * progress: 0 = closed, 1 = fully open.
 * The finger drives it directly while dragging; on release a spring (seeded with the
 * finger's velocity) settles it. That's what gives the "sticks to your finger" feel.
 */
@Stable
class DrawerState(private val scope: CoroutineScope) {

    var progress by mutableFloatStateOf(0f)
        private set

    /** Pixels of finger travel that map to progress 0..1. Set from the layout. */
    var travelPx: Float = 1f

    private var job: Job? = null

    /** deltaPx > 0 means the finger moved down (closing). */
    fun dragBy(deltaPx: Float) {
        job?.cancel()
        progress = (progress - deltaPx / travelPx).coerceIn(0f, 1f)
    }

    /** velocityY in px/s; negative = flicking up. */
    fun settle(velocityY: Float) {
        val target = when {
            velocityY < -700f -> 1f
            velocityY > 700f -> 0f
            abs(velocityY) <= 700f && progress > 0.5f -> 1f
            else -> 0f
        }
        animateTo(target, velocityY)
    }

    fun open() = animateTo(1f)

    fun close() = animateTo(0f)

    private fun animateTo(target: Float, velocityY: Float = 0f) {
        job?.cancel()
        job = scope.launch {
            animate(
                initialValue = progress,
                targetValue = target,
                initialVelocity = -velocityY / travelPx,
                animationSpec = spring(dampingRatio = 0.92f, stiffness = 420f),
            ) { value, _ ->
                progress = value
            }
        }
    }
}
