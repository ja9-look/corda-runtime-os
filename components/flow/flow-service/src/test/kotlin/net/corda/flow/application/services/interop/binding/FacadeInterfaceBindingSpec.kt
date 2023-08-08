package net.corda.flow.application.services.interop.binding

import net.corda.flow.application.services.impl.interop.binding.creation.bindTo
import net.corda.flow.application.services.impl.interop.facade.FacadeReaders
import net.corda.flow.application.services.interop.JavaTokensFacade
import net.corda.v5.application.interop.binding.BindsFacade
import net.corda.v5.application.interop.binding.FacadeVersions
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.reflect.KClass

interface DoesNotBindAFacade

@BindsFacade("org.example.com/another/facade/altogether")
interface BindsAnotherFacade

@BindsFacade("org.corda.interop/platform/tokens")
@FacadeVersions("v2.0")
interface BindsToV2FacadeOnly
class FacadeInterfaceBindingSpec {
    val facadeV1 = FacadeReaders.JSON.read(this::class.java.getResourceAsStream("/sampleFacades/tokens-facade.json")!!)
    val facadeV2 =
        FacadeReaders.JSON.read(this::class.java.getResourceAsStream("/sampleFacades/tokens-facade_v2.json")!!)

    infix fun <T : Any> KClass<T>.shouldFailToBindWith(expectedMessage: String) =
        facadeV1.assertBindingFails(java, expectedMessage)

    @Test
    fun `should fail if the interface does not bind any facade`() {
        DoesNotBindAFacade::class shouldFailToBindWith "Interface is not annotated with @BindsFacade"
    }

    @Test
    fun `should fail if the interface does not bind the requested facade`() {
        BindsAnotherFacade::class shouldFailToBindWith
                "Mismatch: interface's @BindsFacade annotation declares that it is bound to " +
                "org.example.com/another/facade/altogether"
    }

    @Test
    fun `should fail if the interface does not bind to this version of the requested facade`() {
        BindsToV2FacadeOnly::class shouldFailToBindWith
                "Mismatch: interface explicitly declares binding to versions [v2.0] of " +
                "org.corda.interop/platform/tokens, but facade has version v1."
    }

    @Test
    fun `should succeed if the interface explicitly binds to this version of the requested facade`() {
        assertEquals(facadeV2, facadeV2.bindTo<BindsToV2FacadeOnly>().facade)
    }

    @Test
    fun `should succeed`() {
        facadeV1.bindTo<JavaTokensFacade>()
        facadeV2.bindTo<JavaTokensFacade>()
    }
}