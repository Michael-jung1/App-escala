package com.aistudio.escala.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.key
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.EventBusy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.aistudio.escala.data.DataDisponivel
import com.aistudio.escala.data.EscalacaoDia
import com.aistudio.escala.data.EscalaRepository
import com.aistudio.escala.ui.components.CalendarioMensal
import com.aistudio.escala.ui.components.PastBadge
import com.aistudio.escala.ui.components.SearchBarWithAutocomplete
import com.aistudio.escala.util.DateUtils
import com.aistudio.escala.util.PreferencesManager
import com.aistudio.escala.util.SearchUtils
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.YearMonth

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EscalaDoDiaScreen(
    repository: EscalaRepository,
    preferencesManager: PreferencesManager,
    modifier: Modifier = Modifier,
    userFilter: String = "",
    onUserFilterChanged: (String) -> Unit = {},
    onClearUserFilter: () -> Unit = {},
    dataVersion: Int = 0
) {
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val today = LocalDate.now()

    val activeUser = userFilter.ifBlank { preferencesManager.nomeUsuario ?: "" }.trim()
    var searchInput by remember { mutableStateOf(activeUser) }
    var allPeople by remember { mutableStateOf<List<String>>(emptyList()) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var isSuggestionsOpen by remember { mutableStateOf(false) }

    var availableDates by remember { mutableStateOf<List<DataDisponivel>>(emptyList()) }
    var serviceDatesSet by remember { mutableStateOf<Set<LocalDate>>(emptySet()) }
    var userSpecificDates by remember { mutableStateOf<Set<LocalDate>?>(null) }
    var selectedLocalDate by remember { mutableStateOf<LocalDate?>(null) }
    var selectedDateServico by remember { mutableStateOf<String>("") }

    var churches by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedChurchFilter by remember { mutableStateOf("Todas as igrejas") }
    var isFilterMenuExpanded by remember { mutableStateOf(false) }

    var dayDuties by remember { mutableStateOf<List<EscalacaoDia>?>(null) }
    var fallbackDateServico by remember { mutableStateOf<String?>(null) }
    var fallbackLocalDate by remember { mutableStateOf<LocalDate?>(null) }
    var fallbackDuties by remember { mutableStateOf<List<EscalacaoDia>?>(null) }
    var isFallbackActive by remember { mutableStateOf(false) }

    var isLoading by remember { mutableStateOf(false) }
    var collapsedChurches by remember { mutableStateOf<Set<String>>(emptySet()) }

    // Synchronize local search input when activeUser changes externally
    LaunchedEffect(activeUser) {
        if (searchInput != activeUser) {
            searchInput = activeUser
        }
    }

    // Load registered names for autocomplete
    LaunchedEffect(Unit) {
        allPeople = repository.getPessoas()
    }

    // Debounce search input: automatically filters calendar & duty list without lagging typing
    LaunchedEffect(searchInput) {
        val query = searchInput.trim()
        if (query.length >= 2) {
            delay(300L)
            if (query != activeUser) {
                val resolved = SearchUtils.encontrarMelhorPessoa(allPeople, query) ?: query
                onUserFilterChanged(resolved)
            }
        } else if (query.isEmpty() && activeUser.isNotEmpty()) {
            onClearUserFilter()
        }
    }

    fun loadScheduleForDate(dateServico: String, targetDate: LocalDate?) {
        isLoading = true
        coroutineScope.launch {
            val duties = repository.getEscalaDoDia(dateServico)
            dayDuties = duties

            // Regra Cenário B (Fallback):
            // Se houver um usuário filtrado e neste dia o usuário NÃO estiver escalado,
            // ou se a lista estiver vazia para ele, busca e exibe automaticamente a próxima escala futura.
            if (activeUser.isNotBlank()) {
                val userServesHere = duties.any { SearchUtils.correspondeBusca(it.pessoa, activeUser) }
                if (!userServesHere) {
                    val userDuties = repository.getEscalaPessoa(activeUser, apenasFuturas = false)
                    val currentDate = LocalDate.now()
                    val futureDuty = userDuties.filter { duty ->
                        duty.localDate != null && (duty.localDate >= currentDate)
                    }.minByOrNull { it.localDate!! } ?: userDuties.filter { duty ->
                        duty.localDate != null && (targetDate == null || duty.localDate > targetDate) && (duty.localDate >= currentDate)
                    }.minByOrNull { it.localDate!! }

                    if (futureDuty != null && futureDuty.data.isNotBlank()) {
                        fallbackDateServico = futureDuty.data
                        fallbackLocalDate = futureDuty.localDate
                        fallbackDuties = repository.getEscalaDoDia(futureDuty.data)
                        isFallbackActive = true
                    } else {
                        fallbackDateServico = null
                        fallbackLocalDate = null
                        fallbackDuties = null
                        isFallbackActive = false
                    }
                } else {
                    fallbackDateServico = null
                    fallbackLocalDate = null
                    fallbackDuties = null
                    isFallbackActive = false
                }
            } else {
                fallbackDateServico = null
                fallbackLocalDate = null
                fallbackDuties = null
                isFallbackActive = false
            }

            isLoading = false
        }
    }

    fun applyUserFilter(name: String) {
        val clean = name.trim()
        val resolved = SearchUtils.encontrarMelhorPessoa(allPeople, clean) ?: clean
        searchInput = resolved
        isSuggestionsOpen = false
        focusManager.clearFocus()
        onUserFilterChanged(resolved)
    }

    fun clearFilter() {
        searchInput = ""
        isSuggestionsOpen = false
        focusManager.clearFocus()
        onClearUserFilter()
    }

    // React whenever activeUser or dataVersion updates
    LaunchedEffect(activeUser, dataVersion) {
        val igrejas = repository.getIgrejas().map { it.nome }
        churches = listOf("Todas as igrejas") + igrejas

        val dates = repository.getDatasDisponiveis()
        availableDates = dates
        val allLocalDates = dates.mapNotNull { it.localDate }.toSet()

        if (activeUser.isNotBlank()) {
            val userDuties = repository.getEscalaPessoa(activeUser, apenasFuturas = false)
            val userDates = userDuties.mapNotNull { it.localDate }.toSet()
            userSpecificDates = userDates
            // Regras de Renderização: Calendário destaca os dias em que o nome está escalado
            serviceDatesSet = userDates

            // Auto-seleciona a próxima escala futura do usuário se o dia atual não for de escala dele
            val currentSelected = selectedLocalDate
            if (userDates.isNotEmpty() && (currentSelected == null || !userDates.contains(currentSelected))) {
                val nextDate = userDates.filter { it >= today }.minOrNull() ?: userDates.minOrNull()
                if (nextDate != null) {
                    selectedLocalDate = nextDate
                    val match = dates.firstOrNull { it.localDate == nextDate }
                    val ds = match?.dataServico ?: ""
                    selectedDateServico = ds
                    loadScheduleForDate(ds, nextDate)
                    return@LaunchedEffect
                }
            }
        } else {
            userSpecificDates = null
            // All dates with scale
            serviceDatesSet = allLocalDates
        }

        val allDatesList = dates.mapNotNull { it.localDate }
        val relevantFallback = allDatesList.filter { it >= today }.minOrNull()
            ?: allDatesList.maxOrNull()
            ?: today

        val initialDateObj = selectedLocalDate ?: relevantFallback
        selectedLocalDate = initialDateObj
        val match = dates.firstOrNull { it.localDate == initialDateObj }
        val dateServico = match?.dataServico ?: DateUtils.formatarDataExtenso(initialDateObj)
        selectedDateServico = dateServico
        loadScheduleForDate(dateServico, initialDateObj)
    }

    val isSelectedDatePast = selectedLocalDate?.let { DateUtils.isDataPassada(it, today) } ?: false
    val isSelectedDateToday = selectedLocalDate == today

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Escala do dia",
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 0.5.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Quem serve nesse dia",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 2.dp, bottom = 4.dp)
            )
        }

        // Search Bar by Name with Autocomplete
        item {
            SearchBarWithAutocomplete(
                value = searchInput,
                onValueChange = { searchInput = it },
                onSearch = { applyUserFilter(it) },
                onClear = { clearFilter() },
                allPeople = allPeople,
                placeholderText = "Filtrar calendário por nome (ex: Michael)",
                showSearchButton = false,
                testTagInput = "input_filtro_calendario_nome"
            )
        }

        // Active user filter banner (Rule 1 indicator)
        if (activeUser.isNotBlank()) {
            item {
                val dateCount = userSpecificDates?.size ?: 0
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("filter_user_active_banner"),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f)
                    )
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Person,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Filtro ativo: $activeUser",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = if (dateCount > 0) {
                                        "Calendário destacando apenas os $dateCount dia(s) de $activeUser"
                                    } else {
                                        "Nenhuma escala encontrada para $activeUser"
                                    },
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                                )
                            }
                        }
                        IconButton(
                            onClick = { clearFilter() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remover filtro",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }
            }
        }

        // Monthly Interactive Calendar (Rule 1 & Rule 2 applied)
        item {
            val calendarInitialYearMonth = remember(availableDates, selectedLocalDate) {
                selectedLocalDate?.let { YearMonth.from(it) }
                    ?: availableDates.mapNotNull { it.localDate }.filter { it >= today }.minOrNull()?.let { YearMonth.from(it) }
                    ?: availableDates.mapNotNull { it.localDate }.maxOrNull()?.let { YearMonth.from(it) }
                    ?: YearMonth.now()
            }

            CalendarioMensal(
                serviceDates = serviceDatesSet,
                selectedDate = selectedLocalDate,
                initialYearMonth = calendarInitialYearMonth,
                onMonthChanged = { newYearMonth ->
                    if (selectedLocalDate == null || YearMonth.from(selectedLocalDate) != newYearMonth) {
                        val datesInMonth = availableDates.filter { it.localDate != null && YearMonth.from(it.localDate) == newYearMonth }
                        val userDatesInMonth = userSpecificDates?.filter { YearMonth.from(it) == newYearMonth }

                        val targetDate = if (datesInMonth.isNotEmpty()) {
                            val bestUserDate = if (!userDatesInMonth.isNullOrEmpty()) {
                                userDatesInMonth.filter { it >= today }.minOrNull() ?: userDatesInMonth.minOrNull()
                            } else null

                            bestUserDate
                                ?: datesInMonth.mapNotNull { it.localDate }.filter { it >= today }.minOrNull()
                                ?: datesInMonth.mapNotNull { it.localDate }.minOrNull()
                                ?: datesInMonth.first().localDate!!
                        } else {
                            if (YearMonth.from(today) == newYearMonth) today else newYearMonth.atDay(1)
                        }

                        selectedLocalDate = targetDate
                        val match = availableDates.firstOrNull { it.localDate == targetDate }
                        val ds = match?.dataServico ?: DateUtils.formatarDataExtenso(targetDate)
                        selectedDateServico = ds
                        loadScheduleForDate(ds, targetDate)
                    }
                },
                onSelectDate = { pickedDate ->
                    selectedLocalDate = pickedDate
                    val match = availableDates.firstOrNull { it.localDate == pickedDate }
                    if (match != null) {
                        selectedDateServico = match.dataServico
                        loadScheduleForDate(match.dataServico, pickedDate)
                    } else {
                        selectedDateServico = ""
                        dayDuties = emptyList()
                        loadScheduleForDate("", pickedDate)
                    }
                }
            )
        }

        // Date Status Indicator Banner (Rule 2: visual distinction between Past, Today, and Future)
        item {
            val dataFormatada = selectedLocalDate?.let { DateUtils.formatarDataExtenso(it) } ?: selectedDateServico
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = dataFormatada,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isSelectedDatePast) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface
                )

                when {
                    isSelectedDateToday -> {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(MaterialTheme.colorScheme.primary)
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "Hoje",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    isSelectedDatePast -> {
                        // Rule 2: Past scales lose visual highlight and get neutral/muted styling
                        PastBadge(showIcon = true, text = "Escala passada")
                    }
                }
            }
        }

        // Church Filter Dropdown (only when viewing all persons, not when filtered strictly by person)
        if (activeUser.isBlank()) {
            item {
                ExposedDropdownMenuBox(
                    expanded = isFilterMenuExpanded,
                    onExpandedChange = { isFilterMenuExpanded = !isFilterMenuExpanded },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    OutlinedTextField(
                        value = selectedChurchFilter,
                        onValueChange = {},
                        readOnly = true,
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Place,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        },
                        trailingIcon = {
                            ExposedDropdownMenuDefaults.TrailingIcon(expanded = isFilterMenuExpanded)
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                            focusedContainerColor = MaterialTheme.colorScheme.surface,
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface
                        ),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                            .testTag("church_filter_dropdown")
                    )

                    ExposedDropdownMenu(
                        expanded = isFilterMenuExpanded,
                        onDismissRequest = { isFilterMenuExpanded = false }
                    ) {
                        churches.forEach { church ->
                            DropdownMenuItem(
                                text = { Text(church) },
                                onClick = {
                                    selectedChurchFilter = church
                                    isFilterMenuExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        // Schedule by Church Listing (Strict Rule 1 & Rule 2 Enforcement)
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
        } else {
            val duties = dayDuties ?: emptyList()

            // Regras de Renderização do Calendário e Lista Inferior (Filtro por Nome):
            // 1. Filtragem e Destaque por Local (Igrejas): Ao pesquisar um nome (ex: "Michael"), o sistema deve exibir a escala completa (mostrando todos os integrantes) apenas das igrejas onde o nome pesquisado está escalado. Dentro dessa escala completa, aplique um destaque visual (highlight) especificamente no nome pesquisado.
            // 2. Ocultação Condicional de Locais: Se houver uma escala para uma igreja onde o nome pesquisado não está presente, a escala inteira dessa igreja específica deve ser ocultada da interface.
            // 3. Comportamento da Lista (Abaixo do Calendário):
            //    - Cenário A (Data com escala selecionada): Quando o usuário clicar em um dia no calendário que contenha uma escala para o nome pesquisado, a lista inferior deve exibir a escala completa de todas as igrejas onde a pessoa servirá naquele dia exato.
            //    - Cenário B (Fallback - Data sem escala ou Dia Atual): Se o dia selecionado for o "dia de hoje" (e o usuário não estiver escalado) ou se for selecionado um dia qualquer sem escala para aquele nome, a lista não deve ficar vazia. Ela deve buscar e exibir automaticamente a próxima escala futura em que o nome pesquisado irá servir.
            if (activeUser.isNotBlank()) {
                val matchingUserDuties = duties.filter {
                    SearchUtils.correspondeBusca(it.pessoa, activeUser)
                }

                if (matchingUserDuties.isNotEmpty()) {
                    // Cenário A: O usuário pesquisado está escalado neste dia selecionado!
                    // Igrejas onde o nome pesquisado está escalado
                    val churchesWhereUserServes = matchingUserDuties.map { it.igreja }.toSet()
                    // Agrupa TODAS as pessoas da escala dessas igrejas (escala completa)
                    val fullDutiesByChurch = duties
                        .filter { churchesWhereUserServes.contains(it.igreja) }
                        .groupBy { it.igreja }

                    items(fullDutiesByChurch.keys.toList(), key = { it }) { churchName ->
                        val churchDuties = fullDutiesByChurch[churchName] ?: emptyList()
                        val isCollapsed = collapsedChurches.contains(churchName)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(if (isSelectedDatePast) Modifier.alpha(0.65f) else Modifier)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surface)
                                .border(
                                    1.dp,
                                    if (isSelectedDatePast) MaterialTheme.colorScheme.outline.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                    RoundedCornerShape(12.dp)
                                )
                        ) {
                            Column {
                                // Accordion Header
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(
                                            if (isSelectedDatePast) {
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                            } else {
                                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                            }
                                        )
                                        .clickable {
                                            collapsedChurches = if (isCollapsed) {
                                                collapsedChurches - churchName
                                            } else {
                                                collapsedChurches + churchName
                                            }
                                        }
                                        .padding(14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Place,
                                            contentDescription = null,
                                            tint = if (isSelectedDatePast) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(20.dp)
                                        )
                                        Column {
                                            Text(
                                                text = churchName,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Text(
                                                text = "Escala completa (${churchDuties.size} ${if (churchDuties.size == 1) "integrante" else "integrantes"})",
                                                fontSize = 11.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }

                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                            contentDescription = if (isCollapsed) "Expandir" else "Recolher",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                // Accordion Body: Exibe a escala COMPLETA com DESTAQUE no nome pesquisado
                                AnimatedVisibility(visible = !isCollapsed) {
                                    Column {
                                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                        churchDuties.forEachIndexed { index, duty ->
                                            key(duty.escalacaoId) {
                                                val isPersonHighlighted = SearchUtils.correspondeBusca(duty.pessoa, activeUser)

                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                                                    .then(
                                                        if (isPersonHighlighted) {
                                                            Modifier
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .background(
                                                                    if (isSelectedDatePast) {
                                                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
                                                                    } else {
                                                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                                                                    }
                                                                )
                                                                .border(
                                                                    1.dp,
                                                                    if (isSelectedDatePast) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.primary,
                                                                    RoundedCornerShape(8.dp)
                                                                )
                                                                .padding(horizontal = 10.dp, vertical = 9.dp)
                                                        } else {
                                                            Modifier.padding(horizontal = 10.dp, vertical = 9.dp)
                                                        }
                                                    ),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(
                                                        text = DateUtils.normalizarFuncao(duty.funcao),
                                                        fontSize = 13.sp,
                                                        color = if (isPersonHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                        fontWeight = if (isPersonHighlighted) FontWeight.Bold else FontWeight.Medium
                                                    )
                                                }

                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Text(
                                                        text = duty.pessoa,
                                                        fontSize = 14.sp,
                                                        fontWeight = if (isPersonHighlighted) FontWeight.Bold else FontWeight.Normal,
                                                        color = if (isPersonHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                    )
                                                    if (isPersonHighlighted) {
                                                        Box(
                                                            modifier = Modifier
                                                                .clip(RoundedCornerShape(4.dp))
                                                                .background(MaterialTheme.colorScheme.primary)
                                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                                        ) {
                                                            Text(
                                                                text = "Destaque",
                                                                fontSize = 10.sp,
                                                                fontWeight = FontWeight.Bold,
                                                                color = MaterialTheme.colorScheme.onPrimary
                                                            )
                                                        }
                                                    }
                                                }
                                            }

                                            if (index < churchDuties.size - 1 && !isPersonHighlighted) {
                                                HorizontalDivider(
                                                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                                    modifier = Modifier.padding(horizontal = 12.dp)
                                                )
                                            }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Cenário B (Fallback - Data sem escala para o nome ou Dia Atual):
                    // A lista não deve ficar vazia. Ela busca e exibe automaticamente a próxima escala futura.
                    item {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("fallback_user_schedule_banner"),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CalendarToday,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(32.dp)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = "$activeUser não possui escala na data selecionada",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )
                                Text(
                                    text = if (isFallbackActive && fallbackLocalDate != null) {
                                        "Exibindo automaticamente a próxima escala futura em ${DateUtils.formatarDataExtenso(fallbackLocalDate!!)}:"
                                    } else {
                                        "Nenhuma próxima escala futura encontrada para $activeUser."
                                    },
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(top = 4.dp)
                                )
                            }
                        }
                    }

                    // Se houver próxima escala encontrada no fallback, exibe a escala completa das igrejas onde serve!
                    val activeFallbackDuties = fallbackDuties ?: emptyList()
                    val matchingFallback = activeFallbackDuties.filter { SearchUtils.correspondeBusca(it.pessoa, activeUser) }

                    if (matchingFallback.isNotEmpty()) {
                        val fallbackChurchesWhereUserServes = matchingFallback.map { it.igreja }.toSet()
                        val fullFallbackDutiesByChurch = activeFallbackDuties
                            .filter { fallbackChurchesWhereUserServes.contains(it.igreja) }
                            .groupBy { it.igreja }

                        items(fullFallbackDutiesByChurch.keys.toList(), key = { it }) { churchName ->
                            val churchDuties = fullFallbackDutiesByChurch[churchName] ?: emptyList()
                            val isCollapsed = collapsedChurches.contains("fallback_$churchName")

                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(
                                        1.dp,
                                        MaterialTheme.colorScheme.primary.copy(alpha = 0.6f),
                                        RoundedCornerShape(12.dp)
                                    )
                            ) {
                                Column {
                                    // Accordion Header
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f))
                                            .clickable {
                                                val key = "fallback_$churchName"
                                                collapsedChurches = if (isCollapsed) {
                                                    collapsedChurches - key
                                                } else {
                                                    collapsedChurches + key
                                                }
                                            }
                                            .padding(14.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Place,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Column {
                                                Text(
                                                    text = churchName,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = MaterialTheme.colorScheme.onSurface
                                                )
                                                Text(
                                                    text = "Próxima escala (${churchDuties.size} ${if (churchDuties.size == 1) "integrante" else "integrantes"})",
                                                    fontSize = 11.sp,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                                contentDescription = if (isCollapsed) "Expandir" else "Recolher",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    // Accordion Body: Exibe a escala COMPLETA da igreja com DESTAQUE no nome pesquisado
                                    AnimatedVisibility(visible = !isCollapsed) {
                                        Column {
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                            churchDuties.forEachIndexed { index, duty ->
                                                key(duty.escalacaoId) {
                                                    val isPersonHighlighted = SearchUtils.correspondeBusca(duty.pessoa, activeUser)

                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                                        .then(
                                                            if (isPersonHighlighted) {
                                                                Modifier
                                                                    .clip(RoundedCornerShape(8.dp))
                                                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f))
                                                                    .border(
                                                                        1.dp,
                                                                        MaterialTheme.colorScheme.primary,
                                                                        RoundedCornerShape(8.dp)
                                                                    )
                                                                    .padding(horizontal = 10.dp, vertical = 9.dp)
                                                            } else {
                                                                Modifier.padding(horizontal = 10.dp, vertical = 9.dp)
                                                            }
                                                        ),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(
                                                            text = DateUtils.normalizarFuncao(duty.funcao),
                                                            fontSize = 13.sp,
                                                            color = if (isPersonHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                                            fontWeight = if (isPersonHighlighted) FontWeight.Bold else FontWeight.Medium
                                                        )
                                                    }

                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                    ) {
                                                        Text(
                                                            text = duty.pessoa,
                                                            fontSize = 14.sp,
                                                            fontWeight = if (isPersonHighlighted) FontWeight.Bold else FontWeight.Normal,
                                                            color = if (isPersonHighlighted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                        )
                                                        if (isPersonHighlighted) {
                                                            Box(
                                                                modifier = Modifier
                                                                    .clip(RoundedCornerShape(4.dp))
                                                                    .background(MaterialTheme.colorScheme.primary)
                                                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                                            ) {
                                                                Text(
                                                                    text = "Destaque",
                                                                    fontSize = 10.sp,
                                                                    fontWeight = FontWeight.Bold,
                                                                    color = MaterialTheme.colorScheme.onPrimary
                                                                )
                                                            }
                                                        }
                                                    }
                                                }

                                                if (index < churchDuties.size - 1 && !isPersonHighlighted) {
                                                    HorizontalDivider(
                                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                                                        modifier = Modifier.padding(horizontal = 12.dp)
                                                    )
                                                }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // No name filter active: show all duties of the day (respecting church dropdown)
                if (duties.isEmpty()) {
                    item {
                        Text(
                            text = "Nenhuma escalação encontrada para essa data.",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(vertical = 16.dp)
                        )
                    }
                } else {
                    val groupedByChurch = duties.groupBy { it.igreja }
                    val filteredChurches = groupedByChurch.keys.filter { churchName ->
                        selectedChurchFilter == "Todas as igrejas" || churchName == selectedChurchFilter
                    }

                    if (filteredChurches.isEmpty()) {
                        item {
                            Text(
                                text = "Essa igreja não tem escalação nessa data.",
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 16.dp)
                            )
                        }
                    } else {
                        items(filteredChurches.toList(), key = { it }) { churchName ->
                            val churchDuties = groupedByChurch[churchName] ?: emptyList()
                            val isCollapsed = collapsedChurches.contains(churchName)

                            // Rule 2: Past scales lose visual emphasis
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(if (isSelectedDatePast) Modifier.alpha(0.65f) else Modifier)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surface)
                                    .border(
                                        1.dp,
                                        if (isSelectedDatePast) MaterialTheme.colorScheme.outline.copy(alpha = 0.5f) else MaterialTheme.colorScheme.outline,
                                        RoundedCornerShape(12.dp)
                                    )
                            ) {
                                Column {
                                    // Accordion Header
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(
                                                if (isSelectedDatePast) {
                                                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)
                                                } else {
                                                    Color.Transparent
                                                }
                                            )
                                            .clickable {
                                                collapsedChurches = if (isCollapsed) {
                                                    collapsedChurches - churchName
                                                } else {
                                                    collapsedChurches + churchName
                                                }
                                            }
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Place,
                                                contentDescription = null,
                                                tint = if (isSelectedDatePast) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                            Text(
                                                text = churchName,
                                                fontSize = 15.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Text(
                                                text = "${churchDuties.size} ${if (churchDuties.size == 1) "posto" else "postos"}",
                                                fontSize = 13.sp,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                            Icon(
                                                imageVector = if (isCollapsed) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                                                contentDescription = if (isCollapsed) "Expandir" else "Recolher",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    // Accordion Body
                                    AnimatedVisibility(visible = !isCollapsed) {
                                        Column {
                                            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                                            churchDuties.forEachIndexed { index, duty ->
                                                key(duty.escalacaoId) {
                                                    Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(horizontal = 16.dp, vertical = 10.dp),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = DateUtils.normalizarFuncao(duty.funcao),
                                                        fontSize = 14.sp,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        fontWeight = FontWeight.Normal
                                                    )

                                                    Text(
                                                        text = duty.pessoa,
                                                        fontSize = 14.sp,
                                                        fontWeight = FontWeight.SemiBold,
                                                        color = MaterialTheme.colorScheme.onSurface
                                                    )
                                                }
                                                if (index < churchDuties.size - 1) {
                                                    HorizontalDivider(
                                                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                                                        modifier = Modifier.padding(horizontal = 16.dp)
                                                    )
                                                }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}
