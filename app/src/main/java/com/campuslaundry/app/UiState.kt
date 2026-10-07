package com.campuslaundry.app

/**
 * Representasi UI State menggunakan sealed class.
 * Tujuannya agar antarmuka mengetahui secara deterministik kondisi:
 * - Idle: Form siap diisi oleh user
 * - Loading: Operasi asinkron sedang berjalan
 * - Success: Transaksi berhasil diproses
 * - Error: Transaksi mengalami kegagalan, lengkap dengan flag retryability dan error code
 */
sealed class UiState<out T> {
    object Idle : UiState<Nothing>()
    object Loading : UiState<Nothing>()
    data class Success<out T>(val data: T) : UiState<T>()
    data class Error(
        val message: String,
        val canRetry: Boolean = true,
        val errorCode: String? = null
    ) : UiState<Nothing>()
}
