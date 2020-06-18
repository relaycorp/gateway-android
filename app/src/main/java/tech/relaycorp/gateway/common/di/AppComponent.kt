package tech.relaycorp.gateway.common.di

import dagger.Component
import tech.relaycorp.gateway.App
import tech.relaycorp.gateway.AppModule
import tech.relaycorp.gateway.data.DataModule
import javax.inject.Singleton

@Singleton
@Component(
    modules = [
        AppModule::class,
        DataModule::class
    ]
)
interface AppComponent {
    fun activityComponent(): ActivityComponent
    fun inject(app: App)
}
