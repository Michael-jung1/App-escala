package com.aistudio.escala.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.escala.ui.theme.EscalaPrimary
import com.aistudio.escala.ui.theme.EscalaPrimaryContainer
import java.time.LocalDate
import java.time.YearMonth

private val NOMES_MESES = listOf(
    "Janeiro", "Fevereiro", "Março", "Abril", "Maio", "Junho",
    "Julho", "Agosto", "Setembro", "Outubro", "Novembro", "Dezembro"
)

private val DIAS_SEMANA = listOf("D", "S", "T", "Q", "Q", "S", "S")

@Composable
fun CalendarioMensal(
    serviceDates: Set<LocalDate>,
    selectedDate: LocalDate?,
    onSelectDate: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    initialYearMonth: YearMonth = YearMonth.now(),
    onMonthChanged: (YearMonth) -> Unit = {}
) {
    var currentYearMonth by remember(initialYearMonth) { mutableStateOf(initialYearMonth) }
    val today = remember { LocalDate.now() }

    LaunchedEffect(currentYearMonth) {
        onMonthChanged(currentYearMonth)
    }

    LaunchedEffect(selectedDate) {
        if (selectedDate != null) {
            val selectedYm = YearMonth.from(selectedDate)
            if (selectedYm != currentYearMonth) {
                currentYearMonth = selectedYm
            }
        }
    }

    val daysInMonth = currentYearMonth.lengthOfMonth()
    val firstDayOfMonth = currentYearMonth.atDay(1)
    // Day of week in Java: 1 (Mon) to 7 (Sun). We want 0 (Sun) to 6 (Sat)
    val startDayOffset = firstDayOfMonth.dayOfWeek.value % 7

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(12.dp))
            .padding(16.dp)
    ) {
        Column {
            // Header with Month Name and Navigation Arrows
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = { currentYearMonth = currentYearMonth.minusMonths(1) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                        contentDescription = "Mês anterior",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = "${NOMES_MESES[currentYearMonth.monthValue - 1]} de ${currentYearMonth.year}",
                    fontFamily = FontFamily.Serif,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )

                IconButton(
                    onClick = { currentYearMonth = currentYearMonth.plusMonths(1) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                        contentDescription = "Próximo mês",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Days of the week headers
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                DIAS_SEMANA.forEach { dia ->
                    Text(
                        text = dia,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Days grid (Rows of 7)
            val totalCells = startDayOffset + daysInMonth
            val totalRows = (totalCells + 6) / 7

            for (row in 0 until totalRows) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    for (col in 0..6) {
                        val cellIndex = row * 7 + col
                        val dayNumber = cellIndex - startDayOffset + 1

                        if (dayNumber in 1..daysInMonth) {
                            val cellDate = currentYearMonth.atDay(dayNumber)
                            val isSelected = selectedDate == cellDate
                            val hasService = serviceDates.contains(cellDate)
                            val isToday = today == cellDate
                            val isPast = cellDate.isBefore(today)

                            val bgColor = when {
                                isSelected -> if (isPast) MaterialTheme.colorScheme.primary.copy(alpha = 0.75f) else MaterialTheme.colorScheme.primary
                                hasService -> if (isPast) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f) else MaterialTheme.colorScheme.primaryContainer
                                else -> Color.Transparent
                            }

                            val textColor = when {
                                isSelected -> MaterialTheme.colorScheme.onPrimary
                                hasService -> if (isPast) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onPrimaryContainer
                                isPast -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                else -> MaterialTheme.colorScheme.onSurface
                            }

                            val borderModifier = if (isToday && !isSelected) {
                                Modifier.border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(8.dp))
                            } else {
                                Modifier
                            }

                            val cellInteractionSource = remember(cellDate) { MutableInteractionSource() }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                                    .padding(2.dp)
                                    .pressScale(cellInteractionSource)
                                    .clip(RoundedCornerShape(8.dp))
                                    .then(borderModifier)
                                    .background(bgColor)
                                    .clickable(
                                        interactionSource = cellInteractionSource,
                                        indication = null
                                    ) { onSelectDate(cellDate) },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = dayNumber.toString(),
                                    fontSize = 13.sp,
                                    fontWeight = if (hasService && !isPast || isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = textColor
                                )

                                if (hasService && !isSelected) {
                                    val dotColor = if (isPast) {
                                        MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                                    } else {
                                        MaterialTheme.colorScheme.primary
                                    }
                                    val dotShadowModifier = if (!isPast) {
                                        Modifier.shadow(
                                            elevation = 2.dp,
                                            shape = CircleShape,
                                            ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                            spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                                        )
                                    } else {
                                        Modifier
                                    }
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(bottom = 3.dp)
                                            .then(dotShadowModifier)
                                            .size(4.dp)
                                            .background(dotColor, CircleShape)
                                    )
                                }
                            }
                        } else {
                            // Empty cell
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .aspectRatio(1f)
                            )
                        }
                    }
                }
            }

            // Legend below calendar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .shadow(
                                elevation = 2.dp,
                                shape = CircleShape,
                                ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)
                            )
                            .size(8.dp)
                            .background(MaterialTheme.colorScheme.primary, CircleShape)
                    )
                    Text(
                        text = "Escala ativa",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), CircleShape)
                    )
                    Text(
                        text = "Escala passada",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.75f)
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .border(1.5.dp, MaterialTheme.colorScheme.primary, RoundedCornerShape(2.dp))
                    )
                    Text(
                        text = "Hoje",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}
