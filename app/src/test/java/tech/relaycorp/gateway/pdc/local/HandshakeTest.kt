package tech.relaycorp.gateway.pdc.local

import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.cms.ContentInfo
import org.bouncycastle.cms.CMSException
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedData
import org.bouncycastle.cms.CMSSignedDataGenerator
import org.bouncycastle.cms.CMSTypedData
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder
import org.bouncycastle.operator.ContentSigner
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import tech.relaycorp.relaynet.issueEndpointCertificate
import tech.relaycorp.relaynet.wrappers.generateRSAKeyPair
import tech.relaycorp.relaynet.wrappers.x509.Certificate
import java.io.IOException
import java.security.PrivateKey
import java.time.ZonedDateTime

val NONCE = "The nonce".toByteArray()
val ENDPOINT_KEY_PAIR = generateRSAKeyPair()
val ENDPOINT_CERT = issueEndpointCertificate(
    ENDPOINT_KEY_PAIR.public,
    ENDPOINT_KEY_PAIR.private,
    ZonedDateTime.now().plusDays(1)
)

class VerifySignatureTest {
    @Test
    fun `Invalid DER values should be refused`() {
        val invalidCMSSignedData = "Not really DER-encoded".toByteArray()

        val exception = assertThrows<InvalidHandshakeSignatureException> {
            Handshake.verifySignature(invalidCMSSignedData, NONCE)
        }

        assertEquals("Value is not DER-encoded", exception.message)
    }

    @Test
    fun `ContentInfo wrapper should be required`() {
        val invalidCMSSignedData = ASN1Integer(10).encoded

        val exception = assertThrows<InvalidHandshakeSignatureException> {
            Handshake.verifySignature(invalidCMSSignedData, NONCE)
        }

        assertEquals(
            "SignedData value is not wrapped in ContentInfo",
            exception.message
        )
    }

    @Test
    fun `ContentInfo wrapper should contain a valid SignedData value`() {
        val signedDataOid = ASN1ObjectIdentifier("1.2.840.113549.1.7.2")
        val invalidCMSSignedData = ContentInfo(signedDataOid, ASN1Integer(10))

        val exception = assertThrows<InvalidHandshakeSignatureException> {
            Handshake.verifySignature(invalidCMSSignedData.encoded, NONCE)
        }

        assertEquals(
            "ContentInfo wraps invalid SignedData value",
            exception.message
        )
    }

    @Test
    fun `Well formed but invalid signatures should be rejected`() {
        // Swap the SignerInfo collection from two different CMS SignedData values

        val cmsSignedDataSerialized1 = sign(NONCE, ENDPOINT_KEY_PAIR.private, ENDPOINT_CERT)
        val cmsSignedData1 = parseCmsSignedData(cmsSignedDataSerialized1)

        val cmsSignedDataSerialized2 = sign(
            byteArrayOf(0xde.toByte(), *NONCE),
            ENDPOINT_KEY_PAIR.private,
            ENDPOINT_CERT
        )
        val cmsSignedData2 = parseCmsSignedData(cmsSignedDataSerialized2)

        val invalidCmsSignedData = CMSSignedData.replaceSigners(
            cmsSignedData1,
            cmsSignedData2.signerInfos
        )
        val invalidCmsSignedDataSerialized = invalidCmsSignedData.toASN1Structure().encoded

        val exception = assertThrows<InvalidHandshakeSignatureException> {
            Handshake.verifySignature(invalidCmsSignedDataSerialized, NONCE)
        }

        assertEquals("Invalid signature", exception.message)
    }

    @Test
    fun `An empty SignerInfo collection should be refused`() {
        val signedDataGenerator = CMSSignedDataGenerator()
        val plaintextCms: CMSTypedData = CMSProcessableByteArray(NONCE)
        val cmsSignedData = signedDataGenerator.generate(plaintextCms, true)

        val exception = assertThrows<InvalidHandshakeSignatureException> {
            Handshake.verifySignature(cmsSignedData.encoded, NONCE)
        }

        assertEquals("SignedData should contain exactly one SignerInfo (got 0)", exception.message)
    }

