package com.aistudio.escala

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Church
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Security
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aistudio.escala.data.EscalaRepository
import com.aistudio.escala.ui.screens.CoordenadorScreen
import com.aistudio.escala.ui.screens.EscalaDoDiaScreen
import com.aistudio.escala.ui.screens.ImportarEscalaScreen
import com.aistudio.escala.ui.screens.MinhaEscalaScreen
import com.aistudio.escala.ui.theme.EscalaTheme
import com.aistudio.escala.util.CoordenadorLockoutManager
import com.aistudio.escala.util.PreferencesManager
import com.aistudio.escala.util.ThemeMode

data class NavTabItem(
    val title: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        CoordenadorLockoutManager.init(applicationContext)

        setContent {
            val context = LocalContext.current
            val preferencesManager = remember { PreferencesManager(context) }
            var themeMode by remember { mutableStateOf(preferencesManager.themeMode) }
            val systemInDark = isSystemInDarkTheme()

            val isDark = when (themeMode) {
                ThemeMode.SYSTEM -> systemInDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            EscalaTheme(darkTheme = isDark) {
                EscalaApp(
                    preferencesManager = preferencesManager,
                    themeMode = themeMode,
                    isDark = isDark,
                    onThemeModeChange = { newMode ->
                        preferencesManager.themeMode = newMode
                        themeMode = newMode
                    }
                )
            }
        }
    }
}

@Composable
fun EscalaApp(
    preferencesManager: PreferencesManager,
    themeMode: ThemeMode,
    isDark: Boolean,
    onThemeModeChange: (ThemeMode) -> Unit
) {
    val context = LocalContext.current
    val repository = remember { EscalaRepository(context) }
    val systemInDark = isSystemInDarkTheme()

    var selectedTabIndex by rememberSaveable { mutableIntStateOf(0) }
    var dataVersion by remember { mutableIntStateOf(0) }
    var activeUserFilter by rememberSaveable { mutableStateOf(preferencesManager.nomeUsuario ?: "") }

    // Retorna para a aba Minha Escala antes de permitir que o sistema feche o aplicativo
    BackHandler(enabled = selectedTabIndex != 0) {
        selectedTabIndex = 0
    }

    val navItems = listOf(
        NavTabItem(
            title = "Minha escala",
            selectedIcon = Icons.Filled.Person,
            unselectedIcon = Icons.Outlined.Person,
            testTag = "nav_minha_escala"
        ),
        NavTabItem(
            title = "Escala do dia",
            selectedIcon = Icons.Filled.CalendarMonth,
            unselectedIcon = Icons.Outlined.CalendarMonth,
            testTag = "nav_escala_do_dia"
        ),
        NavTabItem(
            title = "Importar",
            selectedIcon = Icons.Filled.UploadFile,
            unselectedIcon = Icons.Outlined.UploadFile,
            testTag = "nav_importar_escala"
        ),
        NavTabItem(
            title = "Coordenador",
            selectedIcon = Icons.Filled.Security,
            unselectedIcon = Icons.Outlined.Security,
            testTag = "nav_coordenador"
        )
    )

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.background,
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Church,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Text(
                            text = "Escala Pastoral",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Serif,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Theme toggle button
                    IconButton(
                        onClick = {
                            val nextMode = when (themeMode) {
                                ThemeMode.LIGHT -> ThemeMode.DARK
                                ThemeMode.DARK -> ThemeMode.LIGHT
                                ThemeMode.SYSTEM -> if (systemInDark) ThemeMode.LIGHT else ThemeMode.DARK
                            }
                            onThemeModeChange(nextMode)
                        },
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .testTag("button_toggle_theme")
                    ) {
                        Icon(
                            imageVector = if (isDark) Icons.Filled.LightMode else Icons.Filled.DarkMode,
                            contentDescription = if (isDark) "Mudar para modo claro" else "Mudar para modo escuro",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .navigationBarsPadding()
                    .testTag("main_navigation_bar"),
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp
            ) {
                navItems.forEachIndexed { index, item ->
                    val isSelected = selectedTabIndex == index
                    NavigationBarItem(
                        selected = isSelected,
                        onClick = { selectedTabIndex = index },
                        icon = {
                            Icon(
                                imageVector = if (isSelected) item.selectedIcon else item.unselectedIcon,
                                contentDescription = item.title
                            )
                        },
                        label = {
                            Text(
                                text = item.title,
                                fontSize = 12.sp
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.primary,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        modifier = Modifier.testTag(item.testTag)
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(innerPadding)
        ) {
            AnimatedContent(
                targetState = selectedTabIndex,
                transitionSpec = {
                    val forward = targetState > initialState
                    (slideInHorizontally(
                        initialOffsetX = { fullWidth -> if (forward) (fullWidth / 3) else (-fullWidth / 3) },
                        animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
                    ) + fadeIn(animationSpec = tween(280)))
                    .togetherWith(
                        slideOutHorizontally(
                            targetOffsetX = { fullWidth -> if (forward) (-fullWidth / 3) else (fullWidth / 3) },
                            animationSpec = tween(durationMillis = 240, easing = FastOutSlowInEasing)
                        ) + fadeOut(animationSpec = tween(200))
                    )
                },
                label = "MainTabsNavigationAnimation",
                modifier = Modifier.fillMaxSize()
            ) { tabIndex ->
                when (tabIndex) {
                    0 -> MinhaEscalaScreen(
                        repository = repository,
                        preferencesManager = preferencesManager,
                        userFilter = activeUserFilter,
                        onUserFilterChanged = { newFilter ->
                            activeUserFilter = newFilter
                        },
                        onNavigateToCalendar = {
                            selectedTabIndex = 1
                        },
                        onDataUpdated = { dataVersion++ }
                    )
                    1 -> EscalaDoDiaScreen(
                        repository = repository,
                        preferencesManager = preferencesManager,
                        userFilter = activeUserFilter,
                        onUserFilterChanged = { newFilter ->
                            activeUserFilter = newFilter
                        },
                        onClearUserFilter = {
                            activeUserFilter = ""
                            preferencesManager.nomeUsuario = ""
                        },
                        dataVersion = dataVersion
                    )
                    2 -> ImportarEscalaScreen(
                        repository = repository,
                        onImportSuccess = { _ ->
                            dataVersion++
                        }
                    )
                    3 -> CoordenadorScreen(
                        repository = repository,
                        preferencesManager = preferencesManager,
                        onDataUpdated = { dataVersion++ }
                    )
                }
            }
        }
    }
}

