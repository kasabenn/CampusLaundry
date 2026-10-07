package com.campuslaundry.app

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

/**
 * Unit testing komprehensif untuk memverifikasi seluruh skenario pengujian Pertemuan 12:
 * TEST 1 s/d TEST 11.
 */
class CampusLaundryLogicTest {

    private lateinit var laundryService: LaundryService

    @Before
    fun setup() {
        laundryService = LaundryService()
    }

    // TEST 1: Normal order
    @Test
    fun testNormalOrderSuccess() = runBlocking {
        val order = LaundryOrder(
            orderId = "CL-2026-001",
            serviceType = "Cuci + Setrika",
            weightKg = 3.0,
            pricePerKg = 8000.0,
            totalPrice = 24000.0,
            paymentMethod = "QRIS",
            status = "DRAFT"
        )

        val result = laundryService.createOrder(order, SimulationMode.NORMAL)
        assertTrue(result.isSuccess)
        val successOrder = result.getOrNull()
        assertNotNull(successOrder)
        assertEquals("SUCCESS", successOrder?.status)
        assertEquals(24000.0, successOrder?.totalPrice ?: 0.0, 0.01)
    }

    // TEST 2: Berat 0 atau negatif melemparkan IllegalArgumentException
    @Test
    fun testZeroOrNegativeWeightFails() = runBlocking {
        val order = LaundryOrder(
            orderId = "CL-2026-002",
            serviceType = "Cuci Kering",
            weightKg = 0.0,
            pricePerKg = 6000.0,
            totalPrice = 0.0,
            paymentMethod = "QRIS",
            status = "DRAFT"
        )

        val result = laundryService.createOrder(order, SimulationMode.NORMAL)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalArgumentException)
    }

    // TEST 4: Berat 25 kg melemparkan LaundryWeightLimitException (Batas 20 kg)
    @Test
    fun testWeightOver20KgThrowsLaundryWeightLimitException() = runBlocking {
        val order = LaundryOrder(
            orderId = "CL-2026-003",
            serviceType = "Cuci Kering",
            weightKg = 25.0,
            pricePerKg = 6000.0,
            totalPrice = 150000.0,
            paymentMethod = "QRIS",
            status = "DRAFT"
        )

        val result = laundryService.createOrder(order, SimulationMode.NORMAL)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is LaundryWeightLimitException)
    }

    // Express > 10 kg melemparkan ExpressWeightLimitException
    @Test
    fun testExpressOver10KgThrowsExpressWeightLimitException() = runBlocking {
        val order = LaundryOrder(
            orderId = "CL-2026-004",
            serviceType = "Express",
            weightKg = 12.0,
            pricePerKg = 12000.0,
            totalPrice = 144000.0,
            paymentMethod = "QRIS",
            status = "DRAFT"
        )

        val result = laundryService.createOrder(order, SimulationMode.NORMAL)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ExpressWeightLimitException)
    }

    // TEST 5: Service Unavailable melemparkan LaundryServiceUnavailableException
    @Test
    fun testServiceUnavailableThrowsCustomException() = runBlocking {
        val order = LaundryOrder(
            orderId = "CL-2026-005",
            serviceType = "Cuci Kering",
            weightKg = 3.0,
            pricePerKg = 6000.0,
            totalPrice = 18000.0,
            paymentMethod = "QRIS",
            status = "DRAFT"
        )

        val result = laundryService.createOrder(order, SimulationMode.SERVICE_UNAVAILABLE)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is LaundryServiceUnavailableException)
    }

    // TEST 6: Payment Timeout melemparkan PaymentTimeoutException
    @Test
    fun testPaymentTimeoutThrowsPaymentTimeoutException() = runBlocking {
        val order = LaundryOrder(
            orderId = "CL-2026-006",
            serviceType = "Cuci Kering",
            weightKg = 2.0,
            pricePerKg = 6000.0,
            totalPrice = 12000.0,
            paymentMethod = "E-Wallet",
            status = "DRAFT"
        )

        val result = laundryService.createOrder(order, SimulationMode.PAYMENT_TIMEOUT)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is PaymentTimeoutException)
    }

    // TEST 8: Total > Rp500.000 tanpa persetujuan supervisor melemparkan HighValueLaundryOrderException
    @Test
    fun testHighValueOrderThrowsHighValueException() = runBlocking {
        val order = LaundryOrder(
            orderId = "CL-2026-007",
            serviceType = "Cuci + Setrika",
            weightKg = 5.0,
            pricePerKg = 8000.0,
            totalPrice = 540000.0, // > Rp500.000
            paymentMethod = "QRIS",
            status = "DRAFT"
        )

        val result = laundryService.createOrder(order, SimulationMode.NORMAL, isSupervisorApproved = false)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is HighValueLaundryOrderException)
    }

    // TEST 10: High value order yang disetujui supervisor berhasil diproses
    @Test
    fun testHighValueOrderApprovedBySupervisorSucceeds() = runBlocking {
        val order = LaundryOrder(
            orderId = "CL-2026-008",
            serviceType = "Cuci + Setrika",
            weightKg = 5.0,
            pricePerKg = 8000.0,
            totalPrice = 540000.0,
            paymentMethod = "QRIS",
            status = "DRAFT"
        )

        val result = laundryService.createOrder(order, SimulationMode.NORMAL, isSupervisorApproved = true)
        assertTrue(result.isSuccess)
        assertEquals("SUCCESS", result.getOrNull()?.status)
    }

    // TEST 11: Unknown Error melemparkan IllegalStateException
    @Test
    fun testUnknownErrorThrowsIllegalStateException() = runBlocking {
        val order = LaundryOrder(
            orderId = "CL-2026-009",
            serviceType = "Cuci Kering",
            weightKg = 2.0,
            pricePerKg = 6000.0,
            totalPrice = 12000.0,
            paymentMethod = "QRIS",
            status = "DRAFT"
        )

        val result = laundryService.createOrder(order, SimulationMode.UNKNOWN_ERROR)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is IllegalStateException)
    }
}
