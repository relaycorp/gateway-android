package tech.relaycorp.gateway.test.factory

import tech.relaycorp.gateway.common.nowInUtc
import tech.relaycorp.relaynet.issueGatewayCertificate
import tech.relaycorp.relaynet.messages.Cargo
import tech.relaycorp.relaynet.wrappers.generateRSAKeyPair

object CargoFactory {

    fun build() = Cargo(
        recipientAddress = "http://example.org",
        senderCertificate = certificate,
        payload = "".toByteArray()
    )

    fun buildSerialized() =
        build().serialize(keyPair.private)

    private val keyPair by lazy {
        generateRSAKeyPair()
    }

    private val certificate by lazy {
        issueGatewayCertificate(
            keyPair.public,
            keyPair.private,
            nowInUtc().plusDays(1)
        )
    }
}
