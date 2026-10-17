package com.petcare.app.ui.screens.pets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petcare.app.model.subtitle
import com.petcare.app.model.galleryPaths
import com.petcare.app.ui.common.LocalFeedback
import com.petcare.app.ui.common.UiState
import com.petcare.app.ui.common.appViewModel
import com.petcare.app.ui.components.CompactTaskRow
import com.petcare.app.ui.components.ConfirmationDialog
import com.petcare.app.ui.components.ErrorState
import com.petcare.app.ui.components.LoadingState
import com.petcare.app.ui.components.PetAvatar
import com.petcare.app.ui.components.PetCareTopBar
import com.petcare.app.ui.components.SectionHeader
import com.petcare.app.ui.theme.Spacing
import com.petcare.app.util.DateTimeFormats
import coil.compose.AsyncImage
import java.io.File
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.window.Dialog
import androidx.compose.material3.Surface

/** Screens 08 (profile) and 10 (delete confirmation). */
@Composable
fun PetProfileScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onDeleted: () -> Unit,
    onOpenTask: (Long) -> Unit,
    onAddTask: (Long) -> Unit,
    onDelegate: (Long) -> Unit
) {
    val viewModel = appViewModel { c, handle -> PetProfileViewModel(c.petRepository, c.taskRepository, handle) }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()
    val feedback = LocalFeedback.current

    var menuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deletingPetName by remember { mutableStateOf<String?>(null) }
    var deleteInProgress by remember { mutableStateOf(false) }
    val pet = (state as? UiState.Success)?.data?.pet

    LaunchedEffect(deleted) {
        if (deleted) {
            feedback.show("${deletingPetName ?: pet?.name ?: "Pet"} deleted")
            onDeleted()
        }
    }

    Scaffold(
        topBar = {
            PetCareTopBar(title = pet?.name ?: "Pet", onBack = onBack) {
                if (pet != null) {
                    IconButton(onClick = { menuOpen = true }, enabled = !deleteInProgress) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "More options")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Edit pet") },
                            leadingIcon = { Icon(Icons.Outlined.Edit, null) },
                            onClick = { menuOpen = false; onEdit(pet.id) }
                        )
                        DropdownMenuItem(
                            text = { Text("Delegate care") },
                            leadingIcon = { Icon(Icons.Outlined.Share, null) },
                            onClick = { menuOpen = false; onDelegate(pet.id) }
                        )
                        DropdownMenuItem(
                            text = { Text("Delete pet", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) },
                            onClick = { menuOpen = false; confirmDelete = true }
                        )
                    }
                }
            }
        }
    ) { padding ->
        when (val s = state) {
            UiState.Loading -> LoadingState(Modifier.padding(padding))
            is UiState.Error -> ErrorState(s.message, Modifier.padding(padding))
            is UiState.Success -> {
                val data = s.data
                if (data == null) {
                    if (!deleted) ErrorState("This pet no longer exists.", Modifier.padding(padding))
                } else {
                    ProfileContent(
                        data = data,
                        modifier = Modifier.padding(padding),
                        onEdit = { onEdit(data.pet.id) },
                        onDelegate = { onDelegate(data.pet.id) },
                        onOpenTask = onOpenTask,
                        onAddTask = { onAddTask(data.pet.id) },
                        onToggle = { task, done ->
                            viewModel.toggleTask(
                                task = task,
                                completed = done,
                                onDone = {
                                    feedback.show(
                                        if (done) "Marked \"${task.name}\" as done"
                                        else "Marked \"${task.name}\" as not done"
                                    )
                                },
                                onError = { feedback.show(it) }
                            )
                        }
                    )
                }
            }
        }
    }

    if (confirmDelete && pet != null) {
        ConfirmationDialog(
            title = "Delete ${pet.name}?",
            message = "This will remove ${pet.name} and all of their care tasks. This can't be undone.",
            confirmLabel = "Delete",
            onConfirm = {
                confirmDelete = false
                deleteInProgress = true
                deletingPetName = pet.name
                viewModel.deletePet(pet) {
                    deleteInProgress = false
                    feedback.show(it)
                }
            },
            onDismiss = { confirmDelete = false }
        )
    }
}

@Composable
private fun ProfileContent(
    data: PetProfileData,
    modifier: Modifier,
    onEdit: () -> Unit,
    onDelegate: () -> Unit,
    onOpenTask: (Long) -> Unit,
    onAddTask: () -> Unit,
    onToggle: (com.petcare.app.model.CareTask, Boolean) -> Unit
) {
    val pet = data.pet
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Spacing.medium)
    ) {
        Card(
            shape = MaterialTheme.shapes.medium,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
        Column(modifier = Modifier.fillMaxWidth().padding(Spacing.medium), horizontalAlignment = Alignment.CenterHorizontally) {
            PetAvatar(pet = pet, size = 96.dp)
            Spacer(Modifier.height(Spacing.medium))
            Text(pet.name, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
            Text(
                pet.subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(Spacing.medium))
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.small)) {
                StatCard(value = data.allTasks.size.toString(), label = "Tasks", modifier = Modifier.weight(1f))
                StatCard(value = data.medicalTasks.size.toString(), label = "Medical", modifier = Modifier.weight(1f))
                StatCard(
                    value = "${data.completedTodayIds.size}/${data.todayTasks.size}",
                    label = "Today",
                    modifier = Modifier.weight(1f)
                )
            }
        } }

        PetPhotoGallery(pet)

        if (pet.diet.isNotBlank()) ProfileSection("Dietary & nutrition") { AboutRow("Diet", pet.diet) }
        if (pet.allergies.isNotBlank()) {
            SectionHeader("Allergies")
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                shape = MaterialTheme.shapes.medium) {
                Text(pet.allergies, color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(Spacing.medium))
            }
        }
        if (pet.favouriteToys.isNotBlank()) ProfileSection("Favourite toys") { AboutRow("Toys", pet.favouriteToys) }

        SectionHeader("Care today", actionLabel = "Add task", onAction = onAddTask)
        if (data.todayTasks.isEmpty()) {
            Text(
                "No care tasks scheduled for today.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
                shape = MaterialTheme.shapes.medium) {
                Column(Modifier.padding(horizontal = Spacing.small, vertical = Spacing.xs)) {
                    data.todayTasks.forEach { task ->
                        val done = task.id in data.completedTodayIds
                        CompactTaskRow(task, done, onToggle = { onToggle(task, !done) }, onClick = { onOpenTask(task.id) })
                    }
                }
            }
        }

        if (pet.vaccinations.isNotBlank() || data.medicalTasks.isNotEmpty()) ProfileSection("Health & medication") {
        AboutRow("Vaccinations", pet.vaccinations)
        if (data.medicalTasks.isNotEmpty()) {
            data.medicalTasks.forEach { task ->
                AboutRow(task.name, "${DateTimeFormats.time(task.timeMinutes)} • ${com.petcare.app.model.Frequency.from(task.frequency).description}")
            }
        } }

        if (pet.notes.isNotBlank()) {
            ProfileSection("Notes") { Text(pet.notes, style = MaterialTheme.typography.bodyMedium) }
        }

        Spacer(Modifier.height(Spacing.large))
        FilledTonalButton(onClick = onEdit, modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = MaterialTheme.shapes.large) {
            Icon(Icons.Outlined.Edit, null)
            Spacer(Modifier.width(Spacing.small))
            Text("Edit pet")
        }
        Spacer(Modifier.height(Spacing.small))
        Button(onClick = onDelegate, modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = MaterialTheme.shapes.large) {
            Icon(Icons.Outlined.Share, null)
            Spacer(Modifier.width(Spacing.small))
            Text("Delegate care")
        }
        Spacer(Modifier.height(Spacing.xl))
    }
}

@Composable
private fun PetPhotoGallery(pet: com.petcare.app.model.Pet) {
    val paths = buildList {
        pet.photoPath?.takeIf { it.isNotBlank() }?.let(::add)
        addAll(pet.galleryPaths)
    }.distinct().mapNotNull { path ->
        File(path).takeIf { it.exists() }?.let { path }
    }
    if (paths.isEmpty()) return

    var selectedPath by remember { mutableStateOf<String?>(null) }
    SectionHeader("Photos")
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(Spacing.small)
    ) {
        paths.forEach { path ->
            AsyncImage(
                model = File(path),
                contentDescription = "${pet.name}'s photo",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(112.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { selectedPath = path }
            )
        }
    }
    if (selectedPath != null) {
        Dialog(onDismissRequest = { selectedPath = null }) {
            Surface(
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surface
            ) {
                AsyncImage(
                    model = File(selectedPath!!),
                    contentDescription = "${pet.name}'s photo",
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.size(320.dp)
                )
            }
        }
    }
}

@Composable
private fun ProfileSection(title: String, content: @Composable () -> Unit) {
    SectionHeader(title)
    Card(
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLowest),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) { Column(Modifier.padding(Spacing.medium)) { content() } }
}

@Composable
private fun StatCard(value: String, label: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(vertical = Spacing.medium),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(value, style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AboutRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        Text(
            label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(128.dp)
        )
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
}
