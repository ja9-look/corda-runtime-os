package net.corda.httprpc.server.impl

import net.corda.httprpc.security.read.RPCSecurityManager
import net.corda.httprpc.server.HttpRpcServer
import net.corda.httprpc.server.config.HttpRpcSettingsProvider
import net.corda.httprpc.server.config.impl.HttpRpcObjectSettingsProvider
import net.corda.httprpc.server.config.models.HttpRpcSettings
import net.corda.httprpc.server.impl.internal.HttpRpcServerInternal
import net.corda.httprpc.server.impl.security.SecurityManagerRPCImpl
import net.corda.httprpc.server.impl.security.provider.AuthenticationProvider
import net.corda.httprpc.server.impl.security.provider.basic.UsernamePasswordAuthenticationProvider
import net.corda.httprpc.server.impl.security.provider.bearer.azuread.AzureAdAuthenticationProvider
import net.corda.v5.base.util.contextLogger
import net.corda.httprpc.Controller
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.write

@SuppressWarnings("TooGenericExceptionThrown", "TooGenericExceptionCaught", "LongParameterList")
class HttpRpcServerImpl(
    controllers: List<Controller>,
    rpcSecurityManager: RPCSecurityManager,
    httpRpcSettings: HttpRpcSettings,
    devMode: Boolean

) : HttpRpcServer {
    private companion object {
        private val log = contextLogger()
    }

    @Volatile
    private var running = false
    private val startStopLock = ReentrantReadWriteLock()

    override val isRunning: Boolean
        get() = running


    private val httpRpcObjectConfigProvider = HttpRpcObjectSettingsProvider(httpRpcSettings, devMode)
//    private val httpRpcServerInternal = HttpRpcServerInternal(
//        JavalinRouteProviderImpl(
//            httpRpcSettings.context.basePath,
//            httpRpcSettings.context.version,
//            resources,
//            cordappClassLoader
//        ),
//        SecurityManagerRPCImpl(createAuthenticationProviders(httpRpcObjectConfigProvider, rpcSecurityManager)),
//        httpRpcObjectConfigProvider,
//        OpenApiInfoProvider(resources, httpRpcObjectConfigProvider)
//    )

    // remove the route provider
    // register the controllers instead
    private val httpRpcServerInternal = HttpRpcServerInternal(
        SecurityManagerRPCImpl(createAuthenticationProviders(httpRpcObjectConfigProvider, rpcSecurityManager)),
        httpRpcObjectConfigProvider,
        controllers
    )


    override fun start() {
        startStopLock.write {
            if (!running) {
                log.info("Started the server")
                httpRpcServerInternal.start()
                running = true
            }
        }
    }

    override fun stop() {
        startStopLock.write {
            if (running) {
                log.info("Stop the server.")
                httpRpcServerInternal.stop()
                running = false
            }
        }
    }

    private fun createAuthenticationProviders(
        settings: HttpRpcSettingsProvider,
        rpcSecurityManager: RPCSecurityManager
    ): Set<AuthenticationProvider> {
        val result = mutableSetOf<AuthenticationProvider>(UsernamePasswordAuthenticationProvider(rpcSecurityManager))
        val azureAdSettings = settings.getSsoSettings()?.azureAd()
        if (azureAdSettings != null) {
            result.add(AzureAdAuthenticationProvider.createDefault(azureAdSettings, rpcSecurityManager))
        }
        return result
    }
}
