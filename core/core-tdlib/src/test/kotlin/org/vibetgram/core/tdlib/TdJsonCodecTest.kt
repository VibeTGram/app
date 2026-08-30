package org.vibetgram.core.tdlib

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class TdJsonCodecTest {
    @Test
    fun `encodes pinned TDLib setup and auth requests with request correlation metadata`() {
        val parameters = TdlibParameters(
            databaseDirectory = "/account/tdlib",
            filesDirectory = "/account/files",
            apiId = 123,
            apiHash = "api-hash",
            deviceModel = "Pixel",
            databaseEncryptionKey = byteArrayOf(1, 2, 3),
        )

        val encoded = TdJsonCodec.encode(
            TdFunction.SetTdlibParameters(parameters),
            clientId = 7,
            requestId = 42,
        )

        assertTrue(encoded.contains("\"@type\":\"setTdlibParameters\""))
        assertTrue(encoded.contains("\"@client_id\":7"))
        assertTrue(encoded.contains("\"@extra\":42"))
        assertTrue(encoded.contains("\"files_directory\":\"/account/files\""))
        assertTrue(encoded.contains("\"database_encryption_key\":\"AQID\""))
        assertTrue(encoded.contains("\"use_file_database\":true"))
        assertTrue(encoded.contains("\"use_chat_info_database\":true"))
        assertTrue(encoded.contains("\"use_message_database\":true"))
    }

    @Test
    fun `decodes detailed authorization updates from the pinned schema`() {
        val password = TdJsonCodec.decode("""
            {"@type":"updateAuthorizationState","authorization_state":{
              "@type":"authorizationStateWaitPassword","password_hint":"correct horse",
              "has_recovery_email_address":true,"has_passport_data":false,
              "recovery_email_address_pattern":"m***@example.org"},"@client_id":3}
        """.trimIndent())
        val passwordUpdate = assertIs<TdJsonEnvelope.Update>(password)
        val passwordState = assertIs<TdUpdate.AuthorizationStateChanged>(passwordUpdate.update)
        assertEquals(AuthorizationState.WAITING_PASSWORD, passwordState.state)
        assertEquals("correct horse", passwordState.passwordHint)

        val qr = TdJsonCodec.decode("""
            {"@type":"updateAuthorizationState","authorization_state":{
              "@type":"authorizationStateWaitOtherDeviceConfirmation",
              "link":"tg://login?token=opaque"},"@client_id":3}
        """.trimIndent())
        val qrState = assertIs<TdUpdate.AuthorizationStateChanged>(assertIs<TdJsonEnvelope.Update>(qr).update)
        assertEquals(AuthorizationState.WAITING_QR_CODE, qrState.state)
        assertEquals("tg://login?token=opaque", qrState.qrCodeLink)

        val registration = TdJsonCodec.decode("""
            {"@type":"updateAuthorizationState","authorization_state":{
              "@type":"authorizationStateWaitRegistration","terms_of_service":{
                "@type":"termsOfService","text":{"@type":"formattedText","text":"Telegram terms","entities":[]},
                "min_user_age":16,"show_popup":true}},"@client_id":3}
        """.trimIndent())
        val registrationState = assertIs<TdUpdate.AuthorizationStateChanged>(
            assertIs<TdJsonEnvelope.Update>(registration).update,
        )
        assertEquals(AuthorizationState.WAITING_REGISTRATION, registrationState.state)
        assertEquals("Telegram terms", registrationState.terms?.text)
        assertEquals(16, registrationState.terms?.minimumUserAge)
        assertEquals(true, registrationState.terms?.showPopup)
    }

    @Test
    fun `maps upstream errors to a safe error identifier without raw details`() {
        val decoded = TdJsonCodec.decode(
            """{"@type":"error","code":400,"message":"PHONE_CODE_INVALID 123456","@client_id":1,"@extra":9}""",
        )
        val response = assertIs<TdJsonEnvelope.Response>(decoded)
        val error = assertIs<TdResult.Error>(response.result)
        assertEquals(TdError.InvalidParameters, error.error)
        assertEquals("PHONE_CODE_INVALID", error.safeMessage)
        assertEquals(9, response.requestId)
    }

    @Test
    fun `decodes pinned chat id pages and negative-id chat details`() {
        val page = assertIs<TdJsonEnvelope.Response>(TdJsonCodec.decode(
            """{"@type":"chats","total_count":2,"chat_ids":[-100123,42],"@client_id":1,"@extra":9}""",
        ))
        val ids = assertIs<TdResult.ChatIds>(page.result)
        assertEquals(listOf(-100123L, 42L), ids.chatIds)
        assertEquals(2, ids.totalCount)

        val detail = assertIs<TdJsonEnvelope.Response>(TdJsonCodec.decode(
            """{"@type":"chat","id":-100123,"title":"Core team","unread_count":7,"@client_id":1,"@extra":10}""",
        ))
        val chat = assertIs<TdResult.Chat>(detail.result).chat
        assertEquals(-100123L, chat.id)
        assertEquals("Core team", chat.title)
        assertEquals(7, chat.unreadCount)
    }
}
