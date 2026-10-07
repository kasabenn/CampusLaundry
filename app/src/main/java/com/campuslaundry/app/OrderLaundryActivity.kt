package com.campuslaundry.app

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.campuslaundry.app.databinding.ActivityOrderLaundryBinding
import com.campuslaundry.app.databinding.DialogSupervisorBinding
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.launch
import timber.log.Timber
import java.text.NumberFormat
import java.util.Locale

/**
 * Aktivitas Pemesanan Laundry Kampus.
 *
 * Fokus Kompetensi Modul 12:
 * 1. try-catch
 * 2. runCatching (onSuccess & onFailure)
 * 3. Kotlin Coroutines & CoroutineExceptionHandler
 * 4. Timber Logging bertingkat (Debug, Info, Warn, Error)
 * 5. sealed class UiState (Idle, Loading, Success, Error)
 * 6. Custom Exception (LaundryWeightLimitException, PaymentTimeoutException, dll)
 * 7. Retry Pattern & Max Retry Threshold (3x)
 * 8. Fallback Mode (Bayar di Outlet -> WAITING_PAYMENT -> Konfirmasi Outlet -> SUCCESS)
 * 9. Defensive Programming (tidak ada force close, tidak ada swallow exception, tidak ada '!!')
 * 10. Breakpoint-friendly calculation pada kalkulasi harga.
 */
class OrderLaundryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOrderLaundryBinding
    private val laundryService = LaundryService()
    private val rupiahFormat = NumberFormat.getCurrencyInstance(Locale("id", "ID"))

    // State transaksi dan pengulangan (Retry)
    private var retryAttempt = 0
    private val maxRetryThreshold = 3
    private var isSupervisorApproved = false
    private var currentOrder: LaundryOrder? = null
    private var orderSequence = 1

    /**
     * CoroutineExceptionHandler sebagai fallback keselamatan coroutine paling akhir.
     * Mencegah aplikasi mengalami force close apabila ada unhandled exception pada coroutine.
     */
    private val coroutineExceptionHandler = CoroutineExceptionHandler { _, throwable ->
        Timber.e(throwable, "Unhandled coroutine exception tertangkap di CoroutineExceptionHandler")
        runOnUiThread {
            renderUiState(
                UiState.Error(
                    message = "Terjadi kegagalan coroutine sistem: ${throwable.localizedMessage ?: "Unknown Error"}",
                    canRetry = false,
                    errorCode = "ERR_COROUTINE_UNHANDLED"
                )
            )
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d("OrderLaundryActivity onCreate: Form pemesanan dibuka")

        binding = ActivityOrderLaundryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupToolbar()
        setupListeners()
        updatePriceAndTotal()
        renderUiState(UiState.Idle)
    }

    private fun setupToolbar() {
        binding.toolbarOrder.setNavigationOnClickListener {
            finish()
        }
    }

    private fun setupListeners() {
        // Listener perubahan layanan
        binding.rgService.setOnCheckedChangeListener { _, checkedId ->
            val serviceName = when (checkedId) {
                R.id.rbWashOnly -> "Cuci Kering"
                R.id.rbWashIron -> "Cuci + Setrika"
                R.id.rbExpress -> "Express"
                else -> "Cuci Kering"
            }
            Timber.d("Layanan laundry dipilih: %s", serviceName)
            updatePriceAndTotal()
        }

        // Listener perubahan berat laundry secara dinamis
        binding.etWeight.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                updatePriceAndTotal()
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // Checkbox tambahan paket bed cover khusus (memudahkan simulasi High Value > Rp500.000)
        binding.cbSpecialBedcover.setOnCheckedChangeListener { _, isChecked ->
            Timber.d("Tambahan bed cover diubah: %b", isChecked)
            updatePriceAndTotal()
        }

        // Tombol Buat Pesanan
        binding.btnCreateOrder.setOnClickListener {
            hideKeyboard()
            Timber.i("Tombol Buat Pesanan ditekan oleh pengguna")
            retryAttempt = 0
            isSupervisorApproved = false
            processCreateOrder()
        }

        // Tombol Retry (Coba Lagi)
        binding.btnRetry.setOnClickListener {
            hideKeyboard()
            // BREAKPOINT: Saat retry bertambah
            retryAttempt++
            Timber.w("Retry attempt: %d dari %d", retryAttempt, maxRetryThreshold)

            if (retryAttempt > maxRetryThreshold) {
                // Sesuai aturan: Jika sudah 3x gagal, jangan izinkan retry ke-4
                showMaxRetryExceededDialog()
            } else {
                binding.tvRetryCounter.text = "Percobaan $retryAttempt dari $maxRetryThreshold"
                processCreateOrder()
            }
        }

        // Tombol Konfirmasi Pembayaran di Outlet (Fallback Mode)
        binding.btnConfirmOutletPayment.setOnClickListener {
            confirmOutletPaymentRecovery()
        }

        // Tombol Pesan Lagi (Reset form ke keadaan awal)
        binding.btnNewOrder.setOnClickListener {
            resetForm()
        }
    }

    /**
     * Kalkulasi tarif laundry yang ramah terhadap Android Studio Debugger.
     * Kode dipecah menjadi variabel eksplisit agar mahasiswa dapat memasang breakpoint,
     * melakukan Step Over / Step Into, serta menginspeksi nilai variables.
     */
    // BREAKPOINT DEMO: inspect weight, pricePerKg, subtotal and total
    private fun calculateLaundryCost(
        weight: Double,
        pricePerKg: Double,
        serviceFee: Double = 0.0
    ): Double {
        Timber.d("Calculating laundry cost")

        // BREAKPOINT: inspect weight and pricePerKg
        val currentWeight = weight
        val currentPricePerKg = pricePerKg

        // BREAKPOINT: inspect subtotal calculation
        val subtotal = currentWeight * currentPricePerKg

        val additionalFee = serviceFee

        // BREAKPOINT: inspect total calculation
        val total = subtotal + additionalFee

        Timber.d("Kalkulasi selesai: Berat=%.2f kg, Tarif=%.0f, Subtotal=%.0f, BiayaTambahan=%.0f, Total=%.0f",
            currentWeight, currentPricePerKg, subtotal, additionalFee, total)

        return total
    }

    /**
     * Memperbarui informasi harga per kg dan estimasi total pada UI secara defensif.
     */
    private fun updatePriceAndTotal() {
        val pricePerKg = getSelectedPricePerKg()
        binding.tvPricePerKg.text = "${rupiahFormat.format(pricePerKg)} / kg"

        val weightStr = binding.etWeight.text?.toString()?.trim() ?: ""
        if (weightStr.isEmpty()) {
            binding.tvEstimatedTotal.text = rupiahFormat.format(0)
            return
        }

        // Defensive parsing menggunakan try-catch
        val weight = try {
            weightStr.toDouble()
        } catch (e: NumberFormatException) {
            Timber.w(e, "Format berat tidak valid saat kalkulasi realtime: %s", weightStr)
            0.0
        }

        val extraFee = if (binding.cbSpecialBedcover.isChecked) 500000.0 else 0.0
        val estimated = calculateLaundryCost(weight, pricePerKg, extraFee)
        binding.tvEstimatedTotal.text = rupiahFormat.format(estimated)
    }

    private fun getSelectedPricePerKg(): Double {
        return when (binding.rgService.checkedRadioButtonId) {
            R.id.rbWashOnly -> 6000.0
            R.id.rbWashIron -> 8000.0
            R.id.rbExpress -> 12000.0
            else -> 6000.0
        }
    }

    private fun getSelectedServiceName(): String {
        return when (binding.rgService.checkedRadioButtonId) {
            R.id.rbWashOnly -> "Cuci Kering"
            R.id.rbWashIron -> "Cuci + Setrika"
            R.id.rbExpress -> "Express"
            else -> "Cuci Kering"
        }
    }

    private fun getSelectedPaymentMethod(): String {
        return when (binding.rgPayment.checkedRadioButtonId) {
            R.id.rbQris -> "QRIS"
            R.id.rbEwallet -> "E-Wallet"
            R.id.rbOutlet -> "Bayar di Outlet"
            else -> "QRIS"
        }
    }

    private fun getSelectedSimulationMode(): SimulationMode {
        return when (binding.rgSimulation.checkedRadioButtonId) {
            R.id.rbNormal -> SimulationMode.NORMAL
            R.id.rbServiceUnavailable -> SimulationMode.SERVICE_UNAVAILABLE
            R.id.rbPaymentTimeout -> SimulationMode.PAYMENT_TIMEOUT
            R.id.rbUnknownError -> SimulationMode.UNKNOWN_ERROR
            else -> SimulationMode.NORMAL
        }
    }

    /**
     * Memvalidasi input pengguna secara defensif sebelum pesanan dieksekusi.
     */
    private fun validateInputDefensively(): Result<Double> {
        val rawWeight = binding.etWeight.text?.toString()?.trim()

        if (rawWeight.isNullOrBlank()) {
            return Result.failure(IllegalArgumentException("Berat laundry wajib diisi."))
        }

        val weight = rawWeight.toDoubleOrNull()
            ?: return Result.failure(IllegalArgumentException("Masukkan berat dalam angka."))

        if (weight <= 0.0) {
            return Result.failure(IllegalArgumentException("Berat harus lebih dari 0 kg."))
        }

        if (weight > 20.0) {
            return Result.failure(LaundryWeightLimitException("Berat laundry maksimal 20 kg per pesanan."))
        }

        val service = getSelectedServiceName()
        if (service == "Express" && weight > 10.0) {
            return Result.failure(ExpressWeightLimitException("Layanan Express maksimal 10 kg."))
        }

        return Result.success(weight)
    }

    /**
     * Memproses order laundry dengan Coroutine, runCatching, onSuccess, dan onFailure.
     */
    private fun processCreateOrder() {
        binding.tilWeight.error = null

        // 1. Validasi Input Defensif
        val weightValidation = validateInputDefensively()
        if (weightValidation.isFailure) {
            val exception = weightValidation.exceptionOrNull() ?: IllegalArgumentException("Input tidak valid")
            Timber.w("Validasi input form gagal: %s", exception.message)

            binding.tilWeight.error = exception.message
            val errorUiState = mapExceptionToUiState(exception)
            renderUiState(errorUiState)
            return
        }

        val weight = weightValidation.getOrDefault(1.0)
        val pricePerKg = getSelectedPricePerKg()
        val extraFee = if (binding.cbSpecialBedcover.isChecked) 500000.0 else 0.0
        val total = calculateLaundryCost(weight, pricePerKg, extraFee)

        val order = LaundryOrder(
            orderId = String.format(Locale.US, "CL-2026-%03d", orderSequence),
            serviceType = getSelectedServiceName(),
            weightKg = weight,
            pricePerKg = pricePerKg,
            totalPrice = total,
            paymentMethod = getSelectedPaymentMethod(),
            status = "DRAFT"
        )
        currentOrder = order

        val simulationMode = getSelectedSimulationMode()

        // Periksa otorisasi pesanan bernilai tinggi sebelum asynchronous submit
        if (order.totalPrice > 500000.0 && !isSupervisorApproved) {
            val highValEx = HighValueLaundryOrderException("Nilai pesanan cukup tinggi (> Rp500.000). Masukkan kode konfirmasi petugas.")
            Timber.w("High value order terdeteksi: %s (Total: Rp%.0f)", order.orderId, order.totalPrice)
            val errorUi = mapExceptionToUiState(highValEx)
            renderUiState(errorUi)
            return
        }

        // Tampilkan status Loading
        renderUiState(UiState.Loading)

        // Asynchronous launch via lifecycleScope dan coroutineExceptionHandler
        lifecycleScope.launch(coroutineExceptionHandler) {
            Timber.d("Menjalankan coroutine pemesanan laundry secara asynchronous")

            // BREAKPOINT: Sebelum createOrder()
            val orderResult: Result<LaundryOrder> = runCatching {
                laundryService.createOrder(
                    order = order,
                    simulationMode = simulationMode,
                    isSupervisorApproved = isSupervisorApproved
                ).getOrThrow()
            }

            // Implementasi bersih onSuccess & onFailure sesuai modul
            orderResult
                .onSuccess { completedOrder ->
                    Timber.i("Laundry order success: %s", completedOrder.orderId)
                    retryAttempt = 0
                    currentOrder = completedOrder
                    orderSequence++
                    renderUiState(UiState.Success(completedOrder))
                }
                .onFailure { error ->
                    // BREAKPOINT: Saat error ditangkap
                    Timber.e(error, "Laundry order failed: %s", error.message)
                    val errorState = mapExceptionToUiState(error)
                    renderUiState(errorState)

                    // Jika retry sudah mencapai 3x dan error bisa di-retry, tampilkan modal threshold
                    if (errorState.canRetry && retryAttempt >= maxRetryThreshold) {
                        showMaxRetryExceededDialog()
                    }
                }
        }
    }

    /**
     * Memetakan berbagai exception menjadi representasi UiState.Error yang aman untuk pengguna.
     * Tidak membocorkan stack trace mentah kepada UI.
     */
    private fun mapExceptionToUiState(throwable: Throwable): UiState.Error {
        return when (throwable) {
            is LaundryServiceUnavailableException -> {
                UiState.Error(
                    message = "Outlet laundry sedang tidak dapat menerima pesanan.",
                    canRetry = true,
                    errorCode = "ERR_SERVICE_UNAVAILABLE"
                )
            }
            is PaymentTimeoutException -> {
                UiState.Error(
                    message = "Pembayaran mengalami timeout.",
                    canRetry = true,
                    errorCode = "ERR_PAYMENT_TIMEOUT"
                )
            }
            is LaundryWeightLimitException -> {
                UiState.Error(
                    message = throwable.message ?: "Berat laundry melebihi batas maksimal.",
                    canRetry = false,
                    errorCode = "ERR_WEIGHT_LIMIT"
                )
            }
            is ExpressWeightLimitException -> {
                UiState.Error(
                    message = throwable.message ?: "Layanan Express maksimal 10 kg.",
                    canRetry = false,
                    errorCode = "ERR_EXPRESS_LIMIT"
                )
            }
            is HighValueLaundryOrderException -> {
                showSupervisorDialog()
                UiState.Error(
                    message = "Nilai pesanan cukup tinggi. Masukkan kode konfirmasi petugas.",
                    canRetry = false,
                    errorCode = "ERR_HIGH_VALUE_CONFIRMATION"
                )
            }
            is IllegalArgumentException -> {
                UiState.Error(
                    message = throwable.message ?: "Data input tidak valid.",
                    canRetry = false,
                    errorCode = "ERR_INVALID_ARGUMENT"
                )
            }
            else -> {
                UiState.Error(
                    message = "Terjadi kesalahan yang tidak terduga.",
                    canRetry = false,
                    errorCode = "ERR_UNKNOWN"
                )
            }
        }
    }

    /**
     * Merender status UI secara terpusat berdasarkan sealed class UiState.
     */
    private fun renderUiState(state: UiState<LaundryOrder>) {
        when (state) {
            is UiState.Idle -> {
                binding.progressOrder.visibility = View.GONE
                binding.btnCreateOrder.isEnabled = true
                binding.cardOrderResult.visibility = View.GONE
            }
            is UiState.Loading -> {
                binding.progressOrder.visibility = View.VISIBLE
                binding.btnCreateOrder.isEnabled = false
                binding.cardOrderResult.visibility = View.VISIBLE

                // Styling Loading
                binding.cardOrderResult.strokeColor = ContextCompat.getColor(this, R.color.info_blue)
                binding.cardOrderResult.setCardBackgroundColor(ContextCompat.getColor(this, R.color.info_blue_bg))
                binding.tvOrderStatus.text = "Memproses pesanan..."
                binding.tvOrderStatus.setTextColor(ContextCompat.getColor(this, R.color.info_blue))
                binding.tvOrderMessage.text = "Mohon tunggu, pesanan sedang diverifikasi oleh sistem..."

                binding.layoutOrderDetails.visibility = View.GONE
                binding.btnRetry.visibility = View.GONE
                binding.tvRetryCounter.visibility = View.GONE
                binding.btnConfirmOutletPayment.visibility = View.GONE
                binding.btnNewOrder.visibility = View.GONE
            }
            is UiState.Success -> {
                binding.progressOrder.visibility = View.GONE
                binding.btnCreateOrder.isEnabled = true
                binding.cardOrderResult.visibility = View.VISIBLE

                // Styling Success
                binding.cardOrderResult.strokeColor = ContextCompat.getColor(this, R.color.success_green)
                binding.cardOrderResult.setCardBackgroundColor(ContextCompat.getColor(this, R.color.success_green_bg))
                binding.tvOrderStatus.text = "Pesanan Berhasil"
                binding.tvOrderStatus.setTextColor(ContextCompat.getColor(this, R.color.success_green))
                binding.tvOrderMessage.text = "Pesanan laundry berhasil dibuat dan siap diproses."

                // Tampilkan Detail
                binding.layoutOrderDetails.visibility = View.VISIBLE
                binding.tvOrderId.text = state.data.orderId
                binding.tvFinalTotal.text = rupiahFormat.format(state.data.totalPrice)

                binding.btnRetry.visibility = View.GONE
                binding.tvRetryCounter.visibility = View.GONE
                binding.btnConfirmOutletPayment.visibility = View.GONE
                binding.btnNewOrder.visibility = View.VISIBLE
            }
            is UiState.Error -> {
                binding.progressOrder.visibility = View.GONE
                binding.btnCreateOrder.isEnabled = true
                binding.cardOrderResult.visibility = View.VISIBLE

                // Styling Error
                binding.cardOrderResult.strokeColor = ContextCompat.getColor(this, R.color.error_red)
                binding.cardOrderResult.setCardBackgroundColor(ContextCompat.getColor(this, R.color.error_red_bg))
                binding.tvOrderStatus.text = "Pesanan Gagal"
                binding.tvOrderStatus.setTextColor(ContextCompat.getColor(this, R.color.error_red))
                binding.tvOrderMessage.text = state.message

                binding.layoutOrderDetails.visibility = View.GONE
                binding.btnConfirmOutletPayment.visibility = View.GONE
                binding.btnNewOrder.visibility = View.GONE

                if (state.canRetry && retryAttempt < maxRetryThreshold) {
                    binding.btnRetry.visibility = View.VISIBLE
                    binding.tvRetryCounter.visibility = View.VISIBLE
                    val displayAttempt = if (retryAttempt == 0) 1 else retryAttempt
                    binding.tvRetryCounter.text = "Percobaan $displayAttempt dari $maxRetryThreshold"
                } else {
                    binding.btnRetry.visibility = View.GONE
                    binding.tvRetryCounter.visibility = View.GONE
                }
            }
        }
    }

    /**
     * Dialog saat retry mencapai batas maksimal 3x.
     * Memberikan opsi Fallback "Bayar di Outlet" atau "Tutup".
     */
    private fun showMaxRetryExceededDialog() {
        Timber.w("Batas retry tercapai (%d kali gagal). Membuka dialog fallback.", maxRetryThreshold)

        AlertDialog.Builder(this)
            .setTitle("Proses Online Bermasalah")
            .setMessage("Proses online gagal dilakukan setelah 3 percobaan.")
            .setCancelable(false)
            .setPositiveButton("Bayar di Outlet") { dialog, _ ->
                dialog.dismiss()
                Timber.i("Fallback mode dipilih: Bayar di Outlet")
                activateFallbackOutletMode()
            }
            .setNegativeButton("Tutup") { dialog, _ ->
                dialog.dismiss()
                binding.btnRetry.visibility = View.GONE
                binding.tvRetryCounter.visibility = View.GONE
            }
            .show()
    }

    /**
     * Mengaktifkan fallback mode: Ubah status menjadi WAITING_PAYMENT.
     * Tidak menampilkan PAID karena pembayaran belum dilakukan.
     */
    private fun activateFallbackOutletMode() {
        val order = currentOrder ?: return
        order.status = "WAITING_PAYMENT"

        binding.progressOrder.visibility = View.GONE
        binding.btnCreateOrder.isEnabled = true
        binding.cardOrderResult.visibility = View.VISIBLE

        binding.cardOrderResult.strokeColor = ContextCompat.getColor(this, R.color.warning_amber)
        binding.cardOrderResult.setCardBackgroundColor(ContextCompat.getColor(this, R.color.warning_amber_bg))
        binding.tvOrderStatus.text = "Menunggu Pembayaran di Outlet"
        binding.tvOrderStatus.setTextColor(ContextCompat.getColor(this, R.color.warning_amber))
        binding.tvOrderMessage.text = "Pesanan berhasil dibuat. Silakan lakukan pembayaran di outlet laundry."

        binding.layoutOrderDetails.visibility = View.VISIBLE
        binding.tvOrderId.text = order.orderId
        binding.tvFinalTotal.text = rupiahFormat.format(order.totalPrice)

        binding.btnRetry.visibility = View.GONE
        binding.tvRetryCounter.visibility = View.GONE
        binding.btnConfirmOutletPayment.visibility = View.VISIBLE
        binding.btnNewOrder.visibility = View.GONE

        Toast.makeText(this, "Silakan selesaikan pembayaran di outlet", Toast.LENGTH_SHORT).show()
    }

    /**
     * Pemulihan status saat user menyelesaikan pembayaran di outlet.
     */
    private fun confirmOutletPaymentRecovery() {
        val order = currentOrder ?: return
        order.status = "SUCCESS"
        Timber.i("Pembayaran di outlet dikonfirmasi untuk order: %s", order.orderId)

        binding.cardOrderResult.strokeColor = ContextCompat.getColor(this, R.color.success_green)
        binding.cardOrderResult.setCardBackgroundColor(ContextCompat.getColor(this, R.color.success_green_bg))
        binding.tvOrderStatus.text = "Pembayaran Telah Dikonfirmasi"
        binding.tvOrderStatus.setTextColor(ContextCompat.getColor(this, R.color.success_green))
        binding.tvOrderMessage.text = "Pembayaran telah dikonfirmasi di outlet. Pakaian Anda sedang diproses!"

        binding.btnConfirmOutletPayment.visibility = View.GONE
        binding.btnNewOrder.visibility = View.VISIBLE

        Toast.makeText(this, "Pembayaran berhasil dikonfirmasi!", Toast.LENGTH_SHORT).show()
    }

    /**
     * Dialog otorisasi pesanan bernilai tinggi (> Rp500.000).
     * Meminta supervisor code demo: LAUNDRY2026.
     * Tidak mencatat kode supervisor ke Timber.
     */
    private fun showSupervisorDialog() {
        val dialog = Dialog(this)
        val dialogBinding = DialogSupervisorBinding.inflate(layoutInflater)
        dialog.setContentView(dialogBinding.root)
        dialog.setCancelable(false)
        dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)

        dialogBinding.btnSupervisorCancel.setOnClickListener {
            dialog.dismiss()
            Timber.i("Otorisasi supervisor dibatalkan oleh pengguna")
        }

        dialogBinding.btnSupervisorConfirm.setOnClickListener {
            val inputCode = dialogBinding.etSupervisorCode.text?.toString()?.trim() ?: ""

            // JANGAN log kode supervisor ke Timber demi keamanan data!
            if (inputCode == "LAUNDRY2026") {
                Timber.i("Otorisasi supervisor berhasil diverifikasi")
                isSupervisorApproved = true
                dialog.dismiss()
                Toast.makeText(this, "Otorisasi supervisor berhasil diterima", Toast.LENGTH_SHORT).show()
                // Lanjutkan proses pesanan
                processCreateOrder()
            } else {
                Timber.w("Percobaan otorisasi supervisor gagal")
                dialogBinding.tvSupervisorError.visibility = View.VISIBLE
                dialogBinding.tvSupervisorError.text = "Kode konfirmasi salah."
            }
        }

        dialog.show()
    }

    /**
     * Reset form input kembali ke keadaan awal.
     */
    private fun resetForm() {
        Timber.d("Form pemesanan direset ke keadaan awal")
        binding.etWeight.text?.clear()
        binding.tilWeight.error = null
        binding.rbWashOnly.isChecked = true
        binding.rbQris.isChecked = true
        binding.rbNormal.isChecked = true
        binding.cbSpecialBedcover.isChecked = false
        retryAttempt = 0
        isSupervisorApproved = false
        currentOrder = null
        renderUiState(UiState.Idle)
        updatePriceAndTotal()
    }

    private fun hideKeyboard() {
        val view = currentFocus ?: binding.root
        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
        imm?.hideSoftInputFromWindow(view.windowToken, 0)
    }
}
