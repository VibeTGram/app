package org.vibetgram.core.tdlib

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/** Port matching TDLib's official org.drinkless.tdlib.JsonClient static API. */
interface TdJsonTransport {
    fun createClientId(): Int
    fun send(clientId: Int, request: String)
    fun receive(timeoutSeconds: Double): String?
}

/**
 * Process-wide JSON-Java receive loop with one typed [TdClient] per TDLib client id.
 * Request metadata remains internal and every callback is removed after one result.
 */
class TdJsonClientManager(
    private val transport: TdJsonTransport,
    private val receiveTimeoutSeconds: Double = 1.0,
    private val diagnosticSink: (String) -> Unit = {},
) : ClientManager, AutoCloseable {
    private val running = AtomicBoolean(true)
    private val clients = ConcurrentHashMap<Int, JsonTdClient>()
    private val receiver = Thread(::receiveLoop, "VibeTGram TDLib JSON receiver").apply {
        isDaemon = true
        start()
    }

    init {
        require(receiveTimeoutSeconds in 0.001..10.0)
    }

    override fun createClient(): TdClient {
        check(running.get()) { "TDLib client manager is closed" }
        val clientId = transport.createClientId()
        check(clientId > 0) { "TDLib returned an invalid client id" }
        val client = JsonTdClient(clientId)
        check(clients.putIfAbsent(clientId, client) == null) { "TDLib reused a live client id" }
        return client
    }

    override fun close() {
        if (!running.compareAndSet(true, false)) return
        clients.values.forEach(JsonTdClient::close)
        receiver.interrupt()
        clients.clear()
    }

    private fun receiveLoop() {
        while (running.get()) {
            val payload = try {
                transport.receive(receiveTimeoutSeconds)
            } catch (_: InterruptedException) {
                if (!running.get()) return
                continue
            } catch (failure: Throwable) {
                diagnosticSink("TDLib receive failed: ${failure::class.simpleName ?: "unknown"}")
                continue
            } ?: continue

            val envelope = try {
                TdJsonCodec.decode(payload)
            } catch (failure: Throwable) {
                diagnosticSink("TDLib payload rejected: ${failure::class.simpleName ?: "unknown"}")
                continue
            }
            val client = clients[envelope.clientId] ?: continue
            when (envelope) {
                is TdJsonEnvelope.Response -> client.complete(envelope.requestId, envelope.result)
                is TdJsonEnvelope.Update -> {
                    client.emit(envelope.update)
                    if (envelope.update is TdUpdate.AuthorizationStateChanged &&
                        envelope.update.state == AuthorizationState.CLOSED
                    ) {
                        clients.remove(envelope.clientId, client)
                        client.cancelPending()
                    }
                }
                is TdJsonEnvelope.Ignored -> Unit
            }
        }
    }

    private inner class JsonTdClient(private val clientId: Int) : TdClient {
        private val nextRequestId = AtomicLong(0)
        private val pending = ConcurrentHashMap<Long, (TdResult) -> Unit>()
        private val closing = AtomicBoolean(false)
        @Volatile private var updateHandler: (TdUpdate) -> Unit = {}

        override fun setUpdateHandler(handler: (TdUpdate) -> Unit) {
            updateHandler = handler
        }

        override fun send(function: TdFunction, callback: (TdResult) -> Unit): Long {
            check(!closing.get()) { "TDLib client is closing" }
            return sendInternal(function, callback)
        }

        override fun close() {
            if (!closing.compareAndSet(false, true)) return
            sendInternal(TdFunction.Close, callback = {})
        }

        fun complete(requestId: Long, result: TdResult) {
            pending.remove(requestId)?.let { callback ->
                runCatching { callback(result) }.onFailure(::reportCallbackFailure)
            }
        }

        fun emit(update: TdUpdate) {
            runCatching { updateHandler(update) }.onFailure(::reportCallbackFailure)
        }

        fun cancelPending() {
            val callbacks = pending.values.toList()
            pending.clear()
            callbacks.forEach { callback ->
                runCatching {
                    callback(TdResult.Error(TdError.Internal, safeMessage = "TDLib client closed"))
                }.onFailure(::reportCallbackFailure)
            }
        }

        private fun sendInternal(function: TdFunction, callback: (TdResult) -> Unit): Long {
            val requestId = nextRequestId.incrementAndGet()
            pending[requestId] = callback
            try {
                transport.send(clientId, TdJsonCodec.encode(function, clientId, requestId))
            } catch (failure: Throwable) {
                pending.remove(requestId)
                callback(TdResult.Error(TdError.Internal, safeMessage = "TDLib request was not sent"))
            }
            return requestId
        }

        private fun reportCallbackFailure(failure: Throwable) {
            diagnosticSink("TDLib callback failed: ${failure::class.simpleName ?: "unknown"}")
        }
    }
}
