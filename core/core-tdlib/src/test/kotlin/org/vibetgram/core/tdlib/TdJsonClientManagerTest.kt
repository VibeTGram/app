package org.vibetgram.core.tdlib

import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TdJsonClientManagerTest {
    @Test
    fun `correlates json java responses and routes ordered updates by client id`() {
        val transport = RecordingJsonTransport()
        val manager = TdJsonClientManager(transport, receiveTimeoutSeconds = 0.01)
        val client = manager.createClient()
        val updates = LinkedBlockingQueue<TdUpdate>()
        val results = LinkedBlockingQueue<TdResult>()
        client.setUpdateHandler(updates::add)

        val requestId = client.send(TdFunction.SetAuthenticationPhoneNumber("+15551234567"), results::add)
        val request = transport.sent.poll(1, TimeUnit.SECONDS) ?: error("request wasn't sent")
        assertTrue(request.json.contains("\"@extra\":$requestId"))
        transport.received.add("""{"@type":"ok","@client_id":${request.clientId},"@extra":$requestId}""")
        assertIs<TdResult.Ok>(results.poll(1, TimeUnit.SECONDS))

        transport.received.add("""
            {"@type":"updateAuthorizationState","authorization_state":{
              "@type":"authorizationStateWaitOtherDeviceConfirmation","link":"tg://login?token=x"},
              "@client_id":${request.clientId}}
        """.trimIndent())
        val update = assertIs<TdUpdate.AuthorizationStateChanged>(updates.poll(1, TimeUnit.SECONDS))
        assertEquals(AuthorizationState.WAITING_QR_CODE, update.state)
        assertEquals("tg://login?token=x", update.qrCodeLink)

        client.close()
        assertTrue(transport.sent.poll(1, TimeUnit.SECONDS)!!.json.contains("\"@type\":\"close\""))
        manager.close()
    }

    @Test
    fun `malformed native payload is contained and does not kill receive loop`() {
        val transport = RecordingJsonTransport()
        val failures = LinkedBlockingQueue<String>()
        val manager = TdJsonClientManager(
            transport,
            receiveTimeoutSeconds = 0.01,
            diagnosticSink = failures::add,
        )
        val client = manager.createClient()
        val updates = LinkedBlockingQueue<TdUpdate>()
        client.setUpdateHandler(updates::add)

        transport.received.add("not-json")
        transport.received.add("""
            {"@type":"updateAuthorizationState","authorization_state":{"@type":"authorizationStateReady"},
             "@client_id":1}
        """.trimIndent())

        assertTrue(failures.poll(1, TimeUnit.SECONDS)!!.startsWith("TDLib payload rejected:"))
        assertEquals(AuthorizationState.READY, assertIs<TdUpdate.AuthorizationStateChanged>(
            updates.poll(1, TimeUnit.SECONDS),
        ).state)
        manager.close()
    }
}

private class RecordingJsonTransport : TdJsonTransport {
    data class Sent(val clientId: Int, val json: String)

    private var nextClientId = 1
    val sent = LinkedBlockingQueue<Sent>()
    val received = LinkedBlockingQueue<String>()

    override fun createClientId(): Int = nextClientId++

    override fun send(clientId: Int, request: String) {
        sent.add(Sent(clientId, request))
    }

    override fun receive(timeoutSeconds: Double): String? =
        received.poll((timeoutSeconds * 1_000).toLong().coerceAtLeast(1), TimeUnit.MILLISECONDS)
}
