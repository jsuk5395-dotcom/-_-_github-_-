package com.example.pnu_me.data.source

import com.example.pnu_me.domain.hazard.RelayHttpResponse
import com.example.pnu_me.domain.hazard.RelayTransport
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.StandardCharsets

/**
 * 로컬 중계 서버 접속 설정.
 * - 에뮬레이터에서 개발 PC는 10.0.2.2 로 접근한다.
 * - 실제 서비스는 HTTPS 도메인과 인증 토큰으로 바꿔야 하며 cleartext 허용은 개발용 주소에만 있다.
 */
object RelayEndpoint {
    const val DEFAULT_BASE_URL: String = "http://10.0.2.2:8787"
}

/** 추가 라이브러리 없이 HttpURLConnection 으로 호출한다. 메인 스레드에서 부르면 안 된다. */
class HttpUrlRelayTransport(
    private val baseUrl: String = RelayEndpoint.DEFAULT_BASE_URL,
    private val token: String? = null,
    private val timeoutMillis: Int = 3_000,
) : RelayTransport {
    override fun execute(method: String, path: String, body: String?): RelayHttpResponse {
        val connection = URL(baseUrl + path).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.connectTimeout = timeoutMillis
            connection.readTimeout = timeoutMillis
            connection.useCaches = false
            connection.setRequestProperty("Accept", "application/json")
            token?.let { connection.setRequestProperty("X-Relay-Token", it) }
            if (body != null) {
                val bytes = body.toByteArray(StandardCharsets.UTF_8)
                connection.doOutput = true
                connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                connection.setFixedLengthStreamingMode(bytes.size)
                connection.outputStream.use { it.write(bytes) }
            }
            val status = connection.responseCode
            val stream = if (status >= 400) connection.errorStream else connection.inputStream
            val text = stream?.use { it.readBytes().toString(StandardCharsets.UTF_8) } ?: ""
            return RelayHttpResponse(status, text)
        } catch (e: IOException) {
            throw e
        } catch (e: RuntimeException) {
            // 잘못된 URL·보안 정책(cleartext 차단 등)도 연결 실패로 다룬다.
            throw IOException(e.message ?: "중계 서버 호출 실패", e)
        } finally {
            connection.disconnect()
        }
    }
}
