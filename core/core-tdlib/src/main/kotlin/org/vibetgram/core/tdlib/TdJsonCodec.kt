package org.vibetgram.core.tdlib

import java.util.Base64
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put

sealed interface TdJsonEnvelope {
    val clientId: Int

    data class Response(
        override val clientId: Int,
        val requestId: Long,
        val result: TdResult,
    ) : TdJsonEnvelope

    data class Update(
        override val clientId: Int,
        val update: TdUpdate,
    ) : TdJsonEnvelope

    data class Ignored(override val clientId: Int) : TdJsonEnvelope
}

/** Exact JSON codec for the typed subset used against the pinned TDLib schema. */
object TdJsonCodec {
    private val json = Json { ignoreUnknownKeys = true }

    fun encode(function: TdFunction, clientId: Int, requestId: Long): String {
        require(clientId > 0)
        require(requestId > 0)
        val payload = when (function) {
            is TdFunction.SetTdlibParameters -> encodeParameters(function.parameters)
            is TdFunction.SetAuthenticationPhoneNumber -> buildJsonObject {
                put("@type", "setAuthenticationPhoneNumber")
                put("phone_number", function.phoneNumber)
                put("settings", JsonNull)
            }
            is TdFunction.CheckAuthenticationCode -> buildJsonObject {
                put("@type", "checkAuthenticationCode")
                put("code", function.copyCode().consumeChars())
            }
            is TdFunction.CheckAuthenticationPassword -> buildJsonObject {
                put("@type", "checkAuthenticationPassword")
                put("password", function.copyPassword().consumeChars())
            }
            is TdFunction.RequestQrCodeAuthentication -> buildJsonObject {
                put("@type", "requestQrCodeAuthentication")
                put("other_user_ids", buildJsonArray {
                    function.otherUserIds.forEach { add(JsonPrimitive(it)) }
                })
            }
            is TdFunction.RegisterUser -> buildJsonObject {
                put("@type", "registerUser")
                put("first_name", function.firstName)
                put("last_name", function.lastName)
                put("disable_notification", function.disableNotification)
            }
            TdFunction.LogOut -> typeOnly("logOut")
            TdFunction.Close -> typeOnly("close")
            is TdFunction.GetChats -> buildJsonObject {
                put("@type", "getChats")
                put("chat_list", JsonNull)
                put("limit", function.limit)
            }
            is TdFunction.GetChat -> buildJsonObject {
                put("@type", "getChat")
                put("chat_id", function.chatId)
            }
            is TdFunction.GetChatHistory -> buildJsonObject {
                put("@type", "getChatHistory")
                put("chat_id", function.chatId)
                put("from_message_id", function.fromMessageId)
                put("offset", 0)
                put("limit", function.limit)
                put("only_local", false)
            }
            is TdFunction.GetMessage -> buildJsonObject {
                put("@type", "getMessage")
                put("chat_id", function.chatId)
                put("message_id", function.messageId)
            }
            is TdFunction.SendMessage -> buildJsonObject {
                put("@type", "sendMessage")
                put("chat_id", function.chatId)
                put("topic_id", JsonNull)
                put("reply_to", function.replyToMessageId?.let { id ->
                    buildJsonObject {
                        put("@type", "inputMessageReplyToMessage")
                        put("message_id", id)
                        put("quote", JsonNull)
                        put("checklist_task_id", 0)
                    }
                } ?: JsonNull)
                put("options", buildJsonObject {
                    put("@type", "messageSendOptions")
                    put("disable_notification", function.disableNotification)
                })
                put("reply_markup", JsonNull)
                put("input_message_content", buildJsonObject {
                    put("@type", "inputMessageText")
                    put("text", buildJsonObject {
                        put("@type", "formattedText")
                        put("text", function.text)
                        put("entities", JsonArray(emptyList()))
                    })
                    put("link_preview_options", JsonNull)
                    put("clear_draft", true)
                })
            }
        }
        return JsonObject(payload + mapOf(
            "@client_id" to JsonPrimitive(clientId),
            "@extra" to JsonPrimitive(requestId),
        )).toString()
    }

    fun decode(value: String): TdJsonEnvelope {
        val root = json.parseToJsonElement(value).jsonObject
        val clientId = root.int("@client_id") ?: 0
        val requestId = root.long("@extra")
        if (requestId != null) {
            return TdJsonEnvelope.Response(clientId, requestId, decodeResult(root))
        }
        val update = decodeUpdate(root) ?: return TdJsonEnvelope.Ignored(clientId)
        return TdJsonEnvelope.Update(clientId, update)
    }

    private fun encodeParameters(parameters: TdlibParameters): JsonObject = buildJsonObject {
        put("@type", "setTdlibParameters")
        put("use_test_dc", false)
        put("database_directory", parameters.databaseDirectory)
        put("files_directory", parameters.filesDirectory)
        put("database_encryption_key", Base64.getEncoder().encodeToString(parameters.databaseEncryptionKey))
        put("use_file_database", true)
        put("use_chat_info_database", true)
        put("use_message_database", true)
        put("use_secret_chats", true)
        put("api_id", parameters.apiId)
        put("api_hash", parameters.apiHash)
        put("system_language_code", "en")
        put("device_model", parameters.deviceModel)
        put("system_version", parameters.systemVersion)
        put("application_version", parameters.applicationVersion)
    }

