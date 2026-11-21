package com.simon.harmonichackernews.network

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class CloudClientCertificateTest {
    private val config = CloudSummaryConfig(
        "https://ai.example.test/v1", apiKey = "", model = "test-model",
        streamResponses = false, clientCertificate = "phone",
    )

    @Test
    fun certificateRequestsUseOnlyTheCertificateTransportAndNeedNoApiKey() = runTest {
        val shared = HttpClient(MockEngine { error("The shared transport holds no certificate") })
        var authorized = true
        val certificateTransport = HttpClient(MockEngine { request ->
            authorized = HttpHeaders.Authorization in request.headers
            respond(
                """{"choices":[{"message":{"content":"Summary"}}]}""",
                headers = headersOf(HttpHeaders.ContentType, "application/json"),
            )
        })
        val requested = mutableListOf<String>()
        try {
            val repository = KtorCloudSummaryRepository(
                KtorHttpClient(shared), "test", StandardTestDispatcher(testScheduler),
                clientCertificateClient = { requested += it; certificateTransport },
            )
            assertTrue(repository.summarize(config, "Text").toList().last() is CloudSummaryEvent.Success)
            assertEquals(listOf("phone"), requested)
            assertFalse(authorized)
        } finally {
            shared.close()
            certificateTransport.close()
        }
    }

    @Test
    fun certificateIsRejectedWhereTheHostCannotPresentOne() = runTest {
        val shared = HttpClient(MockEngine { error("Must not fall back to the shared transport") })
        try {
            val repository = KtorCloudSummaryRepository(
                KtorHttpClient(shared), "test", StandardTestDispatcher(testScheduler),
            )
            assertFailsWith<CloudSummaryException> { repository.summarize(config, "Text").toList() }
            assertFailsWith<CloudSummaryException> {
                repository.summarize(config.copy(clientCertificate = ""), "Text").toList()
            }
        } finally {
            shared.close()
        }
    }
}