    @Test
    fun `A SignerInfo collection with more than one item should be refused`() {
        val signedDataGenerator = CMSSignedDataGenerator()

        val signerBuilder = JcaContentSignerBuilder("SHA256withRSA")
        val contentSigner: ContentSigner = signerBuilder.build(ENDPOINT_KEY_PAIR.private)
        val signerInfoGenerator = JcaSignerInfoGeneratorBuilder(
            JcaDigestCalculatorProviderBuilder()
                .build()
        ).build(contentSigner, ENDPOINT_CERT.certificateHolder)
        // Add the same SignerInfo twice
        signedDataGenerator.addSignerInfoGenerator(
            signerInfoGenerator
        )
        signedDataGenerator.addSignerInfoGenerator(
            signerInfoGenerator
        )

        val cmsSignedData = signedDataGenerator.generate(
            CMSProcessableByteArray(NONCE),
            true
        )

        val exception = assertThrows<InvalidHandshakeSignatureException> {
            Handshake.verifySignature(cmsSignedData.encoded, NONCE)
        }

        assertEquals("SignedData should contain exactly one SignerInfo (got 2)", exception.message)
    }

    @Test
    fun `Certificate of signer should be required`() {
        val signedDataGenerator = CMSSignedDataGenerator()

        val signerBuilder = JcaContentSignerBuilder("SHA256withRSA")
        val contentSigner: ContentSigner = signerBuilder.build(ENDPOINT_KEY_PAIR.private)
        val signerInfoGenerator = JcaSignerInfoGeneratorBuilder(
            JcaDigestCalculatorProviderBuilder()
                .build()
        ).build(contentSigner, ENDPOINT_CERT.certificateHolder)
        signedDataGenerator.addSignerInfoGenerator(
            signerInfoGenerator
        )

        val cmsSignedData = signedDataGenerator.generate(
            CMSProcessableByteArray(NONCE),
            true
        )

        val exception = assertThrows<InvalidHandshakeSignatureException> {
            Handshake.verifySignature(cmsSignedData.encoded, NONCE)
        }

        assertEquals("Certificate of signer should be attached", exception.message)
    }

    @Test
    fun `Verification should fail if signed nonce does not match expected plaintext`() {
        val cmsSignedDataSerialized = sign(NONCE, ENDPOINT_KEY_PAIR.private, ENDPOINT_CERT)

        val exception = assertThrows<InvalidHandshakeSignatureException> {
            Handshake.verifySignature(
                cmsSignedDataSerialized,
                byteArrayOf(0xde.toByte(), *NONCE)
            )
        }

        assertEquals("Invalid signature", exception.message)
    }

    @Test
    fun `Verification should succeed if signed nonce matches expected plaintext`() {
        val cmsSignedDataSerialized = sign(NONCE, ENDPOINT_KEY_PAIR.private, ENDPOINT_CERT)

        // No exceptions thrown
        Handshake.verifySignature(cmsSignedDataSerialized, NONCE)
    }

    @Test
    fun `Signer certificate should be output when verification passes`() {
        val cmsSignedDataSerialized = sign(
            NONCE,
            ENDPOINT_KEY_PAIR.private,
            ENDPOINT_CERT
        )

        val verificationResult = Handshake.verifySignature(cmsSignedDataSerialized, NONCE)

        assertEquals(
            ENDPOINT_CERT.certificateHolder,
            verificationResult.signerCertificate.certificateHolder
        )
    }
}

private fun sign(
    plaintext: ByteArray,
    signerPrivateKey: PrivateKey,
    signerCertificate: Certificate,
    encapsulatePlaintext: Boolean = false
): ByteArray {
    val signedDataGenerator = CMSSignedDataGenerator()

    val signerBuilder = JcaContentSignerBuilder("SHA256withRSA")
    val contentSigner: ContentSigner = signerBuilder.build(signerPrivateKey)
    val signerInfoGenerator = JcaSignerInfoGeneratorBuilder(
        JcaDigestCalculatorProviderBuilder()
            .build()
    ).build(contentSigner, signerCertificate.certificateHolder)
    signedDataGenerator.addSignerInfoGenerator(
        signerInfoGenerator
    )

    signedDataGenerator.addCertificate(signerCertificate.certificateHolder)

    val plaintextCms: CMSTypedData = CMSProcessableByteArray(plaintext)
    val cmsSignedData = signedDataGenerator.generate(plaintextCms, encapsulatePlaintext)
    return cmsSignedData.encoded
}

@Throws(IOException::class, IllegalArgumentException::class, CMSException::class)
private fun parseCmsSignedData(cmsSignedDataSerialized: ByteArray): CMSSignedData {
    val asn1Stream = ASN1InputStream(cmsSignedDataSerialized)
    val asn1Sequence = asn1Stream.readObject()
    val contentInfo = ContentInfo.getInstance(asn1Sequence)
    return CMSSignedData(contentInfo)
}
