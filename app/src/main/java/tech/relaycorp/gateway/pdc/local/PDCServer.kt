package tech.relaycorp.gateway.pdc.local

import io.ktor.application.Application
import io.ktor.application.install
import io.ktor.routing.routing
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.websocket.WebSockets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import tech.relaycorp.gateway.pdc.local.routes.EndpointRegistrationRoute
import tech.relaycorp.gateway.pdc.local.routes.PDCServerRoute
import tech.relaycorp.gateway.pdc.local.routes.ParcelCollectionRoute
import tech.relaycorp.gateway.pdc.local.routes.ParcelDeliveryRoute
import javax.inject.Inject
import kotlin.time.seconds

class PDCServer
@Inject constructor(
    private val stateManager: PDCServerStateManager,
    endpointRegistrationRoute: EndpointRegistrationRoute,
    parcelCollectionRoute: ParcelCollectionRoute,
    parcelDeliveryRoute: ParcelDeliveryRoute
) {

    private val server by lazy {
        embeddedServer(Netty, PORT, "127.0.0.1", watchPaths = emptyList()) {
            PDCServerConfiguration.configure(
                this,
                listOf(
                    endpointRegistrationRoute,
                    parcelCollectionRoute,
                    parcelDeliveryRoute
                )
            )
        }
    }

    suspend fun start() {
        withContext(Dispatchers.IO) {
            server.start(false)
        }
        stateManager.set(State.Started)
    }

    suspend fun stop() {
        withContext(Dispatchers.IO) {
            server.stop(0, CALL_DEADLINE.toLongMilliseconds())
        }
        stateManager.set(State.Stopped)
    }

    enum class State {
        Started, Stopped
    }

    companion object {
        const val PORT = 13276
        private val CALL_DEADLINE = 5.seconds
    }
}

object PDCServerConfiguration {
    fun configure(serverApp: Application, routes: List<PDCServerRoute>) {
        with(serverApp) {
            install(WebSockets)

            routing {
                routes.iterator().forEach { it.register(this) }
            }
        }
    }
}
