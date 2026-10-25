package dev.koolest

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigInteger
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Signature
import java.security.interfaces.RSAPrivateKey
import java.util.Base64

class SelfAdbTest {
    private val pair = KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.generateKeyPair()
    private val key = pair.private as RSAPrivateKey

    @Test fun signsTheTokenAsASha1Digest() {
        val data = "adb token".toByteArray()
        val sig = SelfAdb.sign(key, MessageDigest.getInstance("SHA-1").digest(data))
        assertTrue(Signature.getInstance("SHA1withRSA").apply { initVerify(pair.public); update(data) }.verify(sig))
    }

    @Test fun encodesThePublicKeyLikeAdbd() {
        val text = String(SelfAdb.publicKey(key))
        assertTrue(text.endsWith(" Koolest TV Launcher\u0000"))
        val b = ByteBuffer.wrap(Base64.getDecoder().decode(text.substringBefore(' '))).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(524, b.capacity())
        assertEquals(64, b.int)
        val n0inv = b.int
        val n = BigInteger(1, ByteArray(256).also { b.get(it) }.reversedArray())
        assertEquals(key.modulus, n)
        assertEquals(-1, n0inv * n.toInt()) // n0inv * n[0] = -1 mod 2^32
        val rr = BigInteger(1, ByteArray(256).also { b.get(it) }.reversedArray())
        assertEquals(BigInteger.ONE.shiftLeft(4096).mod(n), rr)
        assertEquals(65537, b.int)
    }
}
