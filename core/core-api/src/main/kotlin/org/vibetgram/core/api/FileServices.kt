package org.vibetgram.core.api

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow

/** Metadata for media crossing the semantic boundary; no path or descriptor is accepted. */
data class MediaMetadata(
    val mimeType: String,
    val sizeBytes: Long,
    val displayName: String? = null,
) {
    init {
        require(mimeType.isNotBlank()) { "media MIME type must not be blank" }
        require(sizeBytes >= 0) { "media size must not be negative" }
        require(displayName == null || displayName.isNotBlank()) {
            "media display name must not be blank"
        }
    }
}

enum class FileTransferState {
    Remote,
    Downloading,
    Downloaded,
    Uploading,
    Failed,
}

/** Immutable state for a TDLib-owned file transfer. */
data class FileSnapshot(
    val handle: FileHandle,
    val state: FileTransferState,
    val transferredBytes: Long,
    val totalBytes: Long? = null,
) {
    init {
        require(transferredBytes >= 0) { "transferred bytes must not be negative" }
        require(totalBytes == null || totalBytes >= transferredBytes) {
            "total bytes must be at least transferred bytes"
        }
    }
}

/** File lifecycle operations; adapters own TDLib transfer state. */
interface FileService {
    fun observeFile(account: AccountHandle, file: FileHandle): Flow<FileSnapshot> = emptyFlow()

    suspend fun startDownload(account: AccountHandle, file: FileHandle): TelegramResult<Unit>

    suspend fun pauseDownload(account: AccountHandle, file: FileHandle): TelegramResult<Unit>

    suspend fun cancelDownload(account: AccountHandle, file: FileHandle): TelegramResult<Unit>
}

/** Host-mediated media import/export without Android or filesystem types in core-api. */
interface UserMediaPort {
    suspend fun importMedia(scope: HandleScope, metadata: MediaMetadata): TelegramResult<MediaHandle>

    suspend fun exportMedia(scope: HandleScope, media: MediaHandle): TelegramResult<FileHandle>
}
