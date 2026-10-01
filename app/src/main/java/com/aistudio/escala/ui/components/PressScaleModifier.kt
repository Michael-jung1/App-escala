package com.aistudio.escala.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Modifier de extensão reutilizável que aplica microinteração de escala ao pressionar
 * um componente clicável (escala vai a 0.96f ao pressionar e 1f ao soltar).
 *
 * Utiliza collectIsPressedAsState() em conjunto com animateFloatAsState() e graphicsLayer.
 */
fun Modifier.pressScale(
    interactionSource: MutableInteractionSource? = null,
    targetScale: Float = 0.96f,
    durationMillis: Int = 120
): Modifier = composed {
    val actualSource = interactionSource ?: remember { MutableInteractionSource() }
    val isPressed by actualSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) targetScale else 1f,
        animationSpec = tween(durationMillis = durationMillis, easing = FastOutSlowInEasing),
        label = "press_scale_animation"
    )
    this.graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
