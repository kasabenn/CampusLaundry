package com.campuslaundry.app

/**
 * Model data transaksi pemesanan laundry kampus.
 *
 * Nilai status yang didukung:
 * - DRAFT: Pesanan baru dirancang dalam form
 * - PROCESSING: Pesanan sedang dikirim ke simulator / antrean
 * - SUCCESS: Pesanan selesai dan lunas / terkonfirmasi
 * - FAILED: Pesanan gagal diproses
 * - WAITING_PAYMENT: Fallback offline di mana user diarahkan bayar di outlet
 */
data class LaundryOrder(
    val orderId: String,
    val serviceType: String,
    val weightKg: Double,
    val pricePerKg: Double,
    val totalPrice: Double,
    val paymentMethod: String,
    var status: String
)
