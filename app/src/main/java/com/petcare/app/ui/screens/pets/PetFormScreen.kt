package com.petcare.app.ui.screens.pets

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.petcare.app.model.Species
import com.petcare.app.ui.common.LocalFeedback
import com.petcare.app.ui.common.appViewModel
import com.petcare.app.ui.components.DropdownField
import com.petcare.app.ui.components.ErrorState
import com.petcare.app.ui.components.FormTextField
import com.petcare.app.ui.components.ImageGalleryField
import com.petcare.app.ui.components.ImagePickerField
import com.petcare.app.ui.components.LoadingState
import com.petcare.app.ui.components.PetCareTopBar
import com.petcare.app.ui.components.SectionHeader
import com.petcare.app.ui.theme.Spacing

/** Screens 07 + 09. `onSaved(petId, wasNew)`. */
@Composable
fun PetFormScreen(onBack: () -> Unit, onSaved: (Long, Boolean) -> Unit) {
    val viewModel = appViewModel { c, handle ->
        PetFormViewModel(c.authRepository, c.petRepository, c.imageStorage, handle)
    }
    val state by viewModel.state.collectAsStateWithLifecycle()
    val feedback = LocalFeedback.current

    BackHandler {
        if (!state.isSaving) {
            viewModel.discardPendingPhotos()
            onBack()
        }
    }

    LaunchedEffect(state.savedPetId) {
        val id = state.savedPetId ?: return@LaunchedEffect
        feedback.show(if (state.isEdit) "Changes saved" else "${state.name.trim()} added")
        onSaved(id, !state.isEdit)
    }

    Scaffold(
        topBar = {
            PetCareTopBar(
                title = if (state.isEdit) "Edit pet" else "Add pet",
                onBack = {
                    if (!state.isSaving) {
                        viewModel.discardPendingPhotos()
                        onBack()
                    }
                }
            )
        }
    ) { padding ->
        when {
            state.isLoading -> LoadingState(Modifier.padding(padding))
            state.loadError != null -> ErrorState(state.loadError!!, Modifier.padding(padding))
            else -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
                    .padding(horizontal = Spacing.medium),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.height(Spacing.medium))
                ImagePickerField(
                    imagePath = state.visiblePhotoPath,
                    pendingUri = state.pendingPhotoUri,
                    onImagePicked = viewModel::onPhotoPicked,
                    onRemove = viewModel::onPhotoRemoved,
                    enabled = !state.isSaving,
                    onPickerError = feedback::show
                )
                Spacer(Modifier.height(Spacing.small))
                ImageGalleryField(
                    imagePaths = state.visibleGalleryPhotoPaths,
                    pendingUris = state.pendingGalleryPhotoUris,
                    onImagesPicked = viewModel::onGalleryPhotosPicked,
                    onRemoveImage = viewModel::onGalleryPhotoRemoved,
                    onRemovePending = viewModel::onPendingGalleryPhotoRemoved,
                    enabled = !state.isSaving,
                    onPickerError = feedback::show
                )

                SectionHeader("Basics")
                FormTextField(state.name, viewModel::onNameChange, "Pet name", required = true, error = state.nameError, placeholder = "Max", enabled = !state.isSaving)
                Spacer(Modifier.height(Spacing.small))
                DropdownField(
                    value = state.species,
                    options = Species.entries,
                    onSelect = viewModel::onSpeciesChange,
                    label = "Species",
                    optionLabel = { it.label },
                    required = true,
                    error = state.speciesError,
                    enabled = !state.isSaving
                )
                Spacer(Modifier.height(Spacing.small))
                FormTextField(state.breed, viewModel::onBreedChange, "Breed", placeholder = "Golden Retriever", enabled = !state.isSaving)
                Spacer(Modifier.height(Spacing.small))
                FormTextField(
                    state.age, viewModel::onAgeChange, "Age (years)",
                    error = state.ageError, keyboardType = KeyboardType.Number, enabled = !state.isSaving
                )
                Spacer(Modifier.height(Spacing.small))
                FormTextField(
                    state.weight, viewModel::onWeightChange, "Weight (kg)",
                    error = state.weightError, keyboardType = KeyboardType.Decimal, enabled = !state.isSaving
                )

                SectionHeader("Health & diet")
                FormTextField(state.diet, viewModel::onDietChange, "Dietary preferences", placeholder = "Dry food, twice a day", singleLine = false, minLines = 2, enabled = !state.isSaving)
                Spacer(Modifier.height(Spacing.small))
                FormTextField(state.allergies, viewModel::onAllergiesChange, "Allergies", placeholder = "None known", enabled = !state.isSaving)
                Spacer(Modifier.height(Spacing.small))
                FormTextField(state.vaccinations, viewModel::onVaccinationsChange, "Vaccination history", placeholder = "Rabies – Mar 2026", singleLine = false, minLines = 2, enabled = !state.isSaving)

                SectionHeader("Personality")
                FormTextField(state.favouriteToys, viewModel::onToysChange, "Favourite toys", placeholder = "Blue ball", enabled = !state.isSaving)
                Spacer(Modifier.height(Spacing.small))
                FormTextField(state.notes, viewModel::onNotesChange, "Notes", singleLine = false, minLines = 3, enabled = !state.isSaving)

                if (state.formError != null) {
                    Spacer(Modifier.height(Spacing.small))
                    Text(state.formError!!, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                }

                Spacer(Modifier.height(Spacing.large))
                Button(
                    onClick = viewModel::save,
                    enabled = !state.isSaving,
                    modifier = Modifier.fillMaxWidth().height(52.dp)
                    ,shape = MaterialTheme.shapes.large
                ) {
                    if (state.isSaving) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = MaterialTheme.colorScheme.onPrimary)
                    } else {
                        Icon(Icons.Filled.Check, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(if (state.isEdit) "Save changes" else "Save")
                    }
                }
                Spacer(Modifier.height(Spacing.xl))
            }
        }
    }
}
