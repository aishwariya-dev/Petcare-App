package com.petcare.app.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.CameraAlt
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.petcare.app.model.PetPhotoPaths
import com.petcare.app.ui.theme.Spacing
import com.petcare.app.util.ImageStorage
import java.io.File

/**
 * Photo chooser backed by the Android photo picker and the system camera.
 * Neither flow needs broad storage permission; camera output is written to a
 * FileProvider URI in the app cache and is copied to app storage by the caller.
 */
@Composable
fun ImagePickerField(
    imagePath: String?,
    pendingUri: Uri?,
    onImagePicked: (Uri) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
    label: String = "Add photo",
    size: Dp = 112.dp,
    circular: Boolean = true,
    enabled: Boolean = true,
    onPickerError: (String) -> Unit = {},
    placeholder: @Composable () -> Unit = {
        Icon(
            imageVector = Icons.Outlined.AddAPhoto,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(32.dp)
        )
    }
) {
    val context = LocalContext.current
    var captureUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var replacedCaptureUriString by rememberSaveable { mutableStateOf<String?>(null) }
    val captureUri = captureUriString?.let(Uri::parse)
    val replacedCaptureUri = replacedCaptureUriString?.let(Uri::parse)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) {
            ImageStorage.deleteCapture(context, captureUri)
            ImageStorage.deleteCapture(context, replacedCaptureUri)
            captureUriString = null
            replacedCaptureUriString = null
            if (enabled) onImagePicked(uri)
        }
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = captureUri
        if (saved && uri != null && enabled) {
            ImageStorage.deleteCapture(context, replacedCaptureUri)
            captureUriString = null
            replacedCaptureUriString = null
            onImagePicked(uri)
        } else {
            ImageStorage.deleteCapture(context, uri)
            captureUriString = replacedCaptureUriString
            replacedCaptureUriString = null
        }
    }

    fun openPicker() {
        if (!enabled) return
        launcher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    fun openCamera() {
        if (!enabled) return
        val uri = try {
            ImageStorage.createCaptureUri(context)
        } catch (error: Exception) {
            onPickerError(error.message ?: "Camera capture is not available.")
            return
        }
        replacedCaptureUriString = captureUriString
        captureUriString = uri.toString()
        cameraLauncher.launch(uri)
    }

    val shape = if (circular) CircleShape else MaterialTheme.shapes.medium
    val model: Any? = pendingUri ?: imagePath?.let { File(it) }?.takeIf { it.exists() }
    var imageLoadFailed by remember(model) { mutableStateOf(false) }

    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .size(size)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, shape)
                .clickable(enabled = enabled, onClickLabel = label) {
                    openPicker()
                },
            contentAlignment = Alignment.Center
        ) {
            if (model != null && !imageLoadFailed) {
                AsyncImage(
                    model = model,
                    contentDescription = "Selected photo",
                    contentScale = ContentScale.Crop,
                    onError = { imageLoadFailed = true },
                    modifier = Modifier.matchParentSize()
                )
            } else {
                placeholder()
            }
        }
        Spacer(Modifier.height(Spacing.small))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = ::openPicker, enabled = enabled) {
                Text(if (model != null) "Change photo" else label)
            }
            TextButton(onClick = ::openCamera, enabled = enabled) {
                Icon(Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.xs))
                Text("Camera")
            }
            if (model != null) {
                Spacer(Modifier.width(Spacing.xs))
                TextButton(onClick = {
                    ImageStorage.deleteCapture(context, captureUri)
                    ImageStorage.deleteCapture(context, replacedCaptureUri)
                    captureUriString = null
                    replacedCaptureUriString = null
                    onRemove()
                }, enabled = enabled) { Text("Remove") }
            }
        }
    }
}

/**
 * Additional pet photos. Existing paths are only removed from the database
 * after the parent record is successfully persisted; pending URIs are copied
 * by the form ViewModel at save time.
 */
@Composable
fun ImageGalleryField(
    imagePaths: List<String>,
    pendingUris: List<Uri>,
    onImagesPicked: (List<Uri>) -> Unit,
    onRemoveImage: (String) -> Unit,
    onRemovePending: (Uri) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onPickerError: (String) -> Unit = {}
) {
    val context = LocalContext.current
    var captureUriString by rememberSaveable { mutableStateOf<String?>(null) }
    var replacedCaptureUriString by rememberSaveable { mutableStateOf<String?>(null) }
    val captureUri = captureUriString?.let(Uri::parse)
    val replacedCaptureUri = replacedCaptureUriString?.let(Uri::parse)
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia(maxItems = MAX_GALLERY_PICK)
    ) { uris ->
        if (uris.isNotEmpty()) {
            ImageStorage.deleteCapture(context, captureUri)
            ImageStorage.deleteCapture(context, replacedCaptureUri)
            captureUriString = null
            replacedCaptureUriString = null
            if (enabled) onImagesPicked(uris)
        }
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        val uri = captureUri
        if (saved && uri != null && enabled) {
            ImageStorage.deleteCapture(context, replacedCaptureUri)
            captureUriString = null
            replacedCaptureUriString = null
            onImagesPicked(listOf(uri))
        } else {
            ImageStorage.deleteCapture(context, uri)
            captureUriString = replacedCaptureUriString
            replacedCaptureUriString = null
        }
    }

    fun openPicker() {
        if (!enabled) return
        picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    fun openCamera() {
        if (!enabled) return
        val uri = try {
            ImageStorage.createCaptureUri(context)
        } catch (error: Exception) {
            onPickerError(error.message ?: "Camera capture is not available.")
            return
        }
        replacedCaptureUriString = captureUriString
        captureUriString = uri.toString()
        camera.launch(uri)
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            "Up to ${PetPhotoPaths.MAX_TOTAL_PHOTOS} photos total",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (imagePaths.isNotEmpty() || pendingUris.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(Spacing.small)
            ) {
                imagePaths.forEach { path ->
                    GalleryThumbnail(
                        model = File(path).takeIf { it.exists() },
                        contentDescription = "Pet photo",
                        onRemove = { if (enabled) onRemoveImage(path) },
                        enabled = enabled
                    )
                }
                pendingUris.forEach { uri ->
                    GalleryThumbnail(
                        model = uri,
                        contentDescription = "Pending pet photo",
                        onRemove = { if (enabled) onRemovePending(uri) },
                        enabled = enabled
                    )
                }
            }
            Spacer(Modifier.height(Spacing.small))
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = ::openPicker, enabled = enabled) {
                Icon(Icons.Outlined.AddAPhoto, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.xs))
                Text("Add photos")
            }
            TextButton(onClick = ::openCamera, enabled = enabled) {
                Icon(Icons.Outlined.CameraAlt, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(Spacing.xs))
                Text("Camera")
            }
        }
    }
}

private const val MAX_GALLERY_PICK = PetPhotoPaths.MAX_TOTAL_PHOTOS

@Composable
private fun GalleryThumbnail(
    model: Any?,
    contentDescription: String,
    onRemove: () -> Unit,
    enabled: Boolean
) {
    Box(
        modifier = Modifier
            .size(88.dp)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, MaterialTheme.shapes.small)
    ) {
        if (model != null) {
            AsyncImage(
                model = model,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize()
            )
        }
        IconButton(
            onClick = onRemove,
            enabled = enabled,
            modifier = Modifier.align(Alignment.TopEnd).size(30.dp)
        ) {
            Icon(
                Icons.Outlined.Close,
                contentDescription = "Remove photo",
                tint = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}
