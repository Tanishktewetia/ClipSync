package com.clipsync.android.security

import java.io.EOFException
import java.io.InputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.security.MessageDigest
import java.util.Locale

/** Phase 4's bootstrap protocol, before the CSP1 stream begins. */
object PairingProtocol {
    fun fingerprint(value: String): String {
        require(value.matches(Regex("[0-9a-fA-F]{64}"))) { "Invalid fingerprint" }
        return value.uppercase(Locale.ROOT)
    }
    fun code(first: String, second: String): String {
        val a = fingerprint(first); val b = fingerprint(second)
        val joined = if (a < b) a + b else b + a
        val digest = MessageDigest.getInstance("SHA-256").digest(joined.toByteArray(Charsets.UTF_8))
        val number = ByteBuffer.wrap(digest).order(ByteOrder.LITTLE_ENDIAN).int.toLong() and 0xffffffffL
        return (number % 1000000).toString().padStart(6, '0')
    }
    fun validateOffer(line: String, local: String, remote: String): String {
        val fields = line.split('|')
        require(fields.size == 3 && fields[0] == "PAIR") { "Invalid pairing response" }
        require(fingerprint(fields[1]) == fingerprint(remote)) { "Pairing identity mismatch" }
        val expected = code(local, remote)
        require(fields[2] == expected) { "Pairing code mismatch" }
        return expected
    }
    fun readLine(input: InputStream): String {
        val bytes = ArrayList<Byte>()
        while (true) {
            val next = input.read()
            if (next == -1) throw EOFException("PC closed the connection")
            if (next == 10) return bytes.toByteArray().toString(Charsets.US_ASCII).trimEnd('\r')
            require(next in 32..126 || next == 13) { "Invalid bootstrap response" }
            require(bytes.size < 256) { "Bootstrap response too long" }
            bytes.add(next.toByte())
        }
    }
}

/** Numeric IPv4 only: no DNS, scans, discovery, URL parsing, or mobile-data route. */
object ManualAddress {
    fun parse(value: String): String {
        val pieces = value.trim().split('.')
        require(pieces.size == 4) { "Enter the PC's IPv4 address" }
        val bytes = pieces.map {
            require(it.matches(Regex("[0-9]{1,3}"))) { "Enter a valid IPv4 address" }
            require(it.length == 1 || !it.startsWith('0')) { "Remove leading zeroes" }
            it.toInt().also { n -> require(n in 0..255) { "Enter a valid IPv4 address" } }
        }
        require(bytes[0] in 1..223 && bytes[0] != 127 && bytes.last() !in listOf(0, 255)) {
            "Enter the PC's Wi-Fi address, not a loopback or broadcast address"
        }
        return bytes.joinToString(".")
    }
}
