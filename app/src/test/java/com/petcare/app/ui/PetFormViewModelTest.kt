package com.petcare.app.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.petcare.app.data.TestEnvironment
import com.petcare.app.data.awaitCondition
import com.petcare.app.model.Pet
import com.petcare.app.model.Species
import com.petcare.app.navigation.Routes
import com.petcare.app.ui.screens.pets.PetFormViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PetFormViewModelTest {
    private lateinit var environment: TestEnvironment

    @Before
    fun setUp() {
        environment = TestEnvironment(ApplicationProvider.getApplicationContext<Context>())
        runBlocking { environment.auth.logout() }
    }

    @After
    fun tearDown() {
        runBlocking { environment.auth.logout() }
        environment.close()
    }

    @Test
    fun `new pet form exposes fields validates boundaries and saves the row`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val user = environment.auth.register("Pet Owner", "pet-form@example.com", "secret1").getOrThrow()
        val viewModel = PetFormViewModel(
            environment.auth,
            environment.pets,
            environment.imageStorage,
            SavedStateHandle()
        )
        try {
            assertFalse(viewModel.state.value.isEdit)
            assertFalse(viewModel.state.value.isLoading)

            viewModel.save()
            assertEquals("Pet name is required", viewModel.state.value.nameError)
            assertEquals("Choose a species", viewModel.state.value.speciesError)
            assertEquals(null, viewModel.state.value.ageError)
            assertEquals(null, viewModel.state.value.weightError)

            viewModel.onNameChange(" Milo ")
            viewModel.onSpeciesChange(Species.DOG)
            viewModel.onBreedChange("Collie")
            viewModel.onAgeChange("four")
            viewModel.onWeightChange("heavy")
            viewModel.save()
            assertNotNull(viewModel.state.value.ageError)
            assertNotNull(viewModel.state.value.weightError)
            assertFalse(viewModel.state.value.isSaving)

            viewModel.onAgeChange("4")
            viewModel.onWeightChange("18.5")
            viewModel.onDietChange("Kibble")
            viewModel.onAllergiesChange("None")
            viewModel.onVaccinationsChange("Rabies")
            viewModel.onToysChange("Ball")
            viewModel.onNotesChange("Friendly")
            viewModel.save()
            awaitCondition(description = "new pet save") {
                viewModel.state.value.savedPetId != null
            }

            val saved = environment.pets.getPet(viewModel.state.value.savedPetId!!)!!
            assertEquals(user.id, saved.ownerId)
            assertEquals("Milo", saved.name)
            assertEquals(Species.DOG.name, saved.species)
            assertEquals(4, saved.ageYears)
            assertEquals(18.5, saved.weightKg!!, 0.0)
            assertEquals("Kibble", saved.diet)
            assertEquals("Friendly", saved.notes)
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `gallery selection is capped at ten and cancellation restores persisted photos`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        environment.auth.register("Gallery Owner", "gallery@example.com", "secret1")
        val existingId = environment.pets.save(
            Pet(
                ownerId = environment.auth.signedInUserId()!!,
                name = "Gallery Pet",
                species = Species.CAT.name,
                photoPath = "/images/primary.jpg",
                galleryPhotoPaths = """["/images/old-1.jpg","/images/old-2.jpg"]"""
            )
        )
        val viewModel = PetFormViewModel(
            environment.auth,
            environment.pets,
            environment.imageStorage,
            SavedStateHandle()
        )
        val editViewModel = PetFormViewModel(
            environment.auth,
            environment.pets,
            environment.imageStorage,
            SavedStateHandle(mapOf(Routes.ARG_PET_ID to existingId))
        )
        try {
            val uris = (1..12).map { Uri.parse("content://gallery/$it") }
            viewModel.onGalleryPhotosPicked(uris)
            assertEquals(10, viewModel.state.value.pendingGalleryPhotoUris.size)
            assertEquals(
                "You can add up to 10 photos total.",
                viewModel.state.value.formError
            )
            viewModel.onGalleryPhotosPicked(listOf(uris.first()))
            assertEquals(10, viewModel.state.value.pendingGalleryPhotoUris.size)

            awaitCondition(description = "existing pet form hydration") {
                !editViewModel.state.value.isLoading
            }
            assertEquals("/images/primary.jpg", editViewModel.state.value.visiblePhotoPath)
            assertEquals(
                listOf("/images/old-1.jpg", "/images/old-2.jpg"),
                editViewModel.state.value.visibleGalleryPhotoPaths
            )
            editViewModel.onPhotoRemoved()
            editViewModel.onGalleryPhotoRemoved("/images/old-1.jpg")
            editViewModel.onGalleryPhotosPicked(
                listOf(Uri.parse("content://gallery/new"))
            )
            assertNull(editViewModel.state.value.visiblePhotoPath)
            assertEquals(listOf("/images/old-2.jpg"), editViewModel.state.value.visibleGalleryPhotoPaths)

            editViewModel.discardPendingPhotos()
            assertEquals("/images/primary.jpg", editViewModel.state.value.visiblePhotoPath)
            assertEquals(
                listOf("/images/old-1.jpg", "/images/old-2.jpg"),
                editViewModel.state.value.visibleGalleryPhotoPaths
            )
            assertTrue(editViewModel.state.value.pendingGalleryPhotoUris.isEmpty())
        } finally {
            viewModel.viewModelScope.cancel()
            editViewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `edit pet hydrates and persists all changed fields`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = environment.auth.register("Edit Owner", "pet-edit@example.com", "secret1").getOrThrow()
        val petId = environment.pets.save(
            Pet(
                ownerId = owner.id,
                name = "Before",
                species = Species.RABBIT.name,
                breed = "Lionhead",
                ageYears = 2,
                weightKg = 1.7,
                diet = "Hay",
                notes = "Quiet"
            )
        )
        val viewModel = PetFormViewModel(
            environment.auth,
            environment.pets,
            environment.imageStorage,
            SavedStateHandle(mapOf(Routes.ARG_PET_ID to petId))
        )
        try {
            awaitCondition(description = "pet edit hydration") {
                !viewModel.state.value.isLoading
            }
            assertEquals("Before", viewModel.state.value.name)
            assertEquals(Species.RABBIT, viewModel.state.value.species)
            assertEquals("1.7", viewModel.state.value.weight)

            viewModel.onNameChange("After")
            viewModel.onSpeciesChange(Species.OTHER)
            viewModel.onWeightChange("2")
            viewModel.onNotesChange("Updated")
            viewModel.save()
            awaitCondition(description = "edited pet save") {
                viewModel.state.value.savedPetId == petId
            }

            val saved = environment.pets.getPet(petId)!!
            assertEquals("After", saved.name)
            assertEquals(Species.OTHER.name, saved.species)
            assertEquals(2.0, saved.weightKg!!, 0.0)
            assertEquals("Updated", saved.notes)
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `editing a missing pet reports a load error`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        environment.auth.register("Missing Pet", "missing-pet@example.com", "secret1")
        val viewModel = PetFormViewModel(
            environment.auth,
            environment.pets,
            environment.imageStorage,
            SavedStateHandle(mapOf(Routes.ARG_PET_ID to 9999L))
        )
        try {
            awaitCondition(description = "missing pet load") {
                !viewModel.state.value.isLoading
            }
            assertEquals("This pet no longer exists.", viewModel.state.value.loadError)
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }
}