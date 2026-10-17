package com.petcare.app.data

import android.content.Context
import android.content.ContextWrapper
import androidx.room.Room
import com.petcare.app.data.local.PetCareDatabase
import com.petcare.app.data.preferences.UserPreferences
import com.petcare.app.data.repository.AuthRepository
import com.petcare.app.data.repository.ExpenseRepository
import com.petcare.app.data.repository.PetRepository
import com.petcare.app.data.repository.TaskRepository
import com.petcare.app.util.ImageStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.atomic.AtomicLong

/**
 * A small real-data fixture shared by the repository and ViewModel tests.
 *
 * The production Application starts seed/reminder work, so these tests use a
 * plain Robolectric Application and wire the same repositories around an
 * in-memory Room database. The preferences context gets its own files
 * directory so a test cannot reuse a checked-in or application preference file.
 */
class TestEnvironment(context: Context) : AutoCloseable {
    private val preferenceRoot = File(
        context.cacheDir,
        "petcare-test-preferences-${nextPreferenceDirectory.incrementAndGet()}"
    ).apply { mkdirs() }

    private val preferenceContext = IsolatedPreferencesContext(context, preferenceRoot)
    val preferencesContextForTest: Context get() = preferenceContext

    val database: PetCareDatabase = Room.inMemoryDatabaseBuilder(
        context,
        PetCareDatabase::class.java
    ).allowMainThreadQueries().build()

    init {
        resetProductionPreferenceDelegate()
    }

    val preferences = UserPreferences(preferenceContext)
    val auth = AuthRepository(database.userDao(), preferences)
    val imageStorage = ImageStorage(context)
    val pets = PetRepository(database.petDao(), database.careTaskDao(), imageStorage, auth)
    val tasks = TaskRepository(database.careTaskDao(), imageStorage, auth)
    val expenses = ExpenseRepository(database.expenseDao(), auth)

    override fun close() {
        database.close()
        preferenceRoot.deleteRecursively()
    }

    private class IsolatedPreferencesContext(
        base: Context,
        private val filesRoot: File
    ) : ContextWrapper(base) {
        override fun getApplicationContext(): Context = this
        override fun getFilesDir(): File = filesRoot
    }

    private companion object {
        val nextPreferenceDirectory = AtomicLong()
    }
}

/**
 * UserPreferences intentionally owns a process singleton DataStore in
 * production. Test fixtures reset only that private delegate so each fixture
 * can point it at its unique directory; no production test hook is added.
 */
private fun resetProductionPreferenceDelegate() {
    val holder = Class.forName("com.petcare.app.data.preferences.UserPreferencesKt")
    val delegateField = holder.declaredFields.firstOrNull {
        it.name == "dataStore" || it.name == "dataStore\$delegate"
    } ?: error("UserPreferences DataStore delegate field was not found")
    val delegate = delegateField.apply {
        isAccessible = true
    }.get(null)
    delegate.javaClass.getDeclaredField("INSTANCE").apply {
        isAccessible = true
        set(delegate, null)
    }
}

/**
 * DataStore and Room use real executors even when a ViewModel uses a virtual
 * Main dispatcher. Await the observed predicate while pumping immediate Main
 * work instead of advancing virtual time through the date-refresh timer.
 */
@OptIn(ExperimentalCoroutinesApi::class)
suspend fun TestScope.awaitCondition(
    timeoutMillis: Long = 5_000L,
    description: String,
    predicate: () -> Boolean
) {
    val deadline = System.nanoTime() + timeoutMillis * 1_000_000L
    while (!predicate()) {
        runCurrent()
        if (predicate()) return
        if (System.nanoTime() >= deadline) {
            throw AssertionError("Timed out waiting for $description")
        }
        withContext(Dispatchers.IO) { Thread.sleep(10L) }
    }
}