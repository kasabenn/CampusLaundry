package com.campuslaundry.app

import kotlinx.coroutines.delay
import timber.log.Timber

/**
 * Simulator gerbang pembayaran lokal untuk demonstrasi praktikum error handling.
 * Mendukung simulasi timeout, metode pembayaran, dan validasi transaksi bernilai tinggi.
 */
class PaymentSimulator {

    /**
     * Memproses pembayaran secara asinkron.
     * Tidak menghubungkan ke network luar; seluruh skenario disimulasikan lokal.
     *
     * @param order Data pesanan yang akan dibayar
     * @param simulationMode Mode simulasi yang dipilih user / tester
     * @param isSupervisorApproved Status apakah supervisor sudah memberikan otorisasi untuk order bernilai tinggi
     */
    suspend fun processPayment(
        order: LaundryOrder,
        simulationMode: SimulationMode,
        isSupervisorApproved: Boolean = false
    ): Result<Boolean> {
        Timber.d("Memulai simulasi pembayaran untuk Order ID: %s, Metode: %s", order.orderId, order.paymentMethod)

        // Simulasi latensi jaringan pembayaran
        delay(800)

        // 1. Skenario High Value Order (> Rp500.000)
        if (order.totalPrice > 500000.0 && !isSupervisorApproved) {
            Timber.w("Transaksi melebihi batas Rp500.000 tanpa otorisasi supervisor: %s", order.orderId)
            return Result.failure(
                HighValueLaundryOrderException("Pesanan bernilai tinggi (> Rp500.000) membutuhkan konfirmasi petugas.")
            )
        }

        // 2. Skenario simulasi timeout
        if (simulationMode == SimulationMode.PAYMENT_TIMEOUT) {
            Timber.e("Simulasi pembayaran: Payment Gateway Timeout pada metode %s", order.paymentMethod)
            return Result.failure(
                PaymentTimeoutException("Proses pembayaran ${order.paymentMethod} mengalami timeout.")
            )
        }

        Timber.i("Pembayaran berhasil untuk Order ID: %s dengan metode %s", order.orderId, order.paymentMethod)
        return Result.success(true)
    }
}
