package com.aistudio.escala.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import com.aistudio.escala.ui.components.pressScale
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.aistudio.escala.data.EscalacaoPessoa
import com.aistudio.escala.data.EscalaRepository
import com.aistudio.escala.ui.components.ChurchBadge
import com.aistudio.escala.ui.components.ChurchDotIndicator
import com.aistudio.escala.ui.components.PastBadge
import com.aistudio.escala.ui.components.SearchBarWithAutocomplete
import com.aistudio.escala.ui.theme.getChurchColor
import com.aistudio.escala.ui.theme.getErrorColor
import com.aistudio.escala.ui.theme.getSuccessColor
import com.aistudio.escala.util.DateUtils
import com.aistudio.escala.util.PreferencesManager
import com.aistudio.escala.util.SearchUtils
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class FiltroTempoMinhaEscala {
    FUTURAS,   // Foco inicial nas escalas presentes e futuras
    HISTORICO, // Ver escalas passadas
    TODAS      // Visão completa
}

@Composable
fun MinhaEscalaScreen(
    repository: EscalaRepository,
    preferencesManager: PreferencesManager,
    modifier: Modifier = Modifier,
    userFilter: String = "",
    onUserFilterChanged: (String) -> Unit = {},
    onNavigateToCalendar: () -> Unit = {},
    onDataUpdated: () -> Unit = {}
) {
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val today = remember { LocalDate.now() }
    val isDark = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    val successColors = getSuccessColor(isDark)
    val errorColors = getErrorColor(isDark)

    val initialName = userFilter.ifBlank { preferencesManager.nomeUsuario ?: "" }
    var searchInput by remember { mutableStateOf(initialName) }
    var currentSearchedName by remember { mutableStateOf(initialName) }
    var allPeople by remember { mutableStateOf<List<String>>(emptyList()) }

    var results by remember { mutableStateOf<List<EscalacaoPessoa>?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Rule 3: Defaults initially to future/current scales
    var filtroTempo by remember { mutableStateOf(FiltroTempoMinhaEscala.FUTURAS) }

    // Synchronize if userFilter changes externally
    LaunchedEffect(userFilter) {
        if (userFilter != currentSearchedName) {
            searchInput = userFilter
            currentSearchedName = userFilter
            if (userFilter.isNotBlank()) {
                isLoading = true
                errorMessage = null
                val found = repository.getEscalaPessoa(userFilter, apenasFuturas = false)
                if (found.isEmpty()) {
                    errorMessage = "Nenhuma escalação encontrada para \"$userFilter\"."
                    results = null
                } else {
                    results = found
                }
                isLoading = false
            } else {
                results = null
                errorMessage = null
            }
        }
    }

    // Load registered names on start
    LaunchedEffect(Unit) {
        val people = repository.getPessoas()
        allPeople = people
        if (currentSearchedName.isNotBlank() && results == null) {
            isLoading = true
            errorMessage = null
            val found = repository.getEscalaPessoa(currentSearchedName, apenasFuturas = false)
            if (found.isEmpty()) {
                errorMessage = "Nenhuma escalação encontrada para \"$currentSearchedName\"."
                results = null
            } else {
                results = found
            }
            isLoading = false
        }
    }

    fun executeSearch(nameToSearch: String) {
        val cleanName = nameToSearch.trim()
        if (cleanName.isEmpty()) return
        focusManager.clearFocus()
        isLoading = true
        errorMessage = null
        results = null

        coroutineScope.launch {
            // Resolve exact or fuzzy match from registered people, or use normalized search in repository
            val resolvedName = SearchUtils.encontrarMelhorPessoa(allPeople, cleanName) ?: cleanName
            val escalacoes = repository.getEscalaPessoa(resolvedName, apenasFuturas = false)
            if (escalacoes.isEmpty()) {
                errorMessage = "Não encontramos ninguém com o nome \"$cleanName\" na escala. Verifique a grafia ou selecione uma das opções sugeridas."
                results = null
            } else {
                results = escalacoes
                currentSearchedName = resolvedName
                searchInput = resolvedName
                preferencesManager.nomeUsuario = resolvedName
                onUserFilterChanged(resolvedName)
            }
            isLoading = false
        }
    }

    // Limpeza automática de resultados ao apagar todo o texto do campo (desfazendo a busca anterior)
    LaunchedEffect(searchInput) {
        if (searchInput.trim().isEmpty() && currentSearchedName.isNotEmpty()) {
            currentSearchedName = ""
            results = null
            errorMessage = null
            preferencesManager.nomeUsuario = ""
            onUserFilterChanged("")
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Minha escala",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Onde e quando eu sirvo",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 2.dp, bottom = 12.dp)
            )
        }

        // Search bar with autocomplete
        item {
            SearchBarWithAutocomplete(
                value = searchInput,
                onValueChange = { searchInput = it },
                onSearch = { executeSearch(it) },
                onClear = {
                    searchInput = ""
                    currentSearchedName = ""
                    results = null
                    errorMessage = null
                    preferencesManager.nomeUsuario = ""
                    onUserFilterChanged("")
                },
                allPeople = allPeople,
                placeholderText = "Digite seu nome (ex: Michael)",
                showSearchButton = true,
                debounceMillis = 0L,
                testTagInput = "name_search_input"
            )
        }

        // Loading state
        if (isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        // Error message card
        errorMessage?.let { error ->
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    colors = CardDefaults.cardColors(containerColor = errorColors.background)
                ) {
                    Text(
                        text = error,
                        modifier = Modifier.padding(16.dp),
                        color = errorColors.text,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                }
            }
        }

        // Results Presentation
        results?.let { list ->
            if (list.isNotEmpty()) {
                val todasEscalas = list
                // Separate past scales vs current/future scales
                val (escalasPassadas, escalasFuturas) = todasEscalas.partition {
                    DateUtils.isDataPassada(it.localDate, today)
                }

                val nextDuty = escalasFuturas.firstOrNull()
                val distinctChurches = todasEscalas.map { it.igreja }.distinct()

                // Hero Card: Focus on next upcoming assignment (or informative state if none)
                item {
                    if (nextDuty != null) {
                        val heroInteractionSource = remember { MutableInteractionSource() }
                        Card(
                            onClick = onNavigateToCalendar,
                            interactionSource = heroInteractionSource,
                            modifier = Modifier
                                .fillMaxWidth()
                                .pressScale(heroInteractionSource)
                                .testTag("hero_next_duty_card"),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Text(
                                    text = "Próxima escalação de $currentSearchedName",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f),
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = DateUtils.normalizarFuncao(nextDuty.funcao),
                                    style = MaterialTheme.typography.headlineMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    modifier = Modifier.padding(vertical = 4.dp)
                                )
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(top = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Place,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp),
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                        )
                                        Text(
                                            text = nextDuty.igreja,
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f),
                                            fontWeight = FontWeight.Medium
                                        )
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.CalendarToday,
                                            contentDescription = null,
                                            modifier = Modifier.size(15.dp),
                                            tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                                        )
                                        Text(
                                            text = DateUtils.humanizarDataISO(nextDuty.dataReal).ifEmpty { nextDuty.data },
                                            fontSize = 14.sp,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.height(14.dp))
                                Button(
                                    onClick = onNavigateToCalendar,
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = MaterialTheme.colorScheme.primary,
                                        contentColor = MaterialTheme.colorScheme.onPrimary
                                    ),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .testTag("btn_ver_escala_no_calendario")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarToday,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Ver datas de $currentSearchedName no Calendário",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    } else {
                        // All duties are past duties
                        val noFutureInteractionSource = remember { MutableInteractionSource() }
                        Card(
                            onClick = { filtroTempo = FiltroTempoMinhaEscala.HISTORICO },
                            interactionSource = noFutureInteractionSource,
                            modifier = Modifier
                                .fillMaxWidth()
                                .pressScale(noFutureInteractionSource)
                                .testTag("hero_no_future_duties_card"),
                            shape = RoundedCornerShape(14.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "Sem próximas escalações",
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Text(
                                    text = "$currentSearchedName não tem escalas futuras pendentes. Todas as ${escalasPassadas.size} escalas já foram concluídas.",
                                    fontSize = 13.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(
                                        onClick = { filtroTempo = FiltroTempoMinhaEscala.HISTORICO },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Ver histórico (${escalasPassadas.size})", fontSize = 12.sp)
                                    }
                                    Button(
                                        onClick = onNavigateToCalendar,
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.surface,
                                            contentColor = MaterialTheme.colorScheme.primary
                                        ),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Calendário", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // Church Indicators (if multiple)
                if (distinctChurches.size > 1) {
                    item {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            horizontalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            distinctChurches.forEach { churchName ->
                                ChurchDotIndicator(churchName = churchName)
                            }
                        }
                    }
                }

                // Summary Stats Section: Editorial horizontal strip without boxes/cards
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Total
                        val totalInteractionSource = remember { MutableInteractionSource() }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .pressScale(totalInteractionSource)
                                .clickable(
                                    interactionSource = totalInteractionSource,
                                    indication = null
                                ) { filtroTempo = FiltroTempoMinhaEscala.TODAS }
                                .padding(vertical = 6.dp)
                                .testTag("stats_summary_total"),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "${todasEscalas.size}",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Total",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        VerticalDivider(
                            modifier = Modifier.height(36.dp),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )

                        // Próximas
                        val proximasInteractionSource = remember { MutableInteractionSource() }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .pressScale(proximasInteractionSource)
                                .clickable(
                                    interactionSource = proximasInteractionSource,
                                    indication = null
                                ) { filtroTempo = FiltroTempoMinhaEscala.FUTURAS }
                                .padding(vertical = 6.dp)
                                .testTag("stats_summary_proximas"),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "${escalasFuturas.size}",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Próximas",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        VerticalDivider(
                            modifier = Modifier.height(36.dp),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
                        )

                        // Concluídas
                        val concluidasInteractionSource = remember { MutableInteractionSource() }
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .pressScale(concluidasInteractionSource)
                                .clickable(
                                    interactionSource = concluidasInteractionSource,
                                    indication = null
                                ) { filtroTempo = FiltroTempoMinhaEscala.HISTORICO }
                                .padding(vertical = 6.dp)
                                .testTag("stats_summary_concluidas"),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "${escalasPassadas.size}",
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = successColors.text
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Concluídas",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                // Rule 3: Clear UI Toggle/Filter for Present/Future vs History (Past) vs All
                item {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Option 1: Present and Future (Initial focus)
                            FilterChip(
                                selected = filtroTempo == FiltroTempoMinhaEscala.FUTURAS,
                                onClick = { filtroTempo = FiltroTempoMinhaEscala.FUTURAS },
                                label = {
                                    Text(
                                        text = "Próximas (${escalasFuturas.size})",
                                        fontSize = 12.sp,
                                        fontWeight = if (filtroTempo == FiltroTempoMinhaEscala.FUTURAS) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = if (filtroTempo == FiltroTempoMinhaEscala.FUTURAS) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.testTag("filter_chip_proximas")
                            )

                            // Option 2: History (Past scales)
                            FilterChip(
                                selected = filtroTempo == FiltroTempoMinhaEscala.HISTORICO,
                                onClick = { filtroTempo = FiltroTempoMinhaEscala.HISTORICO },
                                label = {
                                    Text(
                                        text = "Histórico (${escalasPassadas.size})",
                                        fontSize = 12.sp,
                                        fontWeight = if (filtroTempo == FiltroTempoMinhaEscala.HISTORICO) FontWeight.Bold else FontWeight.Normal
                                    )
                                },
                                leadingIcon = if (filtroTempo == FiltroTempoMinhaEscala.HISTORICO) {
                                    {
                                        Icon(
                                            imageVector = Icons.Default.History,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                } else null,
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag("filter_chip_historico")
                            )

                            // Option 3: All
                            FilterChip(
                                selected = filtroTempo == FiltroTempoMinhaEscala.TODAS,
                                onClick = { filtroTempo = FiltroTempoMinhaEscala.TODAS },
                                label = {
                                    Text(
                                        text = "Todas (${todasEscalas.size})",
                                        fontSize = 12.sp
                                    )
                                },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                                    selectedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                modifier = Modifier.testTag("filter_chip_todas")
                            )
                        }
                    }
                }

                // Determine which duties to display based on the selected filter
                val listToShow = when (filtroTempo) {
                    FiltroTempoMinhaEscala.FUTURAS -> escalasFuturas
                    FiltroTempoMinhaEscala.HISTORICO -> escalasPassadas
                    FiltroTempoMinhaEscala.TODAS -> todasEscalas
                }

                if (listToShow.isEmpty()) {
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = if (filtroTempo == FiltroTempoMinhaEscala.FUTURAS) {
                                        "Nenhuma próxima escala encontrada para $currentSearchedName."
                                    } else {
                                        "Nenhuma escala registrada no histórico."
                                    },
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                if (filtroTempo == FiltroTempoMinhaEscala.FUTURAS && escalasPassadas.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Button(
                                        onClick = { filtroTempo = FiltroTempoMinhaEscala.HISTORICO },
                                        shape = RoundedCornerShape(8.dp),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        )
                                    ) {
                                        Text("Ver ${escalasPassadas.size} escalas no Histórico", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Duties list with Rule 2 applied
                    items(listToShow) { item ->
                        val isPast = DateUtils.isDataPassada(item.localDate, today)
                        val isDutyToday = item.localDate == today
                        val churchColor = getChurchColor(item.igreja, isDark = isDark)
                        val dutyInteractionSource = remember(item.igreja, item.data, item.funcao) { MutableInteractionSource() }

                        // Rule 2: Past scales lose visual emphasis (opacity 0.60f, neutral gray stripe & badge)
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (isPast) Modifier.alpha(0.60f) else Modifier)
                                .pressScale(dutyInteractionSource)
                                .clip(RoundedCornerShape(10.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(
                                    1.dp,
                                    if (isPast) MaterialTheme.colorScheme.outline.copy(alpha = 0.4f) else MaterialTheme.colorScheme.outline,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable(
                                    interactionSource = dutyInteractionSource,
                                    indication = null
                                ) {
                                    onNavigateToCalendar()
                                }
                        ) {
                            Row(modifier = Modifier.fillMaxWidth()) {
                                // Colored side stripe: neutral gray if past, vibrant church color if upcoming/today
                                Box(
                                    modifier = Modifier
                                        .width(5.dp)
                                        .height(72.dp)
                                        .background(if (isPast) MaterialTheme.colorScheme.outline.copy(alpha = 0.5f) else churchColor.text)
                                )
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Text(
                                                text = DateUtils.normalizarFuncao(item.funcao),
                                                fontSize = 15.sp,
                                                fontWeight = if (isPast) FontWeight.Normal else FontWeight.SemiBold,
                                                color = if (isPast) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                                            )
                                            if (isDutyToday) {
                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(4.dp))
                                                        .background(MaterialTheme.colorScheme.primary)
                                                        .padding(horizontal = 6.dp, vertical = 2.dp)
                                                ) {
                                                    Text(
                                                        text = "Hoje",
                                                        fontSize = 10.sp,
                                                        fontWeight = FontWeight.Bold,
                                                        color = MaterialTheme.colorScheme.onPrimary
                                                    )
                                                }
                                            } else if (isPast) {
                                                PastBadge()
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        ChurchBadge(churchName = item.igreja)
                                    }

                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = DateUtils.humanizarDataISO(item.dataReal).ifEmpty { item.data },
                                            fontSize = 13.sp,
                                            color = if (isPast) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = if (isPast) FontWeight.Normal else FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Empty state when nothing searched yet
        if (results == null && errorMessage == null && !isLoading) {
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Digite seu nome como aparece na escala para ver seus próximos dias de serviço.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 20.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
