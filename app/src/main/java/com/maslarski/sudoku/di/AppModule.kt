package com.maslarski.sudoku.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.functions.FirebaseFunctions
import com.maslarski.sudoku.BuildConfig
import com.maslarski.sudoku.data.billing.FakeBillingRepository
import com.maslarski.sudoku.data.billing.PlayBillingRepository
import com.maslarski.sudoku.data.feedback.DeviceGameFeedback
import com.maslarski.sudoku.data.local.LeaderboardDao
import com.maslarski.sudoku.data.local.SavedGameDao
import com.maslarski.sudoku.data.local.SudokuDatabase
import com.maslarski.sudoku.data.repository.DataStoreLivesRepository
import com.maslarski.sudoku.data.repository.DataStoreDailyChallengeRepository
import com.maslarski.sudoku.data.repository.DataStoreTournamentPendingQueue
import com.maslarski.sudoku.data.repository.DataStoreTrophyRepository
import com.maslarski.sudoku.data.repository.FirestoreTournamentRepository
import com.maslarski.sudoku.data.repository.DataStoreReviewPromptRepository
import com.maslarski.sudoku.data.repository.DataStoreSettingsRepository
import com.maslarski.sudoku.data.repository.FirebaseAuthRepository
import com.maslarski.sudoku.data.repository.FirestoreLeaderboardRepository
import com.maslarski.sudoku.data.repository.RoomGameRepository
import com.maslarski.sudoku.data.time.SystemEpochClock
import com.maslarski.sudoku.domain.engine.GameEngine
import com.maslarski.sudoku.domain.engine.HintEngine
import com.maslarski.sudoku.domain.engine.SudokuGenerator
import com.maslarski.sudoku.domain.repository.AuthRepository
import com.maslarski.sudoku.domain.repository.BillingRepository
import com.maslarski.sudoku.domain.repository.DailyChallengeRepository
import com.maslarski.sudoku.domain.repository.TournamentPendingQueue
import com.maslarski.sudoku.domain.repository.TournamentRepository
import com.maslarski.sudoku.domain.repository.TrophyRepository
import com.maslarski.sudoku.domain.repository.GameRepository
import com.maslarski.sudoku.domain.repository.GameFeedback
import com.maslarski.sudoku.domain.repository.LeaderboardRepository
import com.maslarski.sudoku.domain.repository.LivesRepository
import com.maslarski.sudoku.domain.repository.ReviewPromptRepository
import com.maslarski.sudoku.domain.repository.SettingsRepository
import com.maslarski.sudoku.domain.time.EpochClock
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Provider
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @ApplicationScope
    @Provides
    @Singleton
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Singleton
    fun provideEpochClock(): EpochClock = SystemEpochClock

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SudokuDatabase =
        Room.databaseBuilder(context, SudokuDatabase::class.java, "sudoku.db")
            .addMigrations(MIGRATION_2_3)
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    /** pending_scores: one slot per category -> one slot per (category, uid). */
    private val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS pending_scores_new (" +
                    "category TEXT NOT NULL, uid TEXT NOT NULL, displayName TEXT NOT NULL, gridSize INTEGER NOT NULL, " +
                    "difficulty TEXT NOT NULL, timeMillis INTEGER NOT NULL, moves INTEGER NOT NULL, " +
                    "mistakes INTEGER NOT NULL, points INTEGER NOT NULL, completedAtEpochMillis INTEGER NOT NULL, " +
                    "PRIMARY KEY(category, uid))",
            )
            db.execSQL("INSERT INTO pending_scores_new SELECT * FROM pending_scores")
            db.execSQL("DROP TABLE pending_scores")
            db.execSQL("ALTER TABLE pending_scores_new RENAME TO pending_scores")
        }
    }

    @Provides
    fun provideSavedGameDao(db: SudokuDatabase): SavedGameDao = db.savedGameDao()

    @Provides
    fun provideLeaderboardDao(db: SudokuDatabase): LeaderboardDao = db.leaderboardDao()

    @Provides
    @Singleton
    fun providePreferences(@ApplicationContext context: Context): DataStore<Preferences> =
        PreferenceDataStoreFactory.create { context.preferencesDataStoreFile("sudoku_prefs") }

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseFunctions(): FirebaseFunctions = FirebaseFunctions.getInstance("europe-west1")

    @Provides
    @Singleton
    fun provideGenerator(): SudokuGenerator = SudokuGenerator()

    @Provides
    @Singleton
    fun provideGameEngine(): GameEngine = GameEngine()

    @Provides
    @Singleton
    fun provideHintEngine(): HintEngine = HintEngine()
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {
    @Binds abstract fun bindGameRepository(impl: RoomGameRepository): GameRepository
    @Binds abstract fun bindLivesRepository(impl: DataStoreLivesRepository): LivesRepository
    @Binds abstract fun bindReviewPromptRepository(impl: DataStoreReviewPromptRepository): ReviewPromptRepository
    @Binds abstract fun bindSettingsRepository(impl: DataStoreSettingsRepository): SettingsRepository
    @Binds abstract fun bindGameFeedback(impl: DeviceGameFeedback): GameFeedback
    @Binds abstract fun bindAuthRepository(impl: FirebaseAuthRepository): AuthRepository
    @Binds abstract fun bindLeaderboardRepository(impl: FirestoreLeaderboardRepository): LeaderboardRepository
    @Binds abstract fun bindDailyChallengeRepository(impl: DataStoreDailyChallengeRepository): DailyChallengeRepository
    @Binds abstract fun bindTournamentPendingQueue(impl: DataStoreTournamentPendingQueue): TournamentPendingQueue
    @Binds abstract fun bindTournamentRepository(impl: FirestoreTournamentRepository): TournamentRepository
    @Binds abstract fun bindTrophyRepository(impl: DataStoreTrophyRepository): TrophyRepository
}

@Module
@InstallIn(SingletonComponent::class)
object BillingModule {
    @Provides
    @Singleton
    fun provideBillingRepository(
        play: Provider<PlayBillingRepository>,
        fake: Provider<FakeBillingRepository>,
    ): BillingRepository = if (BuildConfig.FAKE_BILLING) fake.get() else play.get()
}
