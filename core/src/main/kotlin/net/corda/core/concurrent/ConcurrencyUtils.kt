package net.corda.core.concurrent

import com.google.common.annotations.VisibleForTesting
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.time.Duration
import java.util.concurrent.ExecutionException
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

/**
 * As soon as a given future becomes done, the handler is invoked with that future as its argument.
 * The result of the handler is copied into the result future, and the handler isn't invoked again.
 * If a given future errors after the result future is done, the error is automatically logged.
 */
fun <V, W> firstOf(vararg futures: CordaFuture<V>, handler: (CordaFuture<V>) -> W) = firstOf(futures, defaultLog, handler)

private val defaultLog = LoggerFactory.getLogger("net.corda.core.concurrent")
@VisibleForTesting
internal val shortCircuitedTaskFailedMessage = "Short-circuited task failed:"

internal fun <V, W> firstOf(futures: Array<out CordaFuture<V>>, log: Logger, handler: (CordaFuture<V>) -> W): CordaFuture<W> {
    val resultFuture = openFuture<W>()
    val winnerChosen = AtomicBoolean()
    futures.forEach {
        it.then {
            if (winnerChosen.compareAndSet(false, true)) {
                resultFuture.catch { handler(it) }
            } else if (it.isCancelled) {
                // Do nothing.
            } else {
                it.match({}, { log.error(shortCircuitedTaskFailedMessage, it) })
            }
        }
    }
    return resultFuture
}

fun <V> Future<V>.get(timeout: Duration? = null): V = if (timeout == null) get() else get(timeout.toNanos(), TimeUnit.NANOSECONDS)

/** Same as [Future.get] but with a more descriptive name, and doesn't throw [ExecutionException], instead throwing its cause */
fun <V> Future<V>.getOrThrow(timeout: Duration? = null): V = try {
    get(timeout)
} catch (e: ExecutionException) {
    throw e.cause!!
}

fun <V, W> Future<V>.match(success: (V) -> W, failure: (Throwable) -> W): W {
    return success(try {
        getOrThrow()
    } catch (t: Throwable) {
        return failure(t)
    })
}
