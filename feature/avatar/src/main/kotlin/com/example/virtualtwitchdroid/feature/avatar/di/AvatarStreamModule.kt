package com.example.virtualtwitchdroid.feature.avatar.di

import com.example.virtualtwitchdroid.core.common.media.AvatarStreamSource
import com.example.virtualtwitchdroid.feature.avatar.stream.AvatarStreamController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** The app-wide avatar video source the publish feature consumes (features never depend on each other). */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class AvatarStreamModule {
    @Binds
    abstract fun bindAvatarStreamSource(impl: AvatarStreamController): AvatarStreamSource
}
