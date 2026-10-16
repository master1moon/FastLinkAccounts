package com.fastlink.accounts.util

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object FormatUtils {
    private val decimalSymbols = DecimalFormatSymbols(Locale.US)
    private val amountFormatter = DecimalFormat("#,##0.00", decimalSymbols)

    fun formatAmount(value: Double, currency: String = "ريال"): String {
        return "${amountFormatter.format(value)} $currency"
    }

    fun formatNumber(value: Double): String {
        return amountFormatter.format(value)
    }

    fun formatDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy/MM/dd", Locale.US)
        return sdf.format(Date(timestamp))
    }

    fun formatShortDate(timestamp: Long): String {
        val sdf = SimpleDateFormat("MM/dd", Locale.US)
        return sdf.format(Date(timestamp))
    }

    fun formatDateTime(timestamp: Long): String {
        val sdf = SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US)
        return sdf.format(Date(timestamp))
    }

    fun formatFileTimestamp(timestamp: Long = System.currentTimeMillis()): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd_HH-mm", Locale.US)
        return sdf.format(Date(timestamp))
    }

    fun normalizePhoneForWhatsApp(phone: String, defaultCountryCode: String = "967"): String {
        var digits = phone.replace(Regex("[^0-9+]"), "")
        if (digits.startsWith("+")) {
            digits = digits.substring(1)
        } else if (digits.startsWith("00")) {
            digits = digits.substring(2)
        } else if (digits.startsWith("0")) {
            digits = defaultCountryCode + digits.substring(1)
        } else if (digits.length <= 9 && !digits.startsWith(defaultCountryCode)) {
            digits = defaultCountryCode + digits
        }
        return digits
    }
}
