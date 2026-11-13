package com.pushitup.core.di

import android.content.Context
import androidx.room.Room
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.pushitup.data.local.database.PushItDatabase
import com.pushitup.data.local.dao.DailyLogDao
import com.pushitup.data.local.dao.SessionDao
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
    fun provideDatabase(@ApplicationContext context: Context): PushItDatabase =
        Room.databaseBuilder(context, PushItDatabase::class.java, "pushit.db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideSessionDao(db: PushItDatabase): SessionDao = db.sessionDao()

    @Provides
    fun provideDailyLogDao(db: PushItDatabase): DailyLogDao = db.dailyLogDao()

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseStorage(): FirebaseStorage = FirebaseStorage.getInstance()
}
