package com.campuslaundry.app

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.campuslaundry.app.databinding.ActivityMainBinding
import timber.log.Timber

/**
 * Halaman utama CampusLaundry.
 * Menampilkan informasi pengenalan aplikasi mahasiswa dan pintu masuk pemesanan.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Timber.d("MainActivity onCreate: CampusLaundry beranda dibuka")

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
    }

    private fun setupListeners() {
        binding.btnStartOrder.setOnClickListener {
            Timber.i("Pengguna menekan tombol Mulai Pesanan")
            val intent = Intent(this, OrderLaundryActivity::class.java)
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        Timber.d("MainActivity onResume")
    }
}
