package krio.systemdesign.chatapp.feature.login.impl.di

import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Configuration
import org.koin.core.annotation.Module

// @Configuration: the app's graph picks the module up on its own.
@Module
@Configuration
@ComponentScan("krio.systemdesign.chatapp.feature.login.impl")
class LoginModule
