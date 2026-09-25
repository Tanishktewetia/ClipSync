package com.clipsync.android.ui

import java.net.InetSocketAddress
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSocket
import javax.net.ssl.X509TrustManager
import java.security.SecureRandom
import java.security.cert.X509Certificate

object ConnectionTestClient {
    // TODO(phase5): pin cert instead of accepting the Phase 1 self-signed certificate.
    fun send(host: String, port: Int, message: String): String {
        val trustAll = object : X509TrustManager {
            override fun getAcceptedIssuers() = arrayOf<X509Certificate>()
            override fun checkClientTrusted(chain: Array<X509Certificate>, authType: String) = Unit
            override fun checkServerTrusted(chain: Array<X509Certificate>, authType: String) = Unit
        }
        val context = SSLContext.getInstance("TLS").apply {
            init(null, arrayOf(trustAll), SecureRandom())
        }
        val socket = context.socketFactory.createSocket() as SSLSocket
        socket.use {
            it.connect(InetSocketAddress(host.trim(), port), 5000)
            it.soTimeout = 5000
            it.startHandshake()
            val writer = it.outputStream.bufferedWriter()
            writer.appendLine(message)
            writer.flush()
            return it.inputStream.bufferedReader().readLine() ?: "(empty response)"
        }
    }
}

