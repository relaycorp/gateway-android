package tech.relaycorp.gateway.domain.endpoint

import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.verify
import com.nhaarman.mockitokotlin2.whenever
import kotlinx.coroutines.test.runBlockingTest
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tech.relaycorp.gateway.data.database.LocalEndpointDao
import tech.relaycorp.gateway.data.model.LocalEndpoint
import tech.relaycorp.gateway.data.model.PrivateMessageAddress
import tech.relaycorp.gateway.domain.LocalConfig
import tech.relaycorp.relaynet.messages.InvalidMessageException
import tech.relaycorp.relaynet.messages.control.PrivateNodeRegistration
import tech.relaycorp.relaynet.messages.control.PrivateNodeRegistrationAuthorization
import tech.relaycorp.relaynet.messages.control.PrivateNodeRegistrationRequest
import tech.relaycorp.relaynet.testing.pki.KeyPairSet
import tech.relaycorp.relaynet.testing.pki.PDACertPath
import tech.relaycorp.relaynet.wrappers.privateAddress
import java.nio.charset.Charset
import java.time.ZonedDateTime
import java.time.temporal.ChronoUnit
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class EndpointRegistrationTest {
    private val mockLocalEndpointDao = mock<LocalEndpointDao>()
    private val mockLocalConfig = mock<LocalConfig>()
    private val endpointRegistration = EndpointRegistration(mockLocalEndpointDao, mockLocalConfig)

    private val dummyApplicationId = "tech.relaycorp.foo"

    @BeforeEach
    internal fun setUp() = runBlockingTest {
        whenever(mockLocalConfig.getKeyPair()).thenReturn(KeyPairSet.PRIVATE_GW)
        whenever(mockLocalConfig.getCertificate()).thenReturn(PDACertPath.PRIVATE_GW)
    }

    @Nested
    inner class Authorize {
        @Test
        fun `Application Id for endpoint should be stored in server data`() = runBlockingTest {
            val authorizationSerialized = endpointRegistration.authorize(dummyApplicationId)

            val authorization = PrivateNodeRegistrationAuthorization.deserialize(
                authorizationSerialized,
                KeyPairSet.PRIVATE_GW.public
            )

            assertEquals(
                dummyApplicationId,
                authorization.gatewayData.toString(Charset.defaultCharset())
            )
        }

        @Test
        fun `Authorization should be valid for 15 seconds`() = runBlockingTest {
            val authorizationSerialized = endpointRegistration.authorize(dummyApplicationId)

            val authorization = PrivateNodeRegistrationAuthorization.deserialize(
                authorizationSerialized,
                KeyPairSet.PRIVATE_GW.public
            )

            val ttl = ChronoUnit.SECONDS.between(ZonedDateTime.now(), authorization.expiryDate)
            assertTrue(ttl in 10..16) // Give some wiggle room
        }
    }

    @Nested
    inner class Register {
        private val authorization = PrivateNodeRegistrationAuthorization(
            ZonedDateTime.now().plusSeconds(3),
            dummyApplicationId.toByteArray()
        )
        private val crr = PrivateNodeRegistrationRequest(
            KeyPairSet.PRIVATE_ENDPOINT.public,
            authorization.serialize(KeyPairSet.PRIVATE_GW.private)
        )

        @Test
        fun `CRR should be refused if its encapsulated authorization is invalid`() {
            val invalidCRR = PrivateNodeRegistrationRequest(
                KeyPairSet.PRIVATE_ENDPOINT.public,
                "invalid authorization".toByteArray()
            )

            val exception = assertThrows<InvalidPNRAException> {
                runBlockingTest { endpointRegistration.register(invalidCRR) }
            }

            assertEquals("Registration request contains invalid authorization", exception.message)
            assertTrue(exception.cause is InvalidMessageException)
        }

        @Test
        fun `Endpoint should be registered if CRR is valid`() = runBlockingTest {
            endpointRegistration.register(crr)

            verify(mockLocalEndpointDao).insert(
                LocalEndpoint(
                    PrivateMessageAddress(KeyPairSet.PRIVATE_ENDPOINT.public.privateAddress),
                    dummyApplicationId
                )
            )
        }

        @Test
        fun `Registration should encapsulate gateway certificate`() = runBlockingTest {
            val registrationSerialized = endpointRegistration.register(crr)

            val registration = PrivateNodeRegistration.deserialize(registrationSerialized)
            assertEquals(PDACertPath.PRIVATE_GW, registration.gatewayCertificate)
        }

        @Nested
        inner class EndpointCertificate {
            @Test
            fun `Issuer should be the gateway`() = runBlockingTest {
                val registrationSerialized = endpointRegistration.register(crr)

                val registration = PrivateNodeRegistration.deserialize(registrationSerialized)
                assertEquals(
                    listOf(registration.privateNodeCertificate, PDACertPath.PRIVATE_GW),
                    registration.privateNodeCertificate.getCertificationPath(
                        emptyList(),
                        setOf(PDACertPath.PRIVATE_GW)
                    ).asList()
                )
            }

            @Test
            fun `Subject should be the endpoint`() = runBlockingTest {
                val registrationSerialized = endpointRegistration.register(crr)

                val registration = PrivateNodeRegistration.deserialize(registrationSerialized)
                assertEquals(
                    registration.privateNodeCertificate.subjectPrivateAddress,
                    crr.privateNodePublicKey.privateAddress
                )
            }

            @Test
            fun `Expiry date should be in three years`() = runBlockingTest {
                val registrationSerialized = endpointRegistration.register(crr)

                val registration = PrivateNodeRegistration.deserialize(registrationSerialized)
                val threeYearsFromNow = ZonedDateTime.now().plusYears(3)
                assertEquals(
                    0,
                    ChronoUnit.MINUTES.between(
                        registration.privateNodeCertificate.expiryDate,
                        threeYearsFromNow
                    )
                )
            }
        }
    }
}
