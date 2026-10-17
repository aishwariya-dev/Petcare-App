package com.petcare.app.ui.screens.pets

import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.petcare.app.data.repository.AuthRepository
import com.petcare.app.data.repository.PetRepository
import com.petcare.app.model.Pet
import com.petcare.app.model.PetPhotoPaths
import com.petcare.app.model.Species
import com.petcare.app.model.galleryPaths
import com.petcare.app.navigation.Routes
import com.petcare.app.ui.common.userMessage
import com.petcare.app.util.ImageStorage
import com.petcare.app.util.Validators
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PetFormState(
    val isEdit: Boolean = false,
    val isLoading: Boolean = true,
    val loadError: String? = null,
    val name: String = "",
    val species: Species? = null,
    val breed: String = "",
    val age: String = "",
    val weight: String = "",
    val diet: String = "",
    val allergies: String = "",
    val vaccinations: String = "",
    val favouriteToys: String = "",
    val notes: String = "",
    val existingPhotoPath: String? = null,
    val pendingPhotoUri: Uri? = null,
    val photoRemoved: Boolean = false,
    val existingGalleryPhotoPaths: List<String> = emptyList(),
    val pendingGalleryPhotoUris: List<Uri> = emptyList(),
    val removedGalleryPhotoPaths: Set<String> = emptySet(),
    val nameError: String? = null,
    val speciesError: String? = null,
    val ageError: String? = null,
    val weightError: String? = null,
    val formError: String? = null,
    val isSaving: Boolean = false,
    /** Set once the pet is saved; the screen navigates away. */
    val savedPetId: Long? = null
) {
    val visiblePhotoPath: String? get() = if (photoRemoved) null else existingPhotoPath
    val visibleGalleryPhotoPaths: List<String>
        get() = existingGalleryPhotoPaths.filterNot { it in removedGalleryPhotoPaths }
}

