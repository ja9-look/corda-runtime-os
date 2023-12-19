package com.r3.corda.demo.swaps.workflows.eth2eth

import com.r3.corda.demo.swaps.workflows.Constants
import java.math.BigInteger
import net.corda.v5.application.flows.ClientRequestBody
import net.corda.v5.application.flows.ClientStartableFlow
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.FlowEngine
import net.corda.v5.application.flows.SubFlow
import net.corda.v5.application.interop.evm.Block
import net.corda.v5.application.interop.evm.EvmService
import net.corda.v5.application.interop.evm.options.EvmOptions
import net.corda.v5.application.marshalling.JsonMarshallingService
import net.corda.v5.base.annotations.Suspendable
import org.slf4j.LoggerFactory

class GetBlockByNumberFlowInput {
    val number: BigInteger? = null
    val includeTransactions: Boolean? = null
}

data class GetBlockByNumberFlowOutput(
    val block: Block? = null
)

class GetBlockByNumberFlow : ClientStartableFlow {
    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var flowEngine: FlowEngine

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    override fun call(requestBody: ClientRequestBody): String {
        log.trace("Starting Evm Get Block Client Flow...")

        val inputs = requestBody.getRequestBodyAs(jsonMarshallingService, GetBlockByNumberFlowInput::class.java)
        val output = flowEngine.subFlow(GetBlockByNumberSubFlow(inputs.number!!, inputs.includeTransactions!!))

        return jsonMarshallingService.format(GetBlockByNumberFlowOutput(output))
    }
}

@Suppress("unused")
class GetBlockByNumberSubFlow(
    private val number: BigInteger,
    private val includeTransactions: Boolean
) : SubFlow<Block> {
    private companion object {
        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @CordaInject
    lateinit var evmService: EvmService

    @CordaInject
    lateinit var jsonMarshallingService: JsonMarshallingService

    @Suspendable
    override fun call(): Block {
        log.trace("Starting Evm Get Block Sub Flow...")
        try {
            val evmOptions = EvmOptions(
                Constants.RPC_URL,
                ""
            )
            // Get the block
            return evmService.getBlockByNumber(number, includeTransactions, evmOptions)
        } catch (e: Exception) {
            log.error("Error in Evm Get Block Sub Flow", e)
            throw e
        }
    }
}