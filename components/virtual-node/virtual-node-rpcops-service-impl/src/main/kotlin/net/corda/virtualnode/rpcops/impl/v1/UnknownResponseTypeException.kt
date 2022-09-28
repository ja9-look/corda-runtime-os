package net.corda.virtualnode.rpcops.impl.v1

import net.corda.v5.base.exceptions.CordaRuntimeException

class UnknownResponseTypeException(type: String) : CordaRuntimeException("Encountered a response of unknown type: $type")