    private fun decodeResult(root: JsonObject): TdResult = when (root.type) {
        "ok" -> TdResult.Ok
        "error" -> decodeError(root)
        "message" -> decodeMessage(root)?.let(TdResult::Message)
            ?: TdResult.Error(TdError.Unsupported)
        "messages" -> {
            val messages = root["messages"]?.jsonArray?.mapNotNull { decodeMessage(it.jsonObject) }.orEmpty()
            TdResult.ChatHistory(messages, hasMore = messages.isNotEmpty())
        }
        "chats" -> TdResult.ChatIds(
            chatIds = root["chat_ids"]?.jsonArray?.mapNotNull { it.jsonPrimitive.longOrNull }.orEmpty(),
            totalCount = root.int("total_count") ?: 0,
        )
        "chat" -> decodeChat(root)?.let(TdResult::Chat) ?: TdResult.Error(TdError.Unsupported)
        else -> TdResult.Error(TdError.Unsupported)
    }

    private fun decodeError(root: JsonObject): TdResult.Error {
        val code = root.int("code") ?: 0
        val message = root.string("message").orEmpty()
        val category = when {
            code == 404 -> TdError.NotFound
            code == 409 -> TdError.Conflict
            code == 429 || message.startsWith("FLOOD_WAIT") -> TdError.RateLimited
            code in 500..599 -> TdError.NetworkUnavailable
            code == 400 -> TdError.InvalidParameters
            else -> TdError.Internal
        }
        return TdResult.Error(category, code = code, safeMessage = message.safeErrorCode())
    }

    /** Keep only Telegram's uppercase error identifier; never expose raw server details. */
    private fun String.safeErrorCode(): String? = trim()
        .substringBefore(' ')
        .takeIf { SAFE_ERROR_CODE.matches(it) }

    private fun decodeUpdate(root: JsonObject): TdUpdate? = when (root.type) {
        "updateAuthorizationState" -> decodeAuthorization(root["authorization_state"]?.jsonObject ?: return null)
        "updateNewMessage" -> decodeMessage(root["message"]?.jsonObject ?: return null)?.let(TdUpdate::NewMessage)
        "updateDeleteMessages" -> {
            val chatId = root.long("chat_id") ?: return null
            val messageId = root["message_ids"]?.jsonArray?.firstOrNull()?.jsonPrimitive?.longOrNull ?: return null
            TdUpdate.MessageDeleted(chatId, messageId)
        }
        else -> null
    }

    private fun decodeAuthorization(state: JsonObject): TdUpdate.AuthorizationStateChanged {
        val type = state.type
        return when (type) {
            "authorizationStateWaitTdlibParameters" -> auth(AuthorizationState.WAITING_PARAMETERS)
            "authorizationStateWaitPhoneNumber" -> auth(AuthorizationState.WAITING_PHONE_NUMBER)
            "authorizationStateWaitCode" -> auth(AuthorizationState.WAITING_CODE)
            "authorizationStateWaitOtherDeviceConfirmation" -> auth(
                AuthorizationState.WAITING_QR_CODE,
                qrCodeLink = state.string("link"),
            )
            "authorizationStateWaitRegistration" -> {
                val termsObject = state["terms_of_service"]?.jsonObject
                val terms = termsObject?.let {
                    AuthorizationTerms(
                        text = it["text"]?.jsonObject?.string("text").orEmpty(),
                        minimumUserAge = it.int("min_user_age")?.takeIf { age -> age > 0 },
                        showPopup = it.boolean("show_popup") ?: false,
                    )
                }
                auth(AuthorizationState.WAITING_REGISTRATION, terms = terms)
            }
            "authorizationStateWaitPassword" -> auth(
                AuthorizationState.WAITING_PASSWORD,
                passwordHint = state.string("password_hint"),
            )
            "authorizationStateReady" -> auth(AuthorizationState.READY)
            "authorizationStateLoggingOut" -> auth(AuthorizationState.LOGGING_OUT)
            "authorizationStateClosing" -> auth(AuthorizationState.CLOSING)
            "authorizationStateClosed" -> auth(AuthorizationState.CLOSED)
            else -> auth(AuthorizationState.UNKNOWN)
        }
    }

    private fun decodeMessage(root: JsonObject): TdMessage? {
        val chatId = root.long("chat_id") ?: return null
        val id = root.long("id") ?: return null
        val date = root.long("date") ?: return null
        val content = root["content"]?.jsonObject
        val text = if (content?.type == "messageText") {
            content["text"]?.jsonObject?.string("text").orEmpty()
        } else {
            ""
        }
        return TdMessage(chatId, id, text, date)
    }

    private fun decodeChat(root: JsonObject): TdChat? {
        val id = root.long("id") ?: return null
        val title = root.string("title")?.takeIf(String::isNotBlank) ?: return null
        return TdChat(id, title, unreadCount = root.int("unread_count") ?: 0)
    }

    private fun auth(
        state: AuthorizationState,
        passwordHint: String? = null,
        qrCodeLink: String? = null,
        terms: AuthorizationTerms? = null,
    ) = TdUpdate.AuthorizationStateChanged(state, passwordHint, qrCodeLink, terms)

    private fun typeOnly(type: String) = buildJsonObject { put("@type", type) }

    private val JsonObject.type: String
        get() = string("@type").orEmpty()

    private fun JsonObject.string(name: String): String? = this[name]?.jsonPrimitive?.contentOrNull
    private fun JsonObject.int(name: String): Int? = this[name]?.jsonPrimitive?.intOrNull
    private fun JsonObject.long(name: String): Long? = this[name]?.jsonPrimitive?.longOrNull
    private fun JsonObject.boolean(name: String): Boolean? = this[name]?.jsonPrimitive?.booleanOrNull

    private fun CharArray.consumeChars(): String = try {
        concatToString()
    } finally {
        fill('\u0000')
    }

    private val SAFE_ERROR_CODE = Regex("^[A-Z][A-Z0-9_]{2,63}$")
}
