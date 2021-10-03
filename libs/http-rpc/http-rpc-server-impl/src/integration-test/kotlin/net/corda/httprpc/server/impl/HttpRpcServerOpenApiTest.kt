package net.corda.httprpc.server.impl

import io.swagger.v3.core.util.Json
import io.swagger.v3.oas.models.OpenAPI
import net.corda.httprpc.server.config.models.HttpRpcSettings
import net.corda.httprpc.server.impl.internal.OptionalDependency
import net.corda.httprpc.server.impl.rpcops.impl.CalendarControllerImpl
import net.corda.httprpc.server.impl.rpcops.impl.TestHealthCheckControllerImpl
import net.corda.httprpc.server.impl.utils.TestHttpClientUnirestImpl
import net.corda.httprpc.server.impl.utils.WebRequest
import net.corda.httprpc.server.impl.utils.compact
import net.corda.httprpc.tools.HttpVerb
import net.corda.v5.base.util.NetworkHostAndPort
import org.apache.http.HttpStatus
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class HttpRpcServerOpenApiTest : HttpRpcServerTestBase() {
    companion object {
        val httpRpcSettings = HttpRpcSettings(
            NetworkHostAndPort("localhost", findFreePort()),
            context,
            null,
            null,
            HttpRpcSettings.MAX_CONTENT_LENGTH_DEFAULT_VALUE
        )

        @BeforeAll
        @JvmStatic
        fun setUpBeforeClass() {
            server = HttpRpcServerImpl(
                listOf(CalendarControllerImpl(), TestHealthCheckControllerImpl()),
                securityManager,
                httpRpcSettings,
                true
            ).apply { start() }
            client =
                TestHttpClientUnirestImpl("http://${httpRpcSettings.address.host}:${httpRpcSettings.address.port}/${httpRpcSettings.context.basePath}/v${httpRpcSettings.context.version}/")
        }

        @AfterAll
        @JvmStatic
        fun cleanUpAfterClass() {
            if (isServerInitialized()) {
                server.stop()
            }
        }
    }

    @Test
    fun `GET openapi should return the OpenApi spec json`() {
        val apiSpec = client.call(HttpVerb.GET, WebRequest<Any>("swagger.json"))
        assertEquals(HttpStatus.SC_OK, apiSpec.responseStatus)
        assertEquals("application/json", apiSpec.headers["Content-Type"])
        val body = apiSpec.body!!.compact()
        assertTrue(body.contains(""""openapi":"3.0.1""""))
        assertFalse(body.contains("\"null\""))
        assertFalse(body.contains("null,"))

        val openAPI = Json.mapper().readValue(body, OpenAPI::class.java)

        val path = openAPI.paths["/api/v1/calendar/daysoftheyear"]
        assertNotNull(path)

        val requestBody = path.post.requestBody
        assertTrue(requestBody.content.containsKey("application/json"))

        val mediaType = requestBody.content["application/json"]
        assertNotNull(mediaType)
        assertEquals("#/components/schemas/CalendarDaysOfTheYearRequest", mediaType.schema.`$ref`)

        val responseOk = path.post.responses["200"]
        assertNotNull(responseOk)
        //need to assert that FiniteDurableReturnResult is generated as a referenced schema rather than inline content
        assertEquals(
            "#/components/schemas/CalendarDaysOfTheYearPollResultResponse",
            responseOk.content["application/json"]!!.schema.`$ref`
        )

        // need to assert "items" by contains this way because when serializing the Schema is not delegated to ArraySchema
        assertThat(body.compact()).contains(finiteDurableReturnResultRef.compact())
        assertThat(body.compact()).contains(schemaDef.compact())

        assertTrue(openAPI.components.schemas.containsKey("CalendarDaysOfTheYearRequest"))
        assertThat(body.compact()).contains(finiteDurableReturnResultSchemaWithCalendarDayRef.compact())

        assertTrue(openAPI.components.schemas.containsKey("TimeCallDto"))
        val timeCallDto = openAPI.components.schemas["TimeCallDto"]
        assertNotNull(timeCallDto)
        val timeProperty = timeCallDto.properties["time"]
        assertNotNull(timeProperty)
        // Javalin cannot currently provide an example
//        assertDoesNotThrow { ZonedDateTime.parse(timeProperty.example.toString()) }
    }

    @Test
    fun `GET swagger UI should return html with reference to swagger json`() {
        val apiSpec = client.call(HttpVerb.GET, WebRequest<Any>("swagger"))
        assertEquals(HttpStatus.SC_OK, apiSpec.responseStatus)
        assertEquals("text/html", apiSpec.headers["Content-Type"])
        // Removed the "/" because it made the actual Swagger UI render badly when it was there.
        val expected = """url: "${context.basePath}/v${context.version}/swagger.json""""
        assertTrue(apiSpec.body!!.contains(expected))
    }

    @Test
    fun `GET swagger UI dependencies should return non empty result`() {
        val baseClient = TestHttpClientUnirestImpl("http://${httpRpcSettings.address.host}:${httpRpcSettings.address.port}/")
        val swaggerUIversion = OptionalDependency.SWAGGERUI.version
        val swagger = baseClient.call(HttpVerb.GET, WebRequest<Any>("api/v1/swagger"))
        val swaggerUIBundleJS = baseClient.call(HttpVerb.GET, WebRequest<Any>("webjars/swagger-ui/$swaggerUIversion/swagger-ui-bundle.js"))
        val swaggerUIcss = baseClient.call(HttpVerb.GET, WebRequest<Any>("webjars/swagger-ui/$swaggerUIversion/swagger-ui-bundle.js"))


        assertEquals(HttpStatus.SC_OK, swagger.responseStatus)
        assertEquals(HttpStatus.SC_OK, swaggerUIBundleJS.responseStatus)
        assertEquals(HttpStatus.SC_OK, swaggerUIcss.responseStatus)
        assertNotNull(swaggerUIBundleJS.body)
        assertNotNull(swaggerUIcss.body)
    }

//    private val schemaDef = """"CalendarDay" : {
//        "required" : [ "dayOfWeek", "dayOfYear" ],
//        "type" : "object",
//        "properties" : {
//          "dayOfWeek" : {
//            "nullable" : false,
//            "example" : "TUESDAY",
//            "enum" : [ "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY" ]
//          },
//          "dayOfYear" : {
//            "type" : "string",
//            "nullable" : false,
//            "example" : "string"
//          }
//        },
//        "nullable" : false
//      }""".trimIndent()

    // Doesn't seem to be a way to currently mark nullable and optional fields in Javalin
    // Altering this schema to how Javalin is generating it
    // The `example` property is lost but an example value is shown in the Swagger UI
    private val schemaDef = """"CalendarDay" : {
        "required" : [ "dayOfWeek", "dayOfYear" ],
        "type" : "object",
        "properties" : {
          "dayOfWeek" : {
            "type" : "string",
            "enum" : [ "MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY" ]
          },
          "dayOfYear" : {
            "type" : "string"
          }
        }
      }""".trimIndent().replace(" ", "")

//    private val finiteDurableReturnResultSchemaWithCalendarDayRef = """"FiniteDurableReturnResult_of_CalendarDay" : {
//        "required" : [ "isLastResult", "positionedValues" ],
//        "type" : "object",
//        "properties" : {
//          "isLastResult" : {
//            "type" : "boolean",
//            "nullable" : false,
//            "example" : true
//          },
//          "positionedValues" : {
//            "uniqueItems" : false,
//            "type" : "array",
//            "nullable" : false,
//            "items" : {
//              "type" : "object",
//              "properties" : {
//                "position" : {
//                  "type" : "integer",
//                  "format" : "int64",
//                  "nullable" : false,
//                  "example" : 0
//                },
//                "value" : {
//                  "${"$"}ref" : "#/components/schemas/CalendarDay"
//                }
//              },
//              "nullable" : false,
//              "example" : "No example available for this type"
//            }
//          }""".trimIndent()

    // Doesn't seem to be a way to currently mark nullable and optional fields in Javalin
    // Altering this schema to how Javalin is generating it
    // The `example` property is lost but an example value is shown in the Swagger UI
    // The generated schema is using $refs to point to other schemas
    private val finiteDurableReturnResultSchemaWithCalendarDayRef = """"CalendarDaysOfTheYearPollResultResponse" : {
        "required" : [ "isLastResult", "positionedValues" ],
        "type" : "object",
        "properties" : {
          "positionedValues" : {
             "type" : "array",
             "items" : {
               "${"$"}ref":"#/components/schemas/PositionedValueCalendarDay"
             }
            },
            "remainingElementsCountEstimate":{
             "type":"integer",
             "format":"int64"
            },
            "isLastResult" : {
             "type" : "boolean"
            }
           }
          }""".trimIndent().replace(" ", "")

    private val finiteDurableReturnResultRef: String = """
         ref":"#/components/schemas/CalendarDaysOfTheYearPollResultResponse
        """.trimIndent()
}
