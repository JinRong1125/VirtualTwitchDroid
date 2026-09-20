package com.example.virtualtwitchdroid.feature.voice.di

import com.example.virtualtwitchdroid.core.common.media.MouthTrackSource
import com.example.virtualtwitchdroid.core.common.media.SpeechStreamSource
import com.example.virtualtwitchdroid.feature.voice.stream.ZundamonVoiceController
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/** The app-wide Zundamon voice: one controller behind both seams (features never depend on each other). */
@Module
@InstallIn(SingletonComponent::class)
internal abstract class VoiceModule {
    @Binds
    abstract fun bindSpeechStreamSource(impl: ZundamonVoiceController): SpeechStreamSource

    @Binds
    abstract fun bindMouthTrackSource(impl: ZundamonVoiceController): MouthTrackSource
}
