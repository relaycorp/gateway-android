package tech.relaycorp.gateway.pdc.local.routes

import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import io.ktor.http.ContentType
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.withCharset
import io.ktor.server.testing.contentType
import io.ktor.server.testing.handleRequest
import io.ktor.server.testing.setBody
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.Test
import tech.relaycorp.gateway.domain.endpoint.EndpointRegistration
import tech.relaycorp.gateway.domain.endpoint.InvalidCRAException
import tech.relaycorp.gateway.pdc.local.ControlMessageContentType
import tech.relaycorp.gateway.test.FullCertPath
import tech.relaycorp.gateway.test.KeyPairSet
import tech.relaycorp.relaynet.messages.control.ClientRegistration
import tech.relaycorp.relaynet.messages.control.ClientRegistrationRequest
import kotlin.test.assertEquals

class EndpointRegistrationTest {
    private val plainTextUTF8ContentType = ContentType.Text.Plain.withCharset(Charsets.UTF_8)

    private val mockEndpointRegistration = mock<EndpointRegistration>()

    @Test
    fun `Invalid request content type should be refused`() {
        testPDCServer(mockEndpointRegistration) {
            val call = handleRequest(HttpMethod.Post, "/v1/clients") {
                addHeader("Content-Type", ContentType.Application.Json.toString())
            }
            with(call) {
                assertEquals(HttpStatusCode.UnsupportedMediaType, response.status())
                assertEquals(plainTextUTF8ContentType, response.contentType())
                assertEquals(
                    "Content type ${ControlMessageContentType.CRR} is required",
                    response.content
                )
            }
        }
    }

    @Test
    fun `Invalid CRR should be refused`() {
        testPDCServer {
            val call = handleRequest(HttpMethod.Post, "/v1/clients") {
                addHeader("Content-Type", ControlMessageContentType.CRR.toString())
                setBody("invalid CRR".toByteArray())
            }
            with(call) {
                assertEquals(HttpStatusCode.BadRequest, response.status())
                assertEquals(plainTextUTF8ContentType, response.contentType())
                assertEquals(
                    "Invalid client registration request",
                    response.content
                )
            }
        }
    }

    @Test
    fun `Valid CRR with invalid CRA encapsulated should be refused`() = runBlockingTest {
        whenever(mockEndpointRegistration.register(any()))
            .thenThrow(InvalidCRAException("Invalid CRA", null))

        testPDCServer(mockEndpointRegistration) {
            val crr = ClientRegistrationRequest(
                KeyPairSet.PRIVATE_ENDPOINT.public,
                "invalid CRA".toByteArray()
            )
            val call = handleRequest(HttpMethod.Post, "/v1/clients") {
                addHeader("Content-Type", ControlMessageContentType.CRR.toString())
                setBody(crr.serialize(KeyPairSet.PRIVATE_ENDPOINT.private))
            }
            with(call) {
                assertEquals(HttpStatusCode.BadRequest, response.status())
                assertEquals(plainTextUTF8ContentType, response.contentType())
                assertEquals(
                    "Invalid client registration authorization encapsulated in CRR",
                    response.content
                )
            }
        }
    }

    @Test
    fun `Valid CRR should complete the registration`() = runBlockingTest {
        val clientRegistration =
            ClientRegistration(FullCertPath.PRIVATE_ENDPOINT, FullCertPath.PRIVATE_GW)
        val clientRegistrationSerialized = clientRegistration.serialize()
        whenever(mockEndpointRegistration.register(any())).thenReturn(clientRegistrationSerialized)

        testPDCServer(mockEndpointRegistration) {
            val crr = ClientRegistrationRequest(
                KeyPairSet.PRIVATE_ENDPOINT.public,
                "invalid CRA".toByteArray()
            )
            val call = handleRequest(HttpMethod.Post, "/v1/clients") {
                addHeader("Content-Type", ControlMessageContentType.CRR.toString())
                setBody(crr.serialize(KeyPairSet.PRIVATE_ENDPOINT.private))
            }
            with(call) {
                assertEquals(HttpStatusCode.OK, response.status())
                assertEquals(ControlMessageContentType.CLIENT_REGISTRATION, response.contentType())
                assertEquals(
                    clientRegistrationSerialized.asList(),
                    response.byteContent!!.asList()
                )
            }
        }
    }
}
