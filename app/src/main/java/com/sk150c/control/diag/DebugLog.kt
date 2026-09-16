package com.sk150c.control.diag

import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Locale

object DebugLog {
    private const val MAX_LINES = 400
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines

    fun d(tag: String, msg: String) {
        Log.d(tag, msg)
        push("D", tag, msg)
    }

    fun w(tag: String, msg: String) {
        Log.w(tag, msg)
        push("W", tag, msg)
    }

    fun e(tag: String, msg: String, t: Throwable? = null) {
        Log.e(tag, msg, t)
        push("E", tag, msg + (t?.let { " (${it.message})" } ?: ""))
    }

    private fun push(level: String, tag: String, msg: String) {
        val line = "${timeFormat.format(System.currentTimeMillis())} $level/$tag: $msg"
        _lines.value = (_lines.value + line).takeLast(MAX_LINES)
    }

    fun clear() {
        _lines.value = emptyList()
    }

    fun asText(): String = _lines.value.joinToString("\n")
}
