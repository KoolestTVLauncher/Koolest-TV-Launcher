package dev.koolest

import android.content.Context
import java.io.DataInputStream
import java.io.File
import java.io.IOException
import java.math.BigInteger
import java.net.InetSocketAddress
import java.net.Socket
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPrivateKey
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.RSAKeyGenParameterSpec
import java.util.Base64
import javax.crypto.Cipher

/**
 * Runs shell commands through the TV's own adb (network debugging, port 5555), so the README's adb steps work
 * from the remote. Sideload build only: Play doesn't take an app that switches off the system launcher, so the
 * play flavor has a stub instead. The first time, the TV asks to allow debugging for Koolest's key.
 * Only the classic adb protocol: Android 11's pairing-code "Wireless debugging" (TLS) isn't supported.
 */
object SelfAdb {
    const val AVAILABLE = true

    /** Runs [commands] in order off the UI thread; [done] gets their output, or a ConnectException when adb is off. */
    fun run(c: Context, commands: List<String>, done: (Result<String>) -> Unit) {
        val keyFile = File(c.noBackupFilesDir, "adbkey") // never in a backup: it unlocks this TV's adb
        Thread {
            done(runCatching {
                Socket().use { s ->
                    s.connect(InetSocketAddress("127.0.0.1", 5555), 3000)
                    s.soTimeout = 10_000
                    shell(Wire(s), key(keyFile), commands) { s.soTimeout = 60_000 } // time to answer the TV's prompt
                }
            })
        }.start()
    }

    private fun key(f: File): RSAPrivateKey {
        if (!f.exists()) f.writeBytes(KeyPairGenerator.getInstance("RSA").apply { initialize(RSAKeyGenParameterSpec(2048, RSAKeyGenParameterSpec.F4)) }.generateKeyPair().private.encoded)
        return KeyFactory.getInstance("RSA").generatePrivate(PKCS8EncodedKeySpec(f.readBytes())) as RSAPrivateKey
    }

    private fun shell(w: Wire, key: RSAPrivateKey, commands: List<String>, prompting: () -> Unit): String {
        w.send(CNXN, VERSION, MAX_DATA, "host::\u0000".toByteArray())
        var signed = false
        while (true) {
            val m = w.read()
            if (m.cmd == CNXN) break
            if (m.cmd != AUTH || m.arg0 != AUTH_TOKEN) throw IOException("unexpected adb message ${m.cmd}")
            if (!signed) w.send(AUTH, AUTH_SIGNATURE, 0, sign(key, m.data)).also { signed = true }
            else w.send(AUTH, AUTH_PUBLIC_KEY, 0, publicKey(key)).also { prompting() } // key not known yet: the TV asks
        }
        val out = StringBuilder()
        commands.forEachIndexed { i, cmd ->
            val local = i + 1
            w.send(OPEN, local, 0, "shell:$cmd\u0000".toByteArray())
            while (true) {
                val m = w.read()
                if (m.arg1 != local) continue // late messages from the previous command
                if (m.cmd == WRTE) out.append(String(m.data)).also { w.send(OKAY, local, m.arg0) }
                if (m.cmd == CLSE) {
                    w.send(CLSE, local, m.arg0)
                    break
                }
            }
        }
        return out.toString()
    }

    /** adbd checks the token as an already-hashed SHA-1 digest: PKCS#1 v1.5 over DigestInfo + token. */
    internal fun sign(key: RSAPrivateKey, token: ByteArray): ByteArray =
        Cipher.getInstance("RSA/ECB/PKCS1Padding").apply { init(Cipher.ENCRYPT_MODE, key) }.doFinal(SHA1_INFO + token)

    /** The public key in adbd's own format (android_pubkey_encode), base64, then a name for the prompt. */
    internal fun publicKey(key: RSAPrivateKey): ByteArray {
        val n = key.modulus
        val word = BigInteger.ONE.shiftLeft(32)
        fun le(x: BigInteger) = x.toByteArray().reversedArray().copyOf(256) // drops the sign byte, pads short ones
        val b = ByteBuffer.allocate(524).order(ByteOrder.LITTLE_ENDIAN)
            .putInt(64) // modulus length in 32-bit words
            .putInt(n.mod(word).modInverse(word).negate().mod(word).toInt()) // -1 / n[0] mod 2^32
            .put(le(n))
            .put(le(BigInteger.ONE.shiftLeft(4096).mod(n))) // R^2 mod n, R = 2^2048
            .putInt(65537) // F4, fixed when the key is made: Android's key class doesn't expose it
        return (Base64.getEncoder().encodeToString(b.array()) + " Koolest TV Launcher\u0000").toByteArray()
    }

    private class Message(val cmd: Int, val arg0: Int, val arg1: Int, val data: ByteArray)

    private class Wire(s: Socket) {
        private val out = s.getOutputStream()
        private val inp = DataInputStream(s.getInputStream())

        fun send(cmd: Int, arg0: Int, arg1: Int, data: ByteArray = ByteArray(0)) {
            out.write(ByteBuffer.allocate(24 + data.size).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(cmd).putInt(arg0).putInt(arg1).putInt(data.size)
                .putInt(data.sumOf { it.toInt() and 0xff }).putInt(cmd.inv()).put(data).array())
            out.flush()
        }

        fun read(): Message {
            val head = ByteBuffer.wrap(ByteArray(24).also { inp.readFully(it) }).order(ByteOrder.LITTLE_ENDIAN)
            val cmd = head.int
            val arg0 = head.int
            val arg1 = head.int
            return Message(cmd, arg0, arg1, ByteArray(head.int).also { inp.readFully(it) })
        }
    }

    private const val CNXN = 0x4e584e43
    private const val AUTH = 0x48545541
    private const val OPEN = 0x4e45504f
    private const val OKAY = 0x59414b4f
    private const val CLSE = 0x45534c43
    private const val WRTE = 0x45545257
    private const val VERSION = 0x01000001
    private const val MAX_DATA = 256 * 1024
    private const val AUTH_TOKEN = 1
    private const val AUTH_SIGNATURE = 2
    private const val AUTH_PUBLIC_KEY = 3
    private val SHA1_INFO = byteArrayOf(0x30, 0x21, 0x30, 0x09, 0x06, 0x05, 0x2b, 0x0e, 0x03, 0x02, 0x1a, 0x05, 0x00, 0x04, 0x14)
}
