package tech.relaycorp.gateway.pdc.local.routes

import io.ktor.http.cio.websocket.CloseReason
import io.ktor.http.cio.websocket.Frame
import io.ktor.http.cio.websocket.close
import io.ktor.http.cio.websocket.readText
import io.ktor.request.header
import io.ktor.routing.Routing
import io.ktor.websocket.DefaultWebSocketServerSession
import io.ktor.websocket.webSocket
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.receiveAsFlow
import tech.relaycorp.gateway.data.model.MessageAddress
import tech.relaycorp.gateway.domain.endpoint.CollectParcels
import tech.relaycorp.gateway.pdc.local.utils.ParcelCollectionHandshake
import tech.relaycorp.relaynet.cogrpc.readBytesAndClose
import tech.relaycorp.relaynet.messages.control.ParcelDelivery
import tech.relaycorp.relaynet.wrappers.x509.Certificate
import javax.inject.Inject
import javax.inject.Provider

class ParcelCollectionRoute
@Inject constructor(
    private val parcelCollectionHandshake: ParcelCollectionHandshake,
    private val collectParcelsProvider: Provider<CollectParcels>
) : PDCServerRoute {

    private val asyncJob = SupervisorJob()
    private val asyncScope = CoroutineScope(asyncJob)

    override fun register(routing: Routing) {
        routing.webSocket(URL_PATH) {
            if (call.request.header(HEADER_ORIGIN) != null) {
                // The client is most likely a (malicious) web page
                close(
                    CloseReason(
                        CloseReason.Codes.VIOLATED_POLICY,
                        "Web browser requests are disabled for security reasons"
                    )
                )
                return@webSocket
            }

            val certificates = try {
                parcelCollectionHandshake.handshake(this)
            } catch (e: ParcelCollectionHandshake.HandshakeUnsuccessful) {
                return@webSocket
            }

            // TODO: Call cert.getCertificatePath() and validate certificate chain

            val collectParcels = collectParcelsProvider.get()
            sendParcels(collectParcels, certificates.toAddresses())
            receiveAcks(collectParcels)

            val keepAlive = call.request.header(HEADER_KEEP_ALIVE) == "on"
            if (!keepAlive) {
                collectParcels
                    .noParcelsToDeliverOrAck
                    .onEach { noParcelsToDeliverOrAck ->
                        if (!keepAlive && noParcelsToDeliverOrAck) {
                            close(
                                CloseReason(
                                    CloseReason.Codes.NORMAL,
                                    "All available parcels delivered"
                                )
                            )
                            asyncJob.complete()
                        }
                    }
                    .launchIn(asyncScope)
            }

            asyncJob.join()
        }
    }

    private fun List<Certificate>.toAddresses() =
        map { MessageAddress.of(it.subjectPrivateAddress) }

    private suspend fun DefaultWebSocketServerSession.sendParcels(
        collectParcels: CollectParcels,
        addresses: List<MessageAddress>
    ) {
        collectParcels.getNewParcelsForEndpoints(addresses)
            .onEach { parcels ->
                parcels.forEach { (localId, parcelStream) ->
                    val parcelDelivery = ParcelDelivery(localId, parcelStream.readBytesAndClose())
                    outgoing.send(
                        Frame.Binary(true, parcelDelivery.serialize())
                    )
                }
            }
            .launchIn(asyncScope)
    }

    private suspend fun DefaultWebSocketServerSession.receiveAcks(collectParcels: CollectParcels) {
        incoming.receiveAsFlow()
            .onEach { frame ->
                if (frame !is Frame.Text) {
                    close(CloseReason(CloseReason.Codes.CANNOT_ACCEPT, "Invalid ack"))
                    asyncJob.complete()
                    return@onEach
                }

                val localId = frame.readText()
                collectParcels.processParcelAck(localId)
            }
            .launchIn(asyncScope)
    }

    companion object {
        const val URL_PATH = "/v1/parcel-collection"
        const val HEADER_ORIGIN = "Origin"
        const val HEADER_KEEP_ALIVE = "X-Relaynet-Keep-Alive"
    }
}
