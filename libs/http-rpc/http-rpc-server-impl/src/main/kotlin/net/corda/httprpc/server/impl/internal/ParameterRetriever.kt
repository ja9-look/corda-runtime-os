package net.corda.httprpc.server.impl.internal

import com.fasterxml.jackson.databind.node.ObjectNode
import java.io.InputStream
import net.corda.httprpc.server.impl.apigen.processing.Parameter
import net.corda.httprpc.server.impl.apigen.processing.ParameterType
import net.corda.httprpc.server.impl.exception.MissingParameterException
import net.corda.httprpc.server.impl.utils.mapTo
import net.corda.v5.base.util.contextLogger
import net.corda.v5.base.util.trace
import java.net.URLDecoder
import java.util.function.Function

/**
 * Retrieve the Parameter(s) from the context
 */
internal interface ParameterRetriever : Function<ParametersRetrieverContext, Any?>

private fun String.decodeRawString(): String = URLDecoder.decode(this, "UTF-8")

internal object ParameterRetrieverFactory {
    fun create(parameter: Parameter, multipartFileUpload: Boolean): ParameterRetriever =
        when (parameter.type) {
            ParameterType.PATH -> PathParameterRetriever(parameter)
            ParameterType.QUERY -> {
                if (parameter.classType == List::class.java) QueryParameterListRetriever(parameter)
                else QueryParameterRetriever(parameter)
            }
            ParameterType.BODY -> {
                if (multipartFileUpload) MultipartParameterRetriever(parameter)
                else BodyParameterRetriever(parameter)
            }
        }
}

@Suppress("TooGenericExceptionThrown")
private class PathParameterRetriever(private val parameter: Parameter) : ParameterRetriever {
    private companion object {
        private val log = contextLogger()
    }

    override fun apply(ctx: ParametersRetrieverContext): Any {
        try {
            log.trace { "Cast \"${parameter.name}\" to path parameter." }
            val rawParam = ctx.pathParam(parameter.name)
            val decodedParam = rawParam.decodeRawString()
            return decodedParam.mapTo(parameter.classType)
                .also { log.trace { "Cast \"${parameter.name}\" to path parameter completed." } }
        } catch (e: Exception) {
            "Error during Cast \"${parameter.name}\" to path parameter".let {
                log.error("$it: ${e.message}")
                throw e
            }
        }
    }
}

@Suppress("TooGenericExceptionThrown")
private class QueryParameterListRetriever(private val parameter: Parameter) : ParameterRetriever {
    private companion object {
        private val log = contextLogger()
    }

    override fun apply(ctx: ParametersRetrieverContext): Any {
        try {
            log.trace { "Cast \"${parameter.name}\" to query parameter list." }
            val paramValues = ctx.queryParams(parameter.name)

            if (parameter.required && paramValues.isEmpty())
                throw MissingParameterException("Missing query parameter \"${parameter.name}\".")

            return paramValues.map { it.decodeRawString() }
                .also { log.trace { "Cast \"${parameter.name}\" to query parameter list completed." } }
        } catch (e: Exception) {
            "Error during Cast \"${parameter.name}\" to query parameter list.".let {
                log.error("$it: ${e.message}")
                throw e
            }
        }
    }
}

@Suppress("TooGenericExceptionThrown")
private class QueryParameterRetriever(private val parameter: Parameter) : ParameterRetriever {
    private companion object {
        private val log = contextLogger()
    }

    override fun apply(ctx: ParametersRetrieverContext): Any? {
        try {
            log.trace { "Cast \"${parameter.name}\" to query parameter." }

            if (parameter.required && ctx.queryParam(parameter.name) == null)
                throw MissingParameterException("Missing query parameter \"${parameter.name}\".")

            val rawQueryParam: String? = ctx.queryParam(parameter.name, parameter.default)
            return rawQueryParam?.decodeRawString()?.mapTo(parameter.classType)
                .also { log.trace { "Cast \"${parameter.name}\" to query parameter completed." } }
        } catch (e: Exception) {
            "Error during Cast \"${parameter.name}\" to query parameter".let {
                log.error("$it: ${e.message}")
                throw e
            }
        }
    }
}

@Suppress("TooGenericExceptionThrown")
private class BodyParameterRetriever(private val parameter: Parameter) : ParameterRetriever {
    private companion object {
        private val log = contextLogger()
    }

    override fun apply(ctx: ParametersRetrieverContext): Any? {
        try {
            log.trace { "Cast \"${parameter.name}\" to body parameter." }

            val node = if (ctx.body().isBlank()) null else ctx.bodyAsClass(ObjectNode::class.java).get(parameter.name)

            if (parameter.required && node == null) throw MissingParameterException("Missing body parameter \"${parameter.name}\".")

            val field = node?.toString() ?: "null"
            return ctx.fromJsonString(field, parameter.classType)
                .also { log.trace { "Cast \"${parameter.name}\" to body parameter completed." } }
        } catch (e: Exception) {
            "Error during Cast \"${parameter.name}\" to body parameter".let {
                log.error("$it: ${e.message}")
                throw e
            }
        }
    }
}

@Suppress("TooGenericExceptionThrown")
private class MultipartParameterRetriever(private val parameter: Parameter) : ParameterRetriever {
    private companion object {
        private val log = contextLogger()
    }

    @Suppress("ComplexMethod")
    override fun apply(ctx: ParametersRetrieverContext): Any {
        try {
            log.trace { "Cast \"${parameter.name}\" to body parameter." }

            if (parameter.isFileUpload) {
                val uploadedFiles = ctx.uploadedFiles(parameter.name)

                if (uploadedFiles.isEmpty())
                    throw MissingParameterException("Expected file with parameter name \"${parameter.name}\" but it was not found.")

                if (Collection::class.java.isAssignableFrom(parameter.classType))
                    return uploadedFiles

                if (InputStream::class.java.isAssignableFrom(parameter.classType)) {
                    return uploadedFiles.first().content
                }

                return uploadedFiles.first()
            }

            val formParameterAsList = ctx.formParams(parameter.name)

            if (!parameter.nullable && formParameterAsList.isEmpty()) {
                throw MissingParameterException("Missing form parameter \"${parameter.name}\".")
            }

            log.trace { "Cast \"${parameter.name}\" to multipart form parameter completed." }

            if (Collection::class.java.isAssignableFrom(parameter.classType)) {
                return formParameterAsList
            }
            return formParameterAsList.first()
        } catch (e: Exception) {
            "Error during Cast \"${parameter.name}\" to multipart form parameter".let {
                log.error("$it: ${e.message}")
                throw e
            }
        }
    }
}