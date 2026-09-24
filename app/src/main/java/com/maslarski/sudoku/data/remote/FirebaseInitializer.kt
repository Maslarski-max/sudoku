package com.maslarski.sudoku.data.remote

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.firestoreSettings
import com.google.firebase.firestore.memoryCacheSettings
import com.maslarski.sudoku.BuildConfig

/**
 * Sets up Firebase once per process.
 *
 * - With `google-services.json` present, the Google Services plugin auto-initialises the default app.
 * - Without it (typical for local development), debug builds create the default app programmatically
 *   with placeholder options. The emulators do not validate these values, so Auth and Firestore work
 *   end-to-end against `firebase emulators:start` with no cloud project at all.
 * - When [BuildConfig.USE_FIREBASE_EMULATOR] is true, both SDKs are pointed at the emulator host.
 */
object FirebaseInitializer {

    private const val TAG = "FirebaseInitializer"
    private const val EMULATOR_PROJECT_ID = "sudoku-local"

    fun initialize(context: Context) {
        if (FirebaseApp.getApps(context).isEmpty()) {
            if (!BuildConfig.USE_FIREBASE_EMULATOR) {
                Log.w(TAG, "google-services.json missing and emulator disabled; Firebase features will be unavailable")
                return
            }
            val options = FirebaseOptions.Builder()
                .setProjectId(EMULATOR_PROJECT_ID)
                .setApplicationId("1:000000000000:android:0000000000000000")
                .setApiKey("fake-api-key-for-emulator")
                .build()
            FirebaseApp.initializeApp(context, options)
        }

        if (BuildConfig.USE_FIREBASE_EMULATOR) {
            val host = BuildConfig.FIREBASE_EMULATOR_HOST
            FirebaseAuth.getInstance().useEmulator(host, BuildConfig.FIREBASE_AUTH_EMULATOR_PORT)
            FirebaseFirestore.getInstance().apply {
                useEmulator(host, BuildConfig.FIREBASE_FIRESTORE_EMULATOR_PORT)
                firestoreSettings = firestoreSettings {
                    setLocalCacheSettings(memoryCacheSettings {})
                }
            }
            Log.i(TAG, "Using Firebase emulators at $host")
        }
    }

    /** True when a default FirebaseApp exists (either from google-services.json or the emulator fallback). */
    fun isAvailable(context: Context): Boolean = FirebaseApp.getApps(context).isNotEmpty()
}
