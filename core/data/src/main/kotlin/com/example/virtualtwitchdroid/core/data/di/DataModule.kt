package com.example.virtualtwitchdroid.core.data.di

import com.example.virtualtwitchdroid.core.data.repository.ChatRepository
import com.example.virtualtwitchdroid.core.data.repository.DefaultChatRepository
import com.example.virtualtwitchdroid.core.data.repository.DefaultSearchRepository
import com.example.virtualtwitchdroid.core.data.repository.DefaultStreamRepository
import com.example.virtualtwitchdroid.core.data.repository.DefaultStreamsRepository
import com.example.virtualtwitchdroid.core.data.repository.SearchRepository
import com.example.virtualtwitchdroid.core.data.repository.StreamRepository
import com.example.virtualtwitchdroid.core.data.repository.StreamsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
internal abstract class DataModule {

    @Binds
    abstract fun bindsStreamRepository(impl: DefaultStreamRepository): StreamRepository

    @Binds
    abstract fun bindsChatRepository(impl: DefaultChatRepository): ChatRepository

    @Binds
    abstract fun bindsStreamsRepository(impl: DefaultStreamsRepository): StreamsRepository

    @Binds
    abstract fun bindsSearchRepository(impl: DefaultSearchRepository): SearchRepository
}
