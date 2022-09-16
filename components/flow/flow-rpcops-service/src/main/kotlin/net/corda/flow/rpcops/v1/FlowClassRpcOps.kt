package net.corda.flow.rpcops.v1

import net.corda.flow.rpcops.v1.types.response.StartableFlowsResponse
import net.corda.httprpc.RpcOps
import net.corda.httprpc.annotations.HttpRpcGET
import net.corda.httprpc.annotations.HttpRpcPathParameter
import net.corda.httprpc.annotations.HttpRpcResource

/** RPC operations for getting flow information from a vNode. */
@Suppress("MaxLineLength")
@HttpRpcResource(
    name = "Flow Info API",
    description = "The Flow Info API consists of a number of endpoints used to find out which flows can be invoked using the Flow Management API for a given identity.",
    path = "flowclass"
)
interface FlowClassRpcOps : RpcOps {

    @HttpRpcGET(
        path = "{holdingIdentityShortHash}",
        title = "Get Startable Flows",
        description = "This method gets all flows that can be used by the specified holding identity.",
        responseDescription = "The class names of all flows that can be run"
    )
    fun getStartableFlows(
        @HttpRpcPathParameter(description = "Short hash of the holding identity. Obtained during node registration.")
        holdingIdentityShortHash: String
    ): StartableFlowsResponse
}
