package com.aistudio.escala.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.aistudio.escala.util.SearchUtils
import kotlinx.coroutines.delay

@Composable
fun SearchBarWithAutocomplete(
    value: String,
    onValueChange: (String) -> Unit,
    onSearch: (String) -> Unit,
    onClear: () -> Unit,
    allPeople: List<String>,
    placeholderText: String,
    modifier: Modifier = Modifier,
    showSearchButton: Boolean = true,
    testTagInput: String = "name_search_input",
    debounceMillis: Long = 300L
) {
    val focusManager = LocalFocusManager.current
    var isSuggestionsOpen by remember { mutableStateOf(false) }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }

    // Autocomplete filtering with normalized & fuzzy matching (local and instant when debounceMillis is 0)
    LaunchedEffect(value, allPeople) {
        val query = value.trim()
        if (query.length >= 2) {
            if (debounceMillis > 0L) {
                delay(debounceMillis)
            }
            suggestions = SearchUtils.filtrarSugestoes(allPeople, query, limite = 6)
        } else {
            suggestions = emptyList()
        }
    }

    Box(modifier = modifier.fillMaxWidth().zIndex(10f)) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = value,
                    onValueChange = {
                        onValueChange(it)
                        isSuggestionsOpen = true
                    },
                    placeholder = { Text(placeholderText, fontSize = 14.sp) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    trailingIcon = {
                        if (value.isNotEmpty()) {
                            IconButton(
                                onClick = {
                                    isSuggestionsOpen = false
                                    onClear()
                                },
                                modifier = Modifier.testTag("clear_search_button")
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Limpar busca",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = {
                        isSuggestionsOpen = false
                        focusManager.clearFocus()
                        onSearch(value)
                    }),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        focusedContainerColor = MaterialTheme.colorScheme.surface,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surface
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag(testTagInput)
                )

                if (showSearchButton) {
                    Button(
                        onClick = {
                            isSuggestionsOpen = false
                            focusManager.clearFocus()
                            onSearch(value)
                        },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ),
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .height(54.dp)
                            .testTag("search_action_button")
                    ) {
                        Text("Buscar", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            // Autocomplete suggestions dropdown
            if (isSuggestionsOpen && suggestions.isNotEmpty()) {
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 4.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                ) {
                    Column(modifier = Modifier.heightIn(max = 220.dp)) {
                        suggestions.forEach { personName ->
                            val itemInteractionSource = remember(personName) { MutableInteractionSource() }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pressScale(itemInteractionSource)
                                    .clickable(
                                        interactionSource = itemInteractionSource,
                                        indication = null
                                    ) {
                                        isSuggestionsOpen = false
                                        focusManager.clearFocus()
                                        onValueChange(personName)
                                        onSearch(personName)
                                    }
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Text(
                                    text = personName,
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
