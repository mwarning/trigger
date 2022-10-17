package app.trigger.https

import kotlin.Throws
import java.math.BigInteger
import java.security.*
import java.security.cert.CertificateEncodingException
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import java.util.*
import javax.net.ssl.X509TrustManager

internal class IgnoreExpirationTrustManager(private val innerTrustManager: X509TrustManager) : X509TrustManager {
    @Throws(CertificateException::class)
    override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) {
        innerTrustManager.checkClientTrusted(chain, authType)
    }

    @Throws(CertificateException::class)
    override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) {
        val newChain = arrayOf<X509Certificate>(EternalCertificate(chain[0]))
        /*
        var chain = chain
        chain = Arrays.copyOf(chain, chain.size)
        val newChain = arrayOfNulls<X509Certificate>(chain.size)
        newChain[0] = EternalCertificate(chain[0])
        System.arraycopy(chain, 1, newChain, 1, chain.size - 1)
        chain = newChain
        innerTrustManager.checkServerTrusted(chain, authType)
        */
        innerTrustManager.checkServerTrusted(newChain, authType)
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> {
        return innerTrustManager.acceptedIssuers
    }

    private inner class EternalCertificate(private val originalCertificate: X509Certificate) : X509Certificate() {
        override fun checkValidity() {
            // ignore notBefore/notAfter
        }

        override fun checkValidity(date: Date) {
            // ignore notBefore/notAfter
        }

        override fun getVersion(): Int {
            return originalCertificate.version
        }

        override fun getSerialNumber(): BigInteger {
            return originalCertificate.serialNumber
        }

        override fun getIssuerDN(): Principal {
            return originalCertificate.issuerDN
        }

        override fun getSubjectDN(): Principal {
            return originalCertificate.subjectDN
        }

        override fun getNotBefore(): Date {
            return originalCertificate.notBefore
        }

        override fun getNotAfter(): Date {
            return originalCertificate.notAfter
        }

        @Throws(CertificateEncodingException::class)
        override fun getTBSCertificate(): ByteArray {
            return originalCertificate.tbsCertificate
        }

        override fun getSignature(): ByteArray {
            return originalCertificate.signature
        }

        override fun getSigAlgName(): String {
            return originalCertificate.sigAlgName
        }

        override fun getSigAlgOID(): String {
            return originalCertificate.sigAlgOID
        }

        override fun getSigAlgParams(): ByteArray {
            return originalCertificate.sigAlgParams
        }

        override fun getIssuerUniqueID(): BooleanArray {
            return originalCertificate.issuerUniqueID
        }

        override fun getSubjectUniqueID(): BooleanArray {
            return originalCertificate.subjectUniqueID
        }

        override fun getKeyUsage(): BooleanArray {
            return originalCertificate.keyUsage
        }

        override fun getBasicConstraints(): Int {
            return originalCertificate.basicConstraints
        }

        @Throws(CertificateEncodingException::class)
        override fun getEncoded(): ByteArray {
            return originalCertificate.encoded
        }

        @Throws(CertificateException::class, NoSuchAlgorithmException::class, InvalidKeyException::class, NoSuchProviderException::class, SignatureException::class)
        override fun verify(key: PublicKey) {
            originalCertificate.verify(key)
        }

        @Throws(CertificateException::class, NoSuchAlgorithmException::class, InvalidKeyException::class, NoSuchProviderException::class, SignatureException::class)
        override fun verify(key: PublicKey, sigProvider: String) {
            originalCertificate.verify(key, sigProvider)
        }

        override fun toString(): String {
            return originalCertificate.toString()
        }

        override fun getPublicKey(): PublicKey {
            return originalCertificate.publicKey
        }

        override fun getCriticalExtensionOIDs(): Set<String> {
            return originalCertificate.criticalExtensionOIDs
        }

        override fun getExtensionValue(oid: String): ByteArray {
            return originalCertificate.getExtensionValue(oid)
        }

        override fun getNonCriticalExtensionOIDs(): Set<String> {
            return originalCertificate.nonCriticalExtensionOIDs
        }

        override fun hasUnsupportedCriticalExtension(): Boolean {
            return originalCertificate.hasUnsupportedCriticalExtension()
        }
    }
}