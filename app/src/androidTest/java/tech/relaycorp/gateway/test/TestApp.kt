package tech.relaycorp.gateway.test

import tech.relaycorp.gateway.App
import tech.relaycorp.gateway.AppModule

open class TestApp : App() {
    override val component: AppTestComponent = DaggerAppTestComponent.builder()
        .appModule(AppModule(this))
        .build() as AppTestComponent

    override fun onCreate() {
        super.onCreate()
        component.inject(this)
    }

    override fun startPublicSyncWhenPossible() {
        // Disable automatic public sync start
    }

    override fun enqueuePublicSyncWorker() {
        // Disable public sync worker
    }
}
