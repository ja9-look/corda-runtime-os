package net.corda.e2etest.utilities

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.fail
import java.time.Duration

/** "Private args" that are only exposed in here */
class AssertWithRetryArgs {
    var timeout: Duration = Duration.ofSeconds(10)
    var interval: Duration = Duration.ofMillis(500)
    var startDelay: Duration = Duration.ofMillis(10)
    var command: (() -> SimpleResponse)? = null
    var condition: ((SimpleResponse) -> Boolean)? = null
    var immediateFailCondition: ((SimpleResponse) -> Boolean)? = null
    var failMessage: String = ""
}

/** The [assertWithRetry] builder class that helps implement the "DSL" */
class AssertWithRetryBuilder(private val args: AssertWithRetryArgs) {
    fun timeout(timeout: Duration) {
        args.timeout = timeout
    }

    fun interval(duration: Duration) {
        args.interval = duration
    }

    fun command(command: () -> SimpleResponse) {
        args.command = command
    }

    fun condition(condition: (SimpleResponse) -> Boolean) {
        args.condition = condition
    }

    fun immediateFailCondition(condition: (SimpleResponse) -> Boolean) {
        args.immediateFailCondition = condition
    }

    fun failMessage(failMessage: String) {
        args.failMessage = failMessage + "\n"
    }
}

private data class Attempt(val attemptNumber: Int, val timeTried: Duration, val response: SimpleResponse)

private fun printAttempt(attempt: Iterable<Attempt>): String =
    attempt.joinToString("\n") { "${it.attemptNumber} (${it.timeTried}): ${it.response}" }

/**
 * Sort-of-DSL.  Asserts a command and retries if it doesn't initially succeed.
 *
 * Specify the [command] to execute, and the [condition] to assert, and optionally a [failMessage] and a different
 * [timeout]
 *
 *      val statusResponse = assertWithRetry {
 *          condition { it.code == 200 && it.toJson()["status"].textValue() == "OK" }
 *          command { cpiStatus(requestId) }
 *      }
 *
 * @throws [AssertionError] or similar if condition isn't successful.
 * @return [SimpleResponse] if successful
 */
fun assertWithRetry(initialize: AssertWithRetryBuilder.() -> Unit): SimpleResponse {
    val args = AssertWithRetryArgs()
    val attempts = mutableListOf<Attempt>()

    AssertWithRetryBuilder(args).apply(initialize).run {
        var response: SimpleResponse?

        var retry = 0
        var timeTried: Long
        Thread.sleep(args.startDelay.toMillis())
        do {
            response = args.command!!.invoke()
            if (retry == 0) {
                println(response.url)
            }
            unbufferedPrint('.')
            if(null != args.immediateFailCondition && args.immediateFailCondition!!.invoke(response)) {
                fail("Failed without retry with status code = ${response.code} and body =\n${response.body}")
            }
            if (args.condition!!.invoke(response)) break
            retry++
            timeTried = args.interval.toMillis() * retry
            attempts.add(Attempt(retry, Duration.ofMillis(timeTried), response))
            Thread.sleep(args.interval.toMillis())
        } while (timeTried < args.timeout.toMillis())
        println()

        assertThat(args.condition!!.invoke(response!!))
            .withFailMessage(
                "${args.failMessage}Retried ${response.url} and " +
                        "failed with status code = ${response.code} and body =\n${response.body}.\nAttempts:\n${printAttempt(attempts)}"
            )
            .isTrue

        return response
    }
}

/**
 * Same as [assertWithRetry], but exceptions thrown are also ignored during the retry process.
 *
 * This method should be preferred over [assertWithRetry] when the actual [AssertWithRetryArgs.command] might
 * receive ignorable transient exceptions during its execution that have nothing to do with the actual
 * [AssertWithRetryArgs.condition] that needs to be asserted.
 *
 * As an example, it might be used to wrap HTTP calls which could fail due to transient connectivity errors but
 * eventually succeed:
 *
 *      val response = assertWithRetryIgnoringExceptions {
 *          command { httpRequest(body) }
 *          condition { it.code == 200 }
 *      }
 */
@Suppress("ComplexMethod", "NestedBlockDepth")
fun assertWithRetryIgnoringExceptions(initialize: AssertWithRetryBuilder.() -> Unit): SimpleResponse {
    val args = AssertWithRetryArgs()
    val attempts = mutableListOf<Attempt>()

    AssertWithRetryBuilder(args).apply(initialize).run {
        var retry = 0
        var result: Any?
        var timeTried: Long

        Thread.sleep(args.startDelay.toMillis())
        do {
            result = try {
                args.command!!.invoke()
            } catch (exception: Exception) {
                exception
            }

            if (retry == 0 && result is SimpleResponse) {
                println(result.url)
            }
            unbufferedPrint('.')

            retry++
            timeTried = args.interval.toMillis() * retry

            if (result is SimpleResponse) {
                if (null != args.immediateFailCondition && args.immediateFailCondition!!.invoke(result)) {
                    fail("Failed without retry with status code = ${result.code} and body =\n${result.body}")
                }

                if (args.condition!!.invoke(result)) break
                attempts.add(Attempt(retry, Duration.ofMillis(timeTried), result))
            }
            Thread.sleep(args.interval.toMillis())
        } while (timeTried < args.timeout.toMillis())
        println()

        when (result) {
            is SimpleResponse -> {
                assertThat(args.condition!!.invoke(result))
                    .withFailMessage(
                        "${args.failMessage}Retried ${result.url} and " +
                                "failed with status code = ${result.code} and body =\n${result.body}.\nAttempts:\n${printAttempt(attempts)}"
                    )
                    .isTrue

                return result
            }

            else -> fail("${args.failMessage} Retried $retry times and failed with $result.\nAttempts:\n${printAttempt(attempts)}")
        }
    }
}

private fun unbufferedPrint(c: Char) {
    print(c)
    System.out.flush()
}
