/*
 * ConnectBot: simple, powerful, open-source SSH client for Android
 * Copyright 2007 Kenny Root, Jeffrey Sharkey
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *	 http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package app.trigger.ssh

import app.trigger.Log
import kotlin.Throws
import com.trilead.ssh2.crypto.PEMDecoder
import com.trilead.ssh2.crypto.SimpleDERReader
import com.trilead.ssh2.crypto.keys.Ed25519PublicKey
import com.trilead.ssh2.crypto.keys.Ed25519Provider
import com.trilead.ssh2.crypto.Base64
import com.trilead.ssh2.signature.*
import java.io.IOException
import java.lang.Exception
import java.lang.StringBuilder
import java.math.BigInteger
import java.security.*
import java.security.interfaces.*
import java.security.spec.*
import java.util.*
import javax.crypto.*
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.PBEParameterSpec

object PubkeyUtils {
    // from PubkeyDatabase
    const val KEY_TYPE_RSA = "RSA"
    const val KEY_TYPE_DSA = "DSA"
    const val KEY_TYPE_IMPORTED = "IMPORTED"
    const val KEY_TYPE_EC = "EC"
    const val KEY_TYPE_ED25519 = "ED25519"
    private const val TAG = "CB.PubkeyUtils"
    const val PKCS8_START = "-----BEGIN PRIVATE KEY-----"
    const val PKCS8_END = "-----END PRIVATE KEY-----"

    // Size in bytes of salt to use.
    private const val SALT_SIZE = 8

    // Number of iterations for password hashing. PKCS#5 recommends 1000
    private const val ITERATIONS = 1000
    fun formatKey(key: Key): String {
        val algo = key.algorithm
        val fmt = key.format
        val encoded = key.encoded
        return "Key[algorithm=" + algo + ", format=" + fmt +
                ", bytes=" + encoded.size + "]"
    }

    @Throws(Exception::class)
    private fun encrypt(cleartext: ByteArray, secret: String): ByteArray {
        val salt = ByteArray(SALT_SIZE)
        val ciphertext = Encryptor.encrypt(salt, ITERATIONS, secret, cleartext)
        val complete = ByteArray(salt.size + ciphertext!!.size)
        System.arraycopy(salt, 0, complete, 0, salt.size)
        System.arraycopy(ciphertext, 0, complete, salt.size, ciphertext.size)
        Arrays.fill(salt, 0x00.toByte())
        Arrays.fill(ciphertext, 0x00.toByte())
        return complete
    }

    @Throws(Exception::class)
    private fun decrypt(saltAndCiphertext: ByteArray?, secret: String): ByteArray? {
        val salt = ByteArray(SALT_SIZE)
        val ciphertext = ByteArray(saltAndCiphertext!!.size - salt.size)
        System.arraycopy(saltAndCiphertext, 0, salt, 0, salt.size)
        System.arraycopy(saltAndCiphertext, salt.size, ciphertext, 0, ciphertext.size)
        return Encryptor.decrypt(salt, ITERATIONS, secret, ciphertext)
    }

    @Throws(Exception::class)
    fun getEncodedPrivate(pk: PrivateKey, secret: String?): ByteArray {
        val encoded = pk.encoded
        return if (secret == null || secret.length == 0) {
            encoded
        } else encrypt(pk.encoded, secret)
    }

    @Throws(NoSuchAlgorithmException::class, InvalidKeySpecException::class)
    fun decodePrivate(encoded: ByteArray?, keyType: String): PrivateKey {
        val privKeySpec = PKCS8EncodedKeySpec(encoded)
        val kf = KeyFactory.getInstance(keyType)
        return kf.generatePrivate(privKeySpec)
    }

    @Throws(Exception::class)
    fun decodePrivate(encoded: ByteArray?, keyType: String, secret: String?): PrivateKey {
        if (secret != null && secret.isNotEmpty()) {
            return decodePrivate(decrypt(encoded, secret), keyType)
        } else {
            return decodePrivate(encoded, keyType)
        }
    }

    @Throws(InvalidKeySpecException::class, NoSuchAlgorithmException::class)
    fun getBitStrength(encoded: ByteArray?, keyType: String?): Int {
        val pubKey = decodePublic(encoded, keyType)
        return if (KEY_TYPE_RSA == keyType) {
            (pubKey as RSAPublicKey).modulus.bitLength()
        } else if (KEY_TYPE_DSA == keyType) {
            1024
        } else if (KEY_TYPE_EC == keyType) {
            (pubKey as ECPublicKey).params.curve.field
                    .fieldSize
        } else if (KEY_TYPE_ED25519 == keyType) {
            256
        } else {
            0
        }
    }

    @Throws(NoSuchAlgorithmException::class, InvalidKeySpecException::class)
    fun decodePublic(encoded: ByteArray?, keyType: String?): PublicKey {
        val pubKeySpec = X509EncodedKeySpec(encoded)
        val kf = KeyFactory.getInstance(keyType)
        return kf.generatePublic(pubKeySpec)
    }

    @Throws(NoSuchAlgorithmException::class)
    fun getAlgorithmForOid(oid: String): String {
        return if ("1.2.840.10045.2.1" == oid) {
            "EC"
        } else if ("1.2.840.113549.1.1.1" == oid) {
            "RSA"
        } else if ("1.2.840.10040.4.1" == oid) {
            "DSA"
        } else {
            throw NoSuchAlgorithmException("Unknown algorithm OID $oid")
        }
    }

    @Throws(NoSuchAlgorithmException::class)
    fun getOidFromPkcs8Encoded(encoded: ByteArray?): String {
        if (encoded == null) {
            throw NoSuchAlgorithmException("encoding is null")
        }
        return try {
            val reader = SimpleDERReader(encoded)
            reader.resetInput(reader.readSequenceAsByteArray())
            reader.readInt()
            reader.resetInput(reader.readSequenceAsByteArray())
            reader.readOid()
        } catch (e: IOException) {
            Log.w(TAG, "Could not read OID $e")
            throw NoSuchAlgorithmException("Could not read key $e")
        }
    }

    @Throws(InvalidKeySpecException::class)
    fun getRSAPublicExponentFromPkcs8Encoded(encoded: ByteArray?): BigInteger {
        if (encoded == null) {
            throw InvalidKeySpecException("encoded key is null")
        }
        return try {
            val reader = SimpleDERReader(encoded)
            reader.resetInput(reader.readSequenceAsByteArray())
            if (reader.readInt() != BigInteger.ZERO) {
                throw InvalidKeySpecException("PKCS#8 is not version 0")
            }
            reader.readSequenceAsByteArray() // OID sequence
            reader.resetInput(reader.readOctetString()) // RSA key bytes
            reader.resetInput(reader.readSequenceAsByteArray()) // RSA key sequence
            if (reader.readInt() != BigInteger.ZERO) {
                throw InvalidKeySpecException("RSA key is not version 0")
            }
            reader.readInt() // modulus
            reader.readInt() // public exponent
        } catch (e: IOException) {
            Log.w(TAG, "Could not read public exponent $e")
            throw InvalidKeySpecException("Could not read key $e")
        }
    }

    @Throws(BadPasswordException::class)
    fun convertToKeyPair(keybean: KeyPairBean, password: String?): KeyPair {
        return if (KEY_TYPE_IMPORTED == keybean.type) {
            // load specific key using pem format
            try {
                PEMDecoder.decode(String(keybean.privateKey, Charsets.UTF_8).toCharArray(), password)
            } catch (e: Exception) {
                Log.e(TAG, "Cannot decode imported key $e")
                throw BadPasswordException()
            }
        } else {
            // load using internal generated format
            try {
                val privKey = decodePrivate(keybean.privateKey, keybean.type, password)
                val pubKey = decodePublic(keybean.publicKey, keybean.type)
                Log.d(TAG, "Unlocked key " + formatKey(pubKey))
                KeyPair(pubKey, privKey)
            } catch (e: Exception) {
                Log.e(TAG, "Cannot decode pubkey from database $e")
                throw BadPasswordException()
            }
        }
    }

    @Throws(NoSuchAlgorithmException::class, InvalidKeySpecException::class)
    fun recoverKeyPair(encoded: ByteArray?): KeyPair {
        val algo = getAlgorithmForOid(getOidFromPkcs8Encoded(encoded))
        val privKeySpec = PKCS8EncodedKeySpec(encoded)
        val kf = KeyFactory.getInstance(algo)
        val priv = kf.generatePrivate(privKeySpec)
        return KeyPair(recoverPublicKey(kf, priv), priv)
    }

    @Throws(NoSuchAlgorithmException::class, InvalidKeySpecException::class)
    fun recoverPublicKey(kf: KeyFactory, priv: PrivateKey): PublicKey {
        return if (priv is RSAPrivateCrtKey) {
            val rsaPriv = priv
            kf.generatePublic(RSAPublicKeySpec(rsaPriv.modulus, rsaPriv
                    .publicExponent))
        } else if (priv is RSAPrivateKey) {
            val publicExponent = getRSAPublicExponentFromPkcs8Encoded(priv.getEncoded())
            kf.generatePublic(RSAPublicKeySpec(priv.modulus, publicExponent))
        } else if (priv is DSAPrivateKey) {
            val dsaPriv = priv
            val params = dsaPriv.params

            // Calculate public key Y
            val y = params.g.modPow(dsaPriv.x, params.p)
            kf.generatePublic(DSAPublicKeySpec(y, params.p, params.q, params.g))
        } else if (priv is ECPrivateKey) {
            val ecPriv = priv
            val params = ecPriv.params

            // Calculate public key Y
            val generator = params.generator
            val wCoords = EcCore.multiplyPointA(arrayOf(generator.affineX,
                    generator.affineY), ecPriv.s, params)
            val w = ECPoint(wCoords!![0], wCoords[1])
            kf.generatePublic(ECPublicKeySpec(w, params))
        } else {
            throw NoSuchAlgorithmException("Key type must be RSA, DSA, or EC")
        }
    }

    /*
	 * OpenSSH compatibility methods
	 */
    @Throws(IOException::class, InvalidKeyException::class)
    fun convertToOpenSSHFormat(pk: PublicKey?, origNickname: String?): String {
        var nickname = origNickname
        if (nickname == null) nickname = "connectbot@android"
        if (pk is RSAPublicKey) {
            var data = "ssh-rsa "
            data += String(Base64.encode(RSASHA1Verify.get().encodePublicKey(pk as RSAPublicKey?)))
            return "$data $nickname"
        } else if (pk is DSAPublicKey) {
            var data = "ssh-dss "
            data += String(Base64.encode(DSASHA1Verify.get().encodePublicKey(pk as DSAPublicKey?)))
            return "$data $nickname"
        } else if (pk is ECPublicKey) {
            val ecPub = pk
            val keyType = ECDSASHA2Verify.getSshKeyType(ecPub)
            val verifier: SSHSignature = ECDSASHA2Verify.getVerifierForKey(ecPub)
            val keyData = String(Base64.encode(verifier.encodePublicKey(ecPub)))
            return "$keyType $keyData $nickname"
        } else if (pk is Ed25519PublicKey) {
            return Ed25519Verify.ED25519_ID + " " + String(Base64.encode(Ed25519Verify.get().encodePublicKey(pk))) +
                    " " + nickname
        }
        throw InvalidKeyException("Unknown key type")
    }
    /*
	 * OpenSSH compatibility methods
	 */
    /**
     * @param pair KeyPair to convert to an OpenSSH public key
     * @return OpenSSH-encoded pubkey
     */
    fun extractOpenSSHPublic(pair: KeyPair): ByteArray? {
        return try {
            val pubKey = pair.public
            if (pubKey is RSAPublicKey) {
                RSASHA1Verify.get().encodePublicKey(pubKey)
            } else if (pubKey is DSAPublicKey) {
                DSASHA1Verify.get().encodePublicKey(pubKey)
            } else if (pubKey is ECPublicKey) {
                ECDSASHA2Verify.getVerifierForKey(pubKey).encodePublicKey(pubKey)
            } else if (pubKey is Ed25519PublicKey) {
                Ed25519Verify.get().encodePublicKey(pubKey)
            } else {
                null
            }
        } catch (e: IOException) {
            null
        }
    }

    @Throws(NoSuchAlgorithmException::class, InvalidParameterSpecException::class, NoSuchPaddingException::class, InvalidKeyException::class, InvalidAlgorithmParameterException::class, InvalidKeySpecException::class, IllegalBlockSizeException::class, IOException::class)
    fun exportPEM(key: PrivateKey?, secret: String?): String {
        val sb = StringBuilder()
        var data = key!!.encoded
        sb.append(PKCS8_START)
        sb.append('\n')
        if (secret != null) {
            val salt = ByteArray(8)
            val random = SecureRandom()
            random.nextBytes(salt)
            val defParams = PBEParameterSpec(salt, 1)
            val params = AlgorithmParameters.getInstance(key.algorithm)
            params.init(defParams)
            val pbeSpec = PBEKeySpec(secret.toCharArray())
            val keyFact = SecretKeyFactory.getInstance(key.algorithm)
            val cipher = Cipher.getInstance(key.algorithm)
            cipher.init(Cipher.WRAP_MODE, keyFact.generateSecret(pbeSpec), params)
            val wrappedKey = cipher.wrap(key)
            val pinfo = EncryptedPrivateKeyInfo(params, wrappedKey)
            data = pinfo.encoded
            sb.append("Proc-Type: 4,ENCRYPTED\n")
            sb.append("DEK-Info: DES-EDE3-CBC,")
            sb.append(encodeHex(salt))
            sb.append("\n\n")
        }
        var i = sb.length
        sb.append(Base64.encode(data))
        i += 63
        while (i < sb.length) {
            sb.insert(i, "\n")
            i += 64
        }
        sb.append('\n')
        sb.append(PKCS8_END)
        sb.append('\n')
        return sb.toString()
    }

    private val HEX_DIGITS = charArrayOf('0', '1', '2', '3', '4', '5', '6',
            '7', '8', '9', 'a', 'b', 'c', 'd', 'e', 'f')

    fun encodeHex(bytes: ByteArray): String {
        val hex = CharArray(bytes.size * 2)
        var i = 0
        for (b in bytes) {
            hex[i++] = HEX_DIGITS[b as Int shr 4 and 0x0f]
            hex[i++] = HEX_DIGITS[b as Int and 0x0f]
        }
        return String(hex)
    }

    class BadPasswordException : Exception()

    init {
        Log.d("PubkeyUtils", "Ed25519Provider.insertIfNeeded")
        Ed25519Provider.insertIfNeeded()
    }
}