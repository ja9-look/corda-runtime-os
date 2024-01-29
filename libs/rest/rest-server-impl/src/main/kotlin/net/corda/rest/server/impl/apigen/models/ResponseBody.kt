package net.corda.rest.server.impl.apigen.models

data class ResponseBody(
    val description: String,
    val successCode: Int,
    val type: Class<*>,
    val parameterizedTypes: List<GenericParameterizedType> = emptyList(),
    val nullable: Boolean = false
)
