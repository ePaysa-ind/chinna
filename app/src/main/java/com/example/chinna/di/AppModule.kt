package com.example.chinna.di

import android.content.Context
import androidx.room.Room
import com.example.chinna.data.local.PrefsManager
import com.example.chinna.data.local.UserDao
import com.example.chinna.data.local.database.AppDatabase
import com.example.chinna.data.remote.AuthService
import com.example.chinna.data.remote.GeminiService
import com.example.chinna.data.remote.WeatherService
import com.example.chinna.data.repository.UserRepository
import com.example.chinna.util.NetworkMonitor
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase
import com.google.firebase.FirebaseApp
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    @Provides
    @Singleton
    fun provideAuthService(): AuthService {
        return AuthService()
    }
    
    @Provides
    @Singleton
    fun provideGeminiService(): GeminiService {
        return GeminiService()
    }
    
    @Provides
    @Singleton
    fun providePrefsManager(@ApplicationContext context: Context): PrefsManager {
        return PrefsManager(context)
    }
    
    @Provides
    @Singleton
    fun provideNetworkMonitor(@ApplicationContext context: Context): NetworkMonitor {
        return NetworkMonitor(context)
    }
    
    @Provides
    @Singleton
    fun provideDatabaseFixer(@ApplicationContext context: Context): com.example.chinna.util.DatabaseFixer {
        return com.example.chinna.util.DatabaseFixer(context)
    }
    
    @Provides
    @Singleton
    fun provideWeatherService(@ApplicationContext context: Context): WeatherService {
        return WeatherService(context)
    }
    
    @Provides
    @Singleton
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase {
        return Room.databaseBuilder(
            context,
            AppDatabase::class.java,
            "chinna_database"
        )
        .addMigrations(AppDatabase.MIGRATION_1_2, AppDatabase.MIGRATION_2_3)
        .fallbackToDestructiveMigration() // In case migration fails, recreate tables
        .allowMainThreadQueries() // Temporary for debugging
        .build()
    }
    
    @Provides
    @Singleton
    fun provideUserDao(database: AppDatabase): UserDao {
        return database.userDao()
    }
    
    @Provides
    @Singleton
    fun provideUserRepository(
        userDao: UserDao,
        prefsManager: PrefsManager
    ): UserRepository {
        return UserRepository(userDao, prefsManager)
    }
    
    @Provides
    @Singleton
    fun provideFirebaseOptionsProvider(): FirebaseOptionsProvider {
        return FirebaseOptionsProvider()
    }
    
    @Provides
    @Singleton
    fun provideFirebaseAuth(
        @ApplicationContext context: Context,
        firebaseOptionsProvider: FirebaseOptionsProvider
    ): FirebaseAuth {
        // Ensure Firebase is initialized with secure API key before getting auth
        try {
            val initialized = firebaseOptionsProvider.initializeFirebaseWithSecureKey(context)
            if (!initialized) {
                android.util.Log.w("AppModule", "Secure Firebase initialization failed, falling back to default")
            }
            
            return Firebase.auth
        } catch (e: Exception) {
            android.util.Log.e("AppModule", "Error providing FirebaseAuth, returning default instance", e)
            // In case of failure, return a default instance
            // This might not work for actual auth but prevents app crashes
            return FirebaseAuth.getInstance()
        }
    }
}