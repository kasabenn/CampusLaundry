package com.campuslaundry.app

import android.app.Application
import timber.log.Timber

/**
 * Inisialisasi tingkat aplikasi.
 * Sesuai ketentuan praktikum:
 * Timber hanya di-plant pada mode DEBUG agar tidak membocorkan log di produksi.
 * Jangan pernah mencatat data sensitif pengguna (PIN, password, dll).
 */
class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            Timber.d("CampusLaundry Application initialized with Timber DebugTree.")
        }
    }
}
