package com.maslarski.sudoku.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.room.Room
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.maslarski.sudoku.data.billing.PlayBillingRepository
import com.maslarski.sudoku.data.local.LeaderboardDao
import com.maslarski.sudoku.data.local.SavedGameDao
import com.maslarski.sudoku.data.local.SudokuDatabase
import com.maslarski.sudoku.data.repository.DataStoreLivesRepository
import com.maslarski.sudoku.data.repository.FirebaseAuthRepository
import com.maslarski.sudoku.data.repository.FirestoreLeaderboardRepository
import com.maslarski.sudoku.data.repository.RoomGameRepository
import com.maslarski.sudoku.domain.engine.GameEngine
import com.maslarski.sudoku.domain.engine.HintEngine
import com.maslarski.sudoku.domain.engine.SudokuGenerator
import com.maslarski.sudoku.domain.repository.AuthRepository
import com.maslarski.sudoku.domain.repository.BillingRepository
import com.maslarski.sudoku.domain.repository.GameRepository
import com.maslarski.sudoku.domain.repository.LeaderboardRepository
import com.maslarski.sudoku.domain.repository.LivesRepository
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
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Singleton
    fun provideClock(): () -> Long = System::currentTimeMillis

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SudokuDatabase =
        Room.databaseBuilder(context, SudokuDatabase::class.java, "sudoku.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

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
    @Binds abstract fun bindAuthRepository(impl: FirebaseAuthRepository): AuthRepository
    @Binds abstract fun bindLeaderboardRepository(impl: FirestoreLeaderboardRepository): LeaderboardRepository
    @Binds abstract fun bindBillingRepository(impl: PlayBillingRepository): BillingRepository
}
