package io.github.nytka_app

import io.github.nytka_app.core.diagnostics.EventLog

/** What the code under test logged, in order, as priority, tag and message. */
class FakeEventLog : EventLog {
    val lines = mutableListOf<Triple<Int, String, String>>()

    /** Just the text of each line. */
    val messages: List<String> get() = lines.map { it.third }

    override fun log(
        priority: Int,
        tag: String,
        message: String,
    ) {
        lines += Triple(priority, tag, message)
    }
}
