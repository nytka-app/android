package io.github.nytka_app.ui

/** The four states a list shows instead of rows. Offline is neutral on purpose: it is normal, not a failure. */
enum class ListStateKind { Empty, Loading, Error, Offline }
