package com.example.virtualtwitchdroid.feature.avatar.di

import com.example.virtualtwitchdroid.feature.avatar.tracking.FaceTracker
import com.example.virtualtwitchdroid.feature.avatar.tracking.MediaPipeFaceTracker
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent

/** One [FaceTracker] (a loaded MediaPipe model) per ViewModel that asks for it. */
@Module
@InstallIn(ViewModelComponent::class)
internal abstract class AvatarModule {
    @Binds
    abstract fun bindFaceTracker(impl: MediaPipeFaceTracker): FaceTracker
}
