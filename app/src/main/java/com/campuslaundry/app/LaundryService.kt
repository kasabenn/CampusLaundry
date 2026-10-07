package com.campuslaundry.app

import kotlinx.coroutines.delay
import timber.log.Timber

/**
 * Mode simulasi pengujian error handling untuk kebutuhan praktikum.
 */
enum class SimulationMode {
    NORMAL,
    SERVICE_UNAVAILABLE,
    PAYMENT_TIMEOUT,
    UNKNOWN_ERROR
}

/**
 * Service untuk memproses pesanan laundry kampus secara asinkron.
 * Mengimplementasikan Coroutine delay, defensive business rules, dan skenario error simulation.
 */
class LaundryService(
    private val paymentSimulator: PaymentSimulator = PaymentSimulator()
) {

    /**
     * Memproses pembuatan pesanan laundry.
     * Menggunakan suspend function dengan delay non-blocking (BUKAN Thread.sleep()).
     */
    suspend fun createOrder(
        order: LaundryOrder,
        simulationMode: SimulationMode,
        isSupervisorApproved: Boolean = false
    ): Result<LaundryOrder> = runCatching {
        Timber.d("Laundry order started: %s, Layanan=%s, Berat=%.1f kg",
            order.orderId, order.serviceType, order.weightKg)

        // Non-blocking coroutine delay (1200ms) untuk mensimulasikan antrean proses
        delay(1200)

        // ==========================================
        // 1. DEFENSIVE BUSINESS VALIDATION
        // ==========================================
        if (order.weightKg <= 0.0) {
            Timber.e("Validasi gagal: berat <= 0 (%s)", order.weightKg)
            throw IllegalArgumentException("Berat harus lebih dari 0 kg.")
        }

        if (order.weightKg > 20.0) {
            Timber.e("Validasi gagal: berat melebihi batas 20 kg (%s)", order.weightKg)
            throw LaundryWeightLimitException("Berat laundry maksimal 20 kg per pesanan.")
        }

        if (order.serviceType.contains("Express", ignoreCase = true) && order.weightKg > 10.0) {
            Timber.e("Validasi gagal: layanan Express melebihi 10 kg (%s)", order.weightKg)
            throw ExpressWeightLimitException("Layanan Express maksimal 10 kg.")
        }

        // ==========================================
        // 2. HIGH VALUE ORDER CHECK (> Rp500.000)
        // ==========================================
        if (order.totalPrice > 500000.0 && !isSupervisorApproved) {
            Timber.w("Pesanan bernilai tinggi (> Rp500.000) belum diotorisasi supervisor")
            throw HighValueLaundryOrderException("Nilai pesanan cukup tinggi. Masukkan kode konfirmasi petugas.")
        }

        // ==========================================
        // 3. SIMULATION MODE HANDLING
        // ==========================================
        when (simulationMode) {
            SimulationMode.SERVICE_UNAVAILABLE -> {
                Timber.e("Simulasi error: SERVICE_UNAVAILABLE dipicu.")
                throw LaundryServiceUnavailableException("Outlet laundry sedang tidak dapat menerima pesanan.")
            }
            SimulationMode.PAYMENT_TIMEOUT -> {
                Timber.e("Simulasi error: PAYMENT_TIMEOUT dipicu.")
                // Delegasikan ke simulator pembayaran
                val payRes = paymentSimulator.processPayment(order, simulationMode, isSupervisorApproved)
                payRes.getOrThrow()
            }
            SimulationMode.UNKNOWN_ERROR -> {
                Timber.e("Simulasi error: UNKNOWN_ERROR dipicu.")
                throw IllegalStateException("Terjadi kesalahan yang tidak terduga pada sistem internal.")
            }
            SimulationMode.NORMAL -> {
                val payRes = paymentSimulator.processPayment(order, simulationMode, isSupervisorApproved)
                payRes.getOrThrow()
            }
        }

        val completedOrder = order.copy(status = "SUCCESS")
        Timber.i("Laundry order success: %s", completedOrder.orderId)
        completedOrder
    }
}
