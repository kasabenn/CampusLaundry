package com.campuslaundry.app

/**
 * Kumpulan Custom Exception untuk domain CampusLaundry.
 * Setiap custom exception membawa pesan error yang relevan untuk kebutuhan bisnis,
 * pemetaan UI State, serta identifikasi masalah saat proses debugging.
 */
class LaundryWeightLimitException(message: String = "Berat laundry melebihi batas maksimal.") :
    Exception(message)

class ExpressWeightLimitException(message: String = "Layanan Express maksimal 10 kg.") :
    Exception(message)

class LaundryServiceUnavailableException(message: String = "Layanan laundry sedang tidak tersedia.") :
    Exception(message)

class PaymentTimeoutException(message: String = "Proses pembayaran mengalami timeout.") :
    Exception(message)

class HighValueLaundryOrderException(message: String = "Pesanan bernilai tinggi membutuhkan konfirmasi tambahan.") :
    Exception(message)

class InvalidOrderException(message: String) :
    Exception(message)
