package com.geidea.passwordgenrated

import android.annotation.SuppressLint
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private val ioExecutor = Executors.newSingleThreadExecutor()

    @SuppressLint("MissingInflatedId")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnShowPassword).setOnClickListener {
            showOptionsDialog()
        }
    }

    override fun onDestroy() {
        ioExecutor.shutdown()
        super.onDestroy()
    }

    private fun copyToClipboard(text: String, toast: String = getString(R.string.password_copied)) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText("kiosk_password", text))
        Toast.makeText(this, toast, Toast.LENGTH_SHORT).show()
    }

    private fun showOptionsDialog() {
        val options = arrayOf(
            "Show next 7 days",
            "Show current hour password",
            "Enter date/time and show password"
        )

        AlertDialog.Builder(this)
            .setTitle("Kiosk Password Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> showSevenDaysDialog()
                    1 -> showTodayDialog()
                    2 -> showCustomDateTimeDialog()
                }
            }
            .setCancelable(false)
            .setNegativeButton("Close", null)
            .show()
    }

    private fun showSevenDaysDialog() {
        ioExecutor.execute {
            val serial = KioskPasswordManager.getDeviceSerial(this@MainActivity)
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                val serialForDisplay = serial.takeLast(6).uppercase(Locale.US)
                val calendar = Calendar.getInstance()
                val lines = StringBuilder()

                for (dayOffset in 0 until 7) {
                    val c = calendar.clone() as Calendar
                    c.add(Calendar.DAY_OF_YEAR, dayOffset)
                    c.set(Calendar.HOUR_OF_DAY, 0)
                    c.set(Calendar.MINUTE, 0)
                    c.set(Calendar.SECOND, 0)
                    c.set(Calendar.MILLISECOND, 0)

                    val dateLabel = String.format(
                        Locale.US,
                        "%04d-%02d-%02d",
                        c.get(Calendar.YEAR),
                        c.get(Calendar.MONTH) + 1,
                        c.get(Calendar.DAY_OF_MONTH)
                    )
                    val password = KioskPasswordManager.generateHourly(
                        serial = serial,
                        hourOffset = 0,
                        nowMillis = c.timeInMillis
                    )
                    lines.append(dateLabel).append(" (index 0 @ 00:00) : ").append(password)
                    if (dayOffset < 6) lines.append('\n')
                }

                val fullMessage = "Serial: $serialForDisplay\n\n$lines"
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Passwords — Next 7 Days")
                    .setMessage(fullMessage)
                    .setNeutralButton(R.string.copy) { _, _ ->
                        copyToClipboard(fullMessage)
                    }
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }
    }

    private fun showTodayDialog() {
        ioExecutor.execute {
            val serial = KioskPasswordManager.getDeviceSerial(this@MainActivity)
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                val serialForDisplay = serial.takeLast(6).uppercase(Locale.US)
                val now = Calendar.getInstance()
                val hourStart = now.clone() as Calendar
                hourStart.set(Calendar.MINUTE, 0)
                hourStart.set(Calendar.SECOND, 0)
                hourStart.set(Calendar.MILLISECOND, 0)
                val hourLabel =
                    SimpleDateFormat("yyyy-MM-dd hh:00 a", Locale.getDefault()).format(hourStart.timeInMillis)
                val password = KioskPasswordManager.generateHourly(
                    serial = serial,
                    hourOffset = 0,
                    nowMillis = System.currentTimeMillis()
                )
                val hourIndex = hourStart.get(Calendar.HOUR_OF_DAY) % 12
                val message =
                    "Serial: $serialForDisplay\n\n" +
                        "Local hour: $hourLabel\n" +
                        "Hour index (0–11): $hourIndex\n\n" +
                        "Password: $password"
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Current Hour Password")
                    .setMessage(message)
                    .setNeutralButton(R.string.copy) { _, _ ->
                        copyToClipboard(password)
                    }
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        }
    }

    private fun showCustomDateTimeDialog() {
        ioExecutor.execute {
            val serial = KioskPasswordManager.getDeviceSerial(this@MainActivity)
            val serialForDisplay = serial.takeLast(6).uppercase(Locale.US)
            runOnUiThread {
                if (isFinishing) return@runOnUiThread
                val now = Calendar.getInstance()

                DatePickerDialog(
                    this@MainActivity,
                    { _, year, month, dayOfMonth ->
                        TimePickerDialog(
                            this@MainActivity,
                            { _, hourOfDay, minute ->
                                val selected = Calendar.getInstance().apply {
                                    set(Calendar.YEAR, year)
                                    set(Calendar.MONTH, month)
                                    set(Calendar.DAY_OF_MONTH, dayOfMonth)
                                    set(Calendar.HOUR_OF_DAY, hourOfDay)
                                    set(Calendar.MINUTE, minute)
                                    set(Calendar.SECOND, 0)
                                    set(Calendar.MILLISECOND, 0)
                                }

                                val hourIndex = selected.get(Calendar.HOUR_OF_DAY) % 12
                                val password = KioskPasswordManager.generateHourly(
                                    serial = serial,
                                    hourOffset = 0,
                                    nowMillis = selected.timeInMillis
                                )
                                val selectedLabel = SimpleDateFormat(
                                    "yyyy-MM-dd hh:mm a",
                                    Locale.getDefault()
                                ).format(selected.timeInMillis)
                                val resultMessage =
                                    "Serial: $serialForDisplay\n" +
                                        "Date: $selectedLabel\n" +
                                        "Hour index (0–11): $hourIndex\n\n" +
                                        password
                                AlertDialog.Builder(this@MainActivity)
                                    .setTitle("Password At Selected Time")
                                    .setMessage(resultMessage)
                                    .setNeutralButton(R.string.copy) { _, _ ->
                                        copyToClipboard(password)
                                    }
                                    .setPositiveButton(android.R.string.ok, null)
                                    .show()
                            },
                            now.get(Calendar.HOUR_OF_DAY),
                            now.get(Calendar.MINUTE),
                            false
                        ).show()
                    },
                    now.get(Calendar.YEAR),
                    now.get(Calendar.MONTH),
                    now.get(Calendar.DAY_OF_MONTH)
                ).show()
            }
        }
    }
}