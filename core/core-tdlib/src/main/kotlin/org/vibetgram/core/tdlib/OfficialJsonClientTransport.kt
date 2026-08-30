package org.vibetgram.core.tdlib

import org.drinkless.tdlib.JsonClient

/** Android production transport backed directly by TDLib's official JSON-Java JNI class. */
class OfficialJsonClientTransport : TdJsonTransport {
    override fun createClientId(): Int = JsonClient.createClientId()

    override fun send(clientId: Int, request: String) {
        JsonClient.send(clientId, request)
    }

    override fun receive(timeoutSeconds: Double): String? = JsonClient.receive(timeoutSeconds)
}
