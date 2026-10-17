package com.petcare.app.ui

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.test.core.app.ApplicationProvider
import com.petcare.app.data.TestEnvironment
import com.petcare.app.data.awaitCondition
import com.petcare.app.model.Species
import com.petcare.app.model.galleryPaths
import com.petcare.app.navigation.Routes
import com.petcare.app.ui.screens.pets.PetFormViewModel
import com.petcare.app.ui.screens.settings.ProfileViewModel
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * End-to-end photo persistence checks using the real Room repositories and
 * content URIs rather than replacing ImageStorage with a test double.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class PhotoViewModelPersistenceTest {
    private lateinit var environment: TestEnvironment
    private val context: Application
        get() = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        environment = TestEnvironment(context)
        runBlocking { environment.auth.logout() }
    }

    @After
    fun tearDown() {
        runBlocking { environment.auth.logout() }
        environment.close()
    }

    @Test
    fun `profile photo create and edit round trip stores new image and removes old image`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val user = environment.auth.register(
            "Photo Profile",
            "photo-profile@example.com",
            "secret1"
        ).getOrThrow()
        val firstUri = Uri.parse("content://photo-roundtrip/profile-first")
        val secondUri = Uri.parse("content://photo-roundtrip/profile-second")
        registerPng(firstUri, width = 8, height = 5)
        registerPng(secondUri, width = 6, height = 9)
        val viewModel = ProfileViewModel(environment.auth, environment.imageStorage)
        try {
            awaitCondition(description = "profile photo form load") {
                viewModel.state.value.loaded && viewModel.state.value.user?.id == user.id
            }

            var firstSave = false
            viewModel.onPhotoPicked(firstUri)
            viewModel.save(onSaved = { firstSave = true }, onError = {})
            awaitCondition(description = "profile photo create") { firstSave }
            awaitCondition(description = "profile photo user refresh") {
                viewModel.state.value.user?.photoPath != null
            }

            val firstPath = environment.database.userDao().findById(user.id)!!.photoPath
                ?: error("Profile photo path was not persisted")
            assertTrue(File(firstPath).exists())

            var secondSave = false
            viewModel.onPhotoPicked(secondUri)
            viewModel.save(onSaved = { secondSave = true }, onError = {})
            awaitCondition(description = "profile photo edit") { secondSave }

            val updated = environment.database.userDao().findById(user.id)!!
            assertNotNull(updated.photoPath)
            assertTrue(File(updated.photoPath!!).exists())
            assertTrue(updated.photoPath != firstPath)
            assertTrue(!File(firstPath).exists())
        } finally {
            viewModel.viewModelScope.cancel()
            Dispatchers.resetMain()
        }
    }

    @Test
    fun `pet primary and gallery photos create and edit round trip through Room`() = runTest {
        Dispatchers.setMain(StandardTestDispatcher(testScheduler))
        val owner = environment.auth.register(
            "Photo Pet Owner",
            "photo-pet@example.com",
            "secret1"
        ).getOrThrow()
        val primaryUri = Uri.parse("content://photo-roundtrip/pet-primary")
        val galleryUri = Uri.parse("content://photo-roundtrip/pet-gallery")
        val editedPrimaryUri = Uri.parse("content://photo-roundtrip/pet-primary-edited")
        val editedGalleryUri = Uri.parse("content://photo-roundtrip/pet-gallery-edited")
        registerPng(primaryUri, width = 11, height = 4)
        registerPng(galleryUri, width = 4, height = 11)
        registerPng(editedPrimaryUri, width = 9, height = 7)
        registerPng(editedGalleryUri, width = 5, height = 10)
        val createViewModel = PetFormViewModel(
            environment.auth,
            environment.pets,
            environment.imageStorage,
            SavedStateHandle()
        )
        var editViewModel: PetFormViewModel? = null
        try {
            createViewModel.onNameChange("Photo Pet")
            createViewModel.onSpeciesChange(Species.DOG)
            createViewModel.onPhotoPicked(primaryUri)
            createViewModel.onGalleryPhotosPicked(listOf(galleryUri))
            createViewModel.save()
            awaitCondition(description = "pet photo create") {
                createViewModel.state.value.savedPetId != null
            }

            val petId = createViewModel.state.value.savedPetId!!
            val created = environment.pets.getPet(petId)!!
            val primaryPath = created.photoPath ?: error("Pet primary photo was not persisted")
            val galleryPath = created.galleryPaths.single()
            assertTrue(File(primaryPath).exists())
            assertTrue(File(galleryPath).exists())

            editViewModel = PetFormViewModel(
                environment.auth,
                environment.pets,
                environment.imageStorage,
                SavedStateHandle(mapOf(Routes.ARG_PET_ID to petId))
            )
            awaitCondition(description = "pet photo edit load") {
                !editViewModel!!.state.value.isLoading
            }
            editViewModel!!.onPhotoPicked(editedPrimaryUri)
            editViewModel!!.onGalleryPhotoRemoved(galleryPath)
            editViewModel!!.onGalleryPhotosPicked(listOf(editedGalleryUri))
            editViewModel!!.save()
            awaitCondition(description = "pet photo edit") {
                editViewModel!!.state.value.savedPetId == petId
            }

            val edited = environment.pets.getPet(petId)!!
            assertNotNull(edited.photoPath)
            assertTrue(File(edited.photoPath!!).exists())
            assertTrue(edited.photoPath != primaryPath)
            assertEquals(1, edited.galleryPaths.size)
            assertTrue(File(edited.galleryPaths.single()).exists())
            assertTrue(edited.galleryPaths.single() != galleryPath)
            assertTrue(!File(primaryPath).exists())
            assertTrue(!File(galleryPath).exists())
        } finally {
            createViewModel.viewModelScope.cancel()
            editViewModel?.viewModelScope?.cancel()
            Dispatchers.resetMain()
        }
    }

    private fun registerPng(uri: Uri, width: Int, height: Int) {
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val bytes = try {
            ByteArrayOutputStream().use { output ->
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
        val input = object : ByteArrayInputStream(bytes) {
            override fun close() {
                super.close()
                reset()
            }
        }
        shadowOf(context.contentResolver).registerInputStream(uri, input)
    }
}