/** Screens 07 (add) and 09 (edit) share this ViewModel; edit mode is chosen by the petId argument. */
class PetFormViewModel(
    private val authRepository: AuthRepository,
    private val petRepository: PetRepository,
    private val imageStorage: ImageStorage,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    private val petId: Long = savedStateHandle.get<Long>(Routes.ARG_PET_ID) ?: -1L
    private var existingPet: Pet? = null

    private val _state = MutableStateFlow(PetFormState(isEdit = petId > 0))
    val state: StateFlow<PetFormState> = _state.asStateFlow()

    init {
        if (petId > 0) load() else _state.update { it.copy(isLoading = false) }
    }

    private fun load() {
        viewModelScope.launch {
            val pet = petRepository.getPet(petId)
            if (pet == null) {
                _state.update { it.copy(isLoading = false, loadError = "This pet no longer exists.") }
                return@launch
            }
            existingPet = pet
            _state.update {
                it.copy(
                    isLoading = false,
                    name = pet.name,
                    species = Species.from(pet.species),
                    breed = pet.breed,
                    age = pet.ageYears?.toString() ?: "",
                    weight = pet.weightKg?.let { w -> if (w % 1.0 == 0.0) w.toInt().toString() else w.toString() } ?: "",
                    diet = pet.diet,
                    allergies = pet.allergies,
                    vaccinations = pet.vaccinations,
                    favouriteToys = pet.favouriteToys,
                    notes = pet.notes,
                    existingPhotoPath = pet.photoPath,
                    existingGalleryPhotoPaths = pet.galleryPaths
                )
            }
        }
    }

    fun onNameChange(v: String) = _state.update { it.copy(name = v, nameError = null) }
    fun onSpeciesChange(v: Species) = _state.update { it.copy(species = v, speciesError = null) }
    fun onBreedChange(v: String) = _state.update { it.copy(breed = v) }
    fun onAgeChange(v: String) = _state.update { it.copy(age = v, ageError = null) }
    fun onWeightChange(v: String) = _state.update { it.copy(weight = v, weightError = null) }
    fun onDietChange(v: String) = _state.update { it.copy(diet = v) }
    fun onAllergiesChange(v: String) = _state.update { it.copy(allergies = v) }
    fun onVaccinationsChange(v: String) = _state.update { it.copy(vaccinations = v) }
    fun onToysChange(v: String) = _state.update { it.copy(favouriteToys = v) }
    fun onNotesChange(v: String) = _state.update { it.copy(notes = v) }
    fun onPhotoPicked(uri: Uri) {
        imageStorage.discardCapture(_state.value.pendingPhotoUri?.takeIf { it != uri })
        _state.update { it.copy(pendingPhotoUri = uri, photoRemoved = false) }
    }

    fun onPhotoRemoved() {
        imageStorage.discardCapture(_state.value.pendingPhotoUri)
        _state.update { it.copy(pendingPhotoUri = null, photoRemoved = true) }
    }
    fun onGalleryPhotosPicked(uris: List<Uri>) = _state.update { state ->
        val known = state.pendingGalleryPhotoUris.map(Uri::toString).toSet()
        val requested = uris.distinctBy { it.toString() }
        val primaryCount = if (
            !state.photoRemoved &&
            (state.pendingPhotoUri != null || state.existingPhotoPath != null)
        ) 1 else 0
        val remaining = (
            PetPhotoPaths.MAX_TOTAL_PHOTOS -
                primaryCount -
                state.visibleGalleryPhotoPaths.size -
                state.pendingGalleryPhotoUris.size
            ).coerceAtLeast(0)
        val accepted = requested
            .filter { it.toString() !in known }
            .take(remaining)
        state.copy(
            pendingGalleryPhotoUris = state.pendingGalleryPhotoUris +
                accepted,
            formError = if (accepted.size < requested.count { it.toString() !in known }) {
                "You can add up to ${PetPhotoPaths.MAX_TOTAL_PHOTOS} photos total."
            } else {
                state.formError
            }
        )
    }

    fun onGalleryPhotoRemoved(path: String) = _state.update {
        it.copy(removedGalleryPhotoPaths = it.removedGalleryPhotoPaths + path)
    }

    fun onPendingGalleryPhotoRemoved(uri: Uri) {
        imageStorage.discardCapture(uri)
        _state.update { it.copy(pendingGalleryPhotoUris = it.pendingGalleryPhotoUris - uri) }
    }

    /** Called when the user abandons the form; safe for configuration changes. */
    fun discardPendingPhotos() {
        val pending = _state.value
        imageStorage.discardCapture(pending.pendingPhotoUri)
        pending.pendingGalleryPhotoUris.forEach { imageStorage.discardCapture(it) }
        _state.update {
            it.copy(
                pendingPhotoUri = null,
                pendingGalleryPhotoUris = emptyList(),
                // These are draft-only changes.  If the ViewModel is retained
                // after cancellation, the persisted photos must become
                // visible again rather than looking deleted.
                photoRemoved = false,
                removedGalleryPhotoPaths = emptySet()
            )
        }
    }

    fun save() {
        if (_state.value.isSaving) return
        val s = _state.value
        val nameError = Validators.required(s.name, "Pet name")
        val speciesError = if (s.species == null) "Choose a species" else null
        val ageError = Validators.age(s.age)
        val weightError = Validators.weight(s.weight)
        if (listOf(nameError, speciesError, ageError, weightError).any { it != null }) {
            _state.update { it.copy(nameError = nameError, speciesError = speciesError, ageError = ageError, weightError = weightError) }
            return
        }

        _state.update { it.copy(isSaving = true, formError = null) }
        viewModelScope.launch {
            val importedPaths = mutableListOf<String>()
            var committed = false
            try {
                val ownerId = authRepository.currentUser.first()?.id
                    ?: throw IllegalStateException("You need to be logged in to save a pet.")
                if (existingPet != null && existingPet?.ownerId != ownerId) {
                    throw IllegalStateException("You do not have permission to edit this pet.")
                }

                val photoPath = when {
                    s.pendingPhotoUri != null -> imageStorage.persist(s.pendingPhotoUri)?.also(importedPaths::add)
                        ?: throw IllegalStateException("The photo could not be saved. Try another image.")
                    s.photoRemoved -> null
                    else -> s.existingPhotoPath
                }
                val galleryPaths = buildList {
                    addAll(s.visibleGalleryPhotoPaths)
                    s.pendingGalleryPhotoUris.forEach { uri ->
                        val path = imageStorage.persist(uri)
                            ?: throw IllegalStateException("A gallery photo could not be saved. Try another image.")
                        importedPaths += path
                        add(path)
                    }
                }

                val pet = Pet(
                    id = existingPet?.id ?: 0L,
                    ownerId = ownerId,
                    name = s.name.trim(),
                    species = s.species!!.name,
                    breed = s.breed.trim(),
                    ageYears = s.age.trim().toIntOrNull(),
                    weightKg = s.weight.trim().toDoubleOrNull(),
                    diet = s.diet.trim(),
                    allergies = s.allergies.trim(),
                    vaccinations = s.vaccinations.trim(),
                    favouriteToys = s.favouriteToys.trim(),
                    notes = s.notes.trim(),
                    photoPath = photoPath,
                    galleryPhotoPaths = PetPhotoPaths.encode(galleryPaths),
                    createdAt = existingPet?.createdAt ?: System.currentTimeMillis()
                )
                // Room has committed the row before save() returns.  Finish
                // that boundary non-cancellably so a cancellation cannot
                // delete a newly referenced image as if the write failed.
                val id = withContext(NonCancellable) {
                    petRepository.save(pet).also { committed = true }
                }
                importedPaths.clear()
                _state.update {
                    it.copy(
                        isSaving = false,
                        savedPetId = id,
                        existingPhotoPath = photoPath,
                        existingGalleryPhotoPaths = galleryPaths,
                        pendingPhotoUri = null,
                        pendingGalleryPhotoUris = emptyList(),
                        photoRemoved = false,
                        removedGalleryPhotoPaths = emptySet()
                    )
                }
            } catch (e: CancellationException) {
                if (!committed) {
                    withContext(NonCancellable) {
                        importedPaths.forEach { imageStorage.delete(it) }
                    }
                }
                throw e
            } catch (e: Exception) {
                // The repository owns old-file cleanup after a committed
                // update. These are newly imported files and must be removed
                // when persistence fails so a failed save cannot leak files.
                if (!committed) {
                    withContext(NonCancellable) {
                        importedPaths.forEach { imageStorage.delete(it) }
                    }
                }
                _state.update { it.copy(isSaving = false, formError = e.userMessage()) }
            }
        }
    }
}
