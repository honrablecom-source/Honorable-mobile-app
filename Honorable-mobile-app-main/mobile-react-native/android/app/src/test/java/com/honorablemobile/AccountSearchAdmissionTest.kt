package com.honorablemobile

import app.honorable.auth.AccountHttpError
import java.net.HttpURLConnection
import java.net.URL
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], application = android.app.Application::class, manifest = Config.NONE)
class AccountSearchAdmissionTest {
    @Test
    fun explicitStartRejectionCanConfirmNoCharge() {
        val error = AccountHttpError.fromResponse(SEARCH_START, 503, response(REJECTION))
        assertEquals(503, error.code)
        assertTrue(error.searchAdmissionRejected)
    }

    @Test
    fun otherRoutesAndUnconfirmedResponsesRetainTheGenericError() {
        val completion = AccountHttpError.fromResponse("/v1/search/complete", 503, response(REJECTION))
        assertFalse(completion.searchAdmissionRejected)
        assertFalse(AccountHttpError.fromResponse(SEARCH_START, 502, response(REJECTION)).searchAdmissionRejected)
        for (body in listOf(null, "Unavailable", """{"error":"UNAVAILABLE"}""", REJECTION + " ".repeat(300))) {
            assertFalse(AccountHttpError.fromResponse(SEARCH_START, 503, response(body)).searchAdmissionRejected)
        }
    }

    private fun response(body: String?) = object : HttpURLConnection(URL("https://example.invalid")) {
        override fun connect() = Unit
        override fun disconnect() = Unit
        override fun usingProxy() = false
        override fun getErrorStream() = body?.byteInputStream()
    }

    private companion object {
        const val SEARCH_START = "/v1/search/start"
        const val REJECTION = """{"error":"SEARCH_COMPLETION_VERIFICATION_REQUIRED"}"""
    }
}
