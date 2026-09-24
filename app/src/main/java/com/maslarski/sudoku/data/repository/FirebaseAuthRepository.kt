package com.maslarski.sudoku.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.maslarski.sudoku.domain.repository.AuthRepository
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

/** Anonymous auth: every install gets a stable uid with no sign-up form. */
@Singleton
class FirebaseAuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
) : AuthRepository {

    private val signInMutex = Mutex()

    override val currentUserId: String? get() = auth.currentUser?.uid

    override suspend fun ensureSignedIn(): String = signInMutex.withLock {
        auth.currentUser?.uid ?: run {
            val result = auth.signInAnonymously().await()
            result.user?.uid ?: error("Anonymous sign-in returned no user")
        }
    }
}
