package com.petcare.app.ui.screens.pets

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Pets
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petcare.app.ui.common.UiState
import com.petcare.app.ui.common.appViewModel
import com.petcare.app.ui.components.EmptyState
import com.petcare.app.ui.components.ErrorState
import com.petcare.app.ui.components.LoadingState
import com.petcare.app.ui.components.PetSummaryCard
import com.petcare.app.ui.components.PetCareTopBar
import com.petcare.app.ui.components.SectionHeader
import com.petcare.app.ui.theme.Spacing

/** Screen 06. */
@Composable
fun PetsScreen(onOpenPet: (Long) -> Unit, onAddPet: () -> Unit) {
    val viewModel = appViewModel { c, _ -> PetsViewModel(c.authRepository, c.petRepository, c.taskRepository) }
    val state by viewModel.state.collectAsStateWithLifecycle()

    Scaffold(
        topBar = { PetCareTopBar(title = "Pets") },
        floatingActionButton = {
            if ((state as? UiState.Success)?.data?.isNotEmpty() == true) FloatingActionButton(onClick = onAddPet) {
                Icon(Icons.Filled.Add, contentDescription = "Add pet")
            }
        }
    ) { padding ->
        when (val s = state) {
            UiState.Loading -> LoadingState(Modifier.padding(padding))
            is UiState.Error -> ErrorState(s.message, Modifier.padding(padding))
            is UiState.Success -> if (s.data.isEmpty()) {
                Column(Modifier.padding(padding).fillMaxSize()) {
                    EmptyState(
                        icon = Icons.Outlined.Pets,
                        title = "No pets yet",
                        message = "Add a pet to start tracking their food, exercise, medication and more.",
                        actionLabel = "Add pet",
                        onAction = onAddPet
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.padding(padding).fillMaxSize(),
                    contentPadding = PaddingValues(start = Spacing.medium, end = Spacing.medium, bottom = 96.dp)
                ) {
                    item { SectionHeader("My pets") }
                    items(s.data, key = { it.pet.id }) { item ->
                        PetSummaryCard(
                            pet = item.pet,
                            completedToday = item.completedToday,
                            totalToday = item.tasksToday,
                            onViewProfile = { onOpenPet(item.pet.id) }
                        )
                        Spacer(Modifier.height(Spacing.medium))
                    }
                }
            }
        }
    }
}
