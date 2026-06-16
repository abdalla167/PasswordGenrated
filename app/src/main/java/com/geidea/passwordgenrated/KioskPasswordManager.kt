package com.geidea.passwordgenrated

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.util.Log
import com.sunmi.pay.hardware.aidl.AidlConstants
import java.nio.charset.StandardCharsets
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import sunmi.paylib.SunmiPayKernel

object KioskPasswordManager {

    private const val SECRET = "SunmiSecureKey123"
    private const val PASSWORD_LENGTH = 6
    private const val HMAC_ALGORITHM = "HmacSHA256"
    private const val UNKNOWN_SERIAL = "UNKNOWN"
    private const val PAY_SDK_READY_TIMEOUT_MS = 3_000L
    private const val TAG = "KioskPasswordManager"

    private fun logD(message: String) {
        try {
            Log.d(TAG, message)
        } catch (_: Throwable) {
        }
    }

    private fun logW(message: String, throwable: Throwable? = null) {
        try {
            if (throwable != null) Log.w(TAG, message, throwable) else Log.w(TAG, message)
        } catch (_: Throwable) {
        }
    }

    fun generateHourly(
        serial: String,
        hourOffset: Int = 0,
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        val cal = Calendar.getInstance()
        cal.timeInMillis = nowMillis
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        cal.add(Calendar.HOUR_OF_DAY, hourOffset)

        val dayStartMillis = Calendar.getInstance().apply {
            timeInMillis = cal.timeInMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val hourIndex = cal.get(Calendar.HOUR_OF_DAY) % 12
        val data = "$serial|$dayStartMillis|$hourIndex"
        val hash = hmacSha256(data, SECRET)
        val digitsOnly = hashToLowerHex(hash).filter { it.isDigit() }
        val password = digitsOnly.take(PASSWORD_LENGTH).padEnd(PASSWORD_LENGTH, '0')
        logD(
            "generateHourly: nowMillis=$nowMillis hourOffset=$hourOffset " +
                "slotLocalYmdH=${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH) + 1}-${cal.get(Calendar.DAY_OF_MONTH)} " +
                "localHod=${cal.get(Calendar.HOUR_OF_DAY)} hourIndex=$hourIndex dayStartMillis=$dayStartMillis " +
                "hmacInput=$data serialTail=${serial.takeLast(6).uppercase(Locale.US)} len=${serial.length} " +
                "password=$password"
        )
        return password
    }



    fun getDeviceSerial(context: Context): String {
        val sunmiSerial = getSunmiSerial(context)
            ?.trim()
            ?.takeUnless { it.isBlank() || it.equals("null", ignoreCase = true) }
        if (sunmiSerial != null) {
            logD("getDeviceSerial: source=sunmi serialTail=${sunmiSerial.takeLast(6).uppercase(Locale.US)}")
            return sunmiSerial
        }

        val hardwareSerial = runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Build.getSerial()
            } else {
                @Suppress("DEPRECATION")
                Build.SERIAL
            }
        }.getOrNull()
            .orEmpty()
            .trim()
            .takeUnless { it.isBlank() || it.equals("unknown", ignoreCase = true) || it.equals("null", ignoreCase = true) }
        if (hardwareSerial != null) {
            logD("getDeviceSerial: source=hardware serialTail=${hardwareSerial.takeLast(6).uppercase(Locale.US)}")
            return hardwareSerial
        }

        val androidId = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ANDROID_ID
        ).orEmpty()
            .trim()
            .takeUnless { it.isBlank() || it.equals("unknown", ignoreCase = true) || it.equals("null", ignoreCase = true) }
        if (androidId != null) {
            logD("getDeviceSerial: source=android_id serialTail=${androidId.takeLast(6).uppercase(Locale.US)}")
            return androidId
        }

        logW("getDeviceSerial: source=unknown using UNKNOWN_SERIAL")
        return UNKNOWN_SERIAL
    }

    private fun hmacSha256(data: String, secret: String): ByteArray {
        val mac = Mac.getInstance(HMAC_ALGORITHM)
        val key = SecretKeySpec(secret.toByteArray(StandardCharsets.UTF_8), HMAC_ALGORITHM)
        mac.init(key)
        return mac.doFinal(data.toByteArray(StandardCharsets.UTF_8))
    }

    private fun hashToLowerHex(bytes: ByteArray): String {
        val out = CharArray(bytes.size * 2)
        var i = 0
        for (b in bytes) {
            val v = b.toInt() and 0xFF
            out[i++] = HEX[(v ushr 4) and 0x0F]
            out[i++] = HEX[v and 0x0F]
        }
        return String(out)
    }

    private fun getSunmiSerial(context: Context): String? {
        return runCatching {
            val kernel = SunmiPayKernel.getInstance()
            val latch = CountDownLatch(1)
            var serial: String? = null

            kernel.initPaySDK(context.applicationContext, object : SunmiPayKernel.ConnectCallback {
                override fun onConnectPaySDK() {
                    serial = runCatching {
                        kernel.mBasicOptV2?.getSysParam(AidlConstants.SysParam.SN)
                    }.getOrNull()
                    latch.countDown()
                }

                override fun onDisconnectPaySDK() {
                    latch.countDown()
                }
            })

            latch.await(PAY_SDK_READY_TIMEOUT_MS, TimeUnit.MILLISECONDS)
            serial
        }.onFailure {
            logW("Unable to read serial from Sunmi Pay SDK", it)
        }.getOrNull()
    }

    private val HEX = "0123456789abcdef".toCharArray()
}
