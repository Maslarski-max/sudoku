package com.maslarski.sudoku.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.FirebaseFunctionsException
import com.google.firebase.firestore.FirebaseFirestore
import com.maslarski.sudoku.data.local.LeaderboardDao
import com.maslarski.sudoku.domain.model.DisplayNameError
import com.maslarski.sudoku.domain.model.DisplayNameException
import com.maslarski.sudoku.domain.model.DisplayNameRules
import com.maslarski.sudoku.domain.model.Score
import com.maslarski.sudoku.domain.repository.AuthRepository
import com.maslarski.sudoku.domain.repository.ProfileRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeout
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

internal fun displayNameErrorFor(code: String, details: Any?): DisplayNameError =
    when (code) {
        FirebaseFunctionsException.Code.INVALID_ARGUMENT.name -> {
            val reason = (details as? Map<*, *>)?.get("reason") as? String
            DisplayNameError.entries.firstOrNull {
                it.name == reason && it != DisplayNameError.RATE_LIMIT && it != DisplayNameError.NETWORK
            } ?: DisplayNameError.BLOCKED
        }
        FirebaseFunctionsException.Code.RESOURCE_EXHAUSTED.name -> DisplayNameError.RATE_LIMIT
        else -> DisplayNameError.NETWORK
    }

@Singleton
class FirebaseProfileRepository @Inject constructor(
    private val firestore: FirebaseFirestore,
    private val functions: FirebaseFunctions,
    private val authRepository: AuthRepository,
    private val dataStore: DataStore<Preferences>,
    private val leaderboardDao: LeaderboardDao,
) : ProfileRepository {
    private val nameRevision = AtomicLong()

    override val displayName: Flow<String?> = dataStore.data.map { preferences ->
        val uid = authRepository.currentUserId
        if (uid == null || preferences[KEY_UID] != uid) {
            null
        } else {
            preferences[KEY_DISPLAY_NAME] ?: Score.displayNameFor(uid)
        }
    }

    override suspend fun displayNameFor(uid: String): String {
        val preferences = dataStore.data.first()
        return if (preferences[KEY_UID] == uid) {
            preferences[KEY_DISPLAY_NAME] ?: Score.displayNameFor(uid)
        } else {
            Score.displayNameFor(uid)
        }
    }

    override suspend fun refresh() {
        var signedInUid: String? = null
        try {
            val uid = authRepository.ensureSignedIn()
            signedInUid = uid
            dataStore.edit { preferences ->
                if (preferences[KEY_UID] != uid) preferences.remove(KEY_DISPLAY_NAME)
                preferences[KEY_UID] = uid
            }
            val startRevision = nameRevision.get()
            val snapshot = withTimeout(NETWORK_TIMEOUT_MILLIS) {
                firestore.collection(COLLECTION_PROFILES).document(uid).get().await()
            }
            val name = snapshot.getString(FIELD_DISPLAY_NAME)
            dataStore.edit { preferences ->
                preferences[KEY_UID] = uid
                if (nameRevision.get() == startRevision) {
                    if (name == null) preferences.remove(KEY_DISPLAY_NAME)
                    else preferences[KEY_DISPLAY_NAME] = name
                }
            }
        } catch (error: CancellationException) {
            if (error is TimeoutCancellationException) return
            throw error
        } catch (_: Exception) {
            signedInUid?.let { uid ->
                try {
                    dataStore.edit { preferences ->
                        if (preferences[KEY_UID] != uid) preferences.remove(KEY_DISPLAY_NAME)
                        preferences[KEY_UID] = uid
                    }
                } catch (error: CancellationException) {
                    throw error
                } catch (_: Exception) {
                    Unit
                }
            }
        }
    }

    override suspend fun setDisplayName(raw: String): Result<String> {
        val name = DisplayNameRules.normalize(raw)
        DisplayNameRules.validate(name)?.let { error ->
            return Result.failure(DisplayNameException(error))
        }

        return try {
            val uid = authRepository.ensureSignedIn()
            val result = withTimeout(NETWORK_TIMEOUT_MILLIS) {
                functions.getHttpsCallable(CALLABLE_NAME)
                    .call(mapOf("name" to name))
                    .await()
            }
            val returnedName = (result.data as? Map<*, *>)?.get(FIELD_DISPLAY_NAME) as? String ?: name
            dataStore.edit { preferences ->
                nameRevision.incrementAndGet()
                preferences[KEY_UID] = uid
                preferences[KEY_DISPLAY_NAME] = returnedName
            }
            leaderboardDao.renameUser(uid, returnedName)
            Result.success(returnedName)
        } catch (error: FirebaseFunctionsException) {
            Result.failure(DisplayNameException(displayNameErrorFor(error.code.name, error.details)))
        } catch (error: CancellationException) {
            if (error is TimeoutCancellationException) {
                Result.failure(DisplayNameException(DisplayNameError.NETWORK))
            } else {
                throw error
            }
        } catch (_: Exception) {
            Result.failure(DisplayNameException(DisplayNameError.NETWORK))
        }
    }

    private companion object {
        const val NETWORK_TIMEOUT_MILLIS = 15_000L
        const val CALLABLE_NAME = "setDisplayName"
        const val COLLECTION_PROFILES = "profiles"
        const val FIELD_DISPLAY_NAME = "displayName"
        val KEY_UID = stringPreferencesKey("profile_uid")
        val KEY_DISPLAY_NAME = stringPreferencesKey("profile_display_name")
    }
}
