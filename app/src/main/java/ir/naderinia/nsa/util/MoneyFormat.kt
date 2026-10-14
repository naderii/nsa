package ir.naderinia.nsa.util

import java.text.NumberFormat
import java.util.Locale

/** 12500000 -> "12,500,000" — makes amounts readable at a glance. */
fun Long.grouped(): String = NumberFormat.getInstance(Locale.US).format(this)
