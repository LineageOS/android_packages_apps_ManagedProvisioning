package com.android.managedprovisioning.common

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent

@Module
@InstallIn(ActivityComponent::class)
interface CommonModule {
    @Binds
    fun bind(impl: DefaultFlags): Flags
}
