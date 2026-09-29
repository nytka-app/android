package io.github.nytka_app.ui.developer

/** Events per second, measured over windows of at least [windowMs]. A counter that went backwards starts over. */
class RateMeter(
    private val windowMs: Long = 1_000,
) {
    private var baseCount = 0L
    private var baseAtMs: Long? = null
    private var rate = 0.0

    fun sample(
        count: Long,
        atMs: Long,
    ): Double {
        val start = baseAtMs
        if (start == null || count < baseCount) {
            baseCount = count
            baseAtMs = atMs
            rate = 0.0
        } else if (atMs - start >= windowMs) {
            rate = (count - baseCount) * 1_000.0 / (atMs - start)
            baseCount = count
            baseAtMs = atMs
        }
        return rate
    }
}
