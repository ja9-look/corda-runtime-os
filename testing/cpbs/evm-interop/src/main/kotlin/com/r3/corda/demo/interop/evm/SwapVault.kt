package com.r3.corda.demo.interop.evm

import net.corda.v5.application.interop.evm.EvmService
import net.corda.v5.application.interop.evm.Parameter
import net.corda.v5.application.interop.evm.Type
import net.corda.v5.application.interop.evm.options.CallOptions
import net.corda.v5.application.interop.evm.options.EvmOptions
import net.corda.v5.application.interop.evm.options.TransactionOptions
import java.math.BigInteger

class SwapVault(
    private val rpcUrl: String,
    private val evmService: EvmService,
    private val contractAddress: String,
) {

    fun claimCommitment(swapId: String): Boolean {
        val dummyGasNumber = BigInteger("a41c5", 16)
        val transactionOptions = TransactionOptions(
            dummyGasNumber,                 // gasLimit
            0.toBigInteger(),               // value
            20000000000.toBigInteger(),     // maxFeePerGas
            20000000000.toBigInteger(),     // maxPriorityFeePerGas
            rpcUrl,                // rpcUrl
            contractAddress,          // from
        )

        val parameters = listOf(
            Parameter.of("swapId", Type.STRING, swapId),
        )

        val hash = evmService.transaction(
            "claimCommitment",
            contractAddress,
            transactionOptions,
            parameters
        )

        val receipt = evmService.waitForTransaction(hash, transactionOptions)
        return receipt.status
    }


    fun commit(swapId: String, recipient: String, signaturesThreshold: BigInteger): Boolean {
        val dummyGasNumber = BigInteger("a41c5", 16)
        val transactionOptions = TransactionOptions(
            dummyGasNumber,                 // gasLimit
            0.toBigInteger(),               // value
            20000000000.toBigInteger(),     // maxFeePerGas
            20000000000.toBigInteger(),     // maxPriorityFeePerGas
            rpcUrl,                // rpcUrl
            contractAddress,          // from
        )

        val parameters = listOf(
            Parameter.of("swapId", Type.STRING, swapId),
            Parameter.of("recipient", Type.ADDRESS, recipient),
            Parameter.of("signaturesThreshold", Type.UINT256, signaturesThreshold),
        )

        val hash = evmService.transaction(
            "commit",
            contractAddress,
            transactionOptions,
            parameters
        )

        val receipt = evmService.waitForTransaction(hash, transactionOptions)
        return receipt.status
    }

    fun commit(swapId: String, recipient: String, signaturesThreshold: BigInteger, signatures: List<String>): Boolean {
        val dummyGasNumber = BigInteger("a41c5", 16)
        val transactionOptions = TransactionOptions(
            dummyGasNumber,                 // gasLimit
            0.toBigInteger(),               // value
            20000000000.toBigInteger(),     // maxFeePerGas
            20000000000.toBigInteger(),     // maxPriorityFeePerGas
            rpcUrl,                // rpcUrl
            contractAddress,          // from
        )

        val parameters = listOf(
            Parameter.of("swapId", Type.STRING, swapId),
            Parameter.of("recipient", Type.ADDRESS, recipient),
            Parameter.of("signaturesThreshold", Type.UINT256, signaturesThreshold),
            Parameter.of("signatures", Type.ADDRESS_LIST, signatures),
        )

        val hash = evmService.transaction(
            "commit",
            contractAddress,
            transactionOptions,
            parameters
        )

        val receipt = evmService.waitForTransaction(hash, transactionOptions)
        return receipt.status
    }


    fun commitWithToken(
        swapId: String,
        tokenAddress: String,
        tokenId: BigInteger,
        amount: BigInteger,
        recipient: String,
        String: String,
        signaturesThreshold: BigInteger
    ): Boolean {
        val dummyGasNumber = BigInteger("a41c5", 16)
        val transactionOptions = TransactionOptions(
            dummyGasNumber,                 // gasLimit
            0.toBigInteger(),               // value
            20000000000.toBigInteger(),     // maxFeePerGas
            20000000000.toBigInteger(),     // maxPriorityFeePerGas
            rpcUrl,                // rpcUrl
            contractAddress,          // from
        )

        val parameters = listOf(
            Parameter.of("swapId", Type.STRING, swapId),
            Parameter.of("tokenAddress", Type.ADDRESS, tokenAddress),
            Parameter.of("tokenId", Type.UINT256, tokenId),
            Parameter.of("amount", Type.UINT256, amount),
            Parameter.of("recipient", Type.ADDRESS, recipient),
            Parameter.of("signaturesThreshold", Type.UINT256, signaturesThreshold),
        )

        val hash = evmService.transaction(
            "commitWithToken",
            contractAddress,
            transactionOptions,
            parameters
        )

        val receipt = evmService.waitForTransaction(hash, transactionOptions)
        return receipt.status
    }


    fun commitWithToken(
        swapId: String,
        tokenAddress: String,
        amount: BigInteger,
        recipient: String,
        signaturesThreshold: BigInteger,
        signatures: List<String>
    ): Boolean {
        val dummyGasNumber = BigInteger("a41c5", 16)
        val transactionOptions = TransactionOptions(
            dummyGasNumber,                 // gasLimit
            0.toBigInteger(),               // value
            20000000000.toBigInteger(),     // maxFeePerGas
            20000000000.toBigInteger(),     // maxPriorityFeePerGas
            rpcUrl,                // rpcUrl
            contractAddress,          // from
        )

        val parameters = listOf(
            Parameter.of("swapId", Type.STRING, swapId),
            Parameter.of("tokenAddress", Type.ADDRESS, tokenAddress),
            Parameter.of("amount", Type.UINT256, amount),
            Parameter.of("recipient", Type.ADDRESS, recipient),
            Parameter.of("signaturesThreshold", Type.UINT256, signaturesThreshold),
            Parameter.of("signatures", Type.ADDRESS_LIST, signatures),
        )

        val hash = evmService.transaction(
            "commitWithToken",
            contractAddress,
            transactionOptions,
            parameters
        )

        val receipt = evmService.waitForTransaction(hash, transactionOptions)
        return receipt.status
    }

    fun commitWithToken(
        swapId: String,
        tokenAddress: String,
        tokenId: BigInteger,
        amount: BigInteger,
        recipient: String,
        signaturesThreshold: BigInteger,
        signatures: List<String>
    ): Boolean {
        val dummyGasNumber = BigInteger("a41c5", 16)
        val transactionOptions = TransactionOptions(
            dummyGasNumber,                 // gasLimit
            0.toBigInteger(),               // value
            20000000000.toBigInteger(),     // maxFeePerGas
            20000000000.toBigInteger(),     // maxPriorityFeePerGas
            rpcUrl,                // rpcUrl
            contractAddress,          // from
        )

        val parameters = listOf(
            Parameter.of("swapId", Type.STRING, swapId),
            Parameter.of("tokenAddress", Type.ADDRESS, tokenAddress),
            Parameter.of("tokenId", Type.UINT256, tokenId),
            Parameter.of("amount", Type.UINT256, amount),
            Parameter.of("recipient", Type.ADDRESS, recipient),
            Parameter.of("signaturesThreshold", Type.UINT256, signaturesThreshold),
            Parameter.of("signatures", Type.ADDRESS_LIST, signatures),
        )

        val hash = evmService.transaction(
            "commitWithToken",
            contractAddress,
            transactionOptions,
            parameters
        )

        val receipt = evmService.waitForTransaction(hash, transactionOptions)
        return receipt.status
    }


    fun revertCommit(swapId: String): Boolean {
        val dummyGasNumber = BigInteger("a41c5", 16)
        val transactionOptions = TransactionOptions(
            dummyGasNumber,                 // gasLimit
            0.toBigInteger(),               // value
            20000000000.toBigInteger(),     // maxFeePerGas
            20000000000.toBigInteger(),     // maxPriorityFeePerGas
            rpcUrl,                // rpcUrl
            contractAddress,          // from
        )

        val parameters = listOf(
            Parameter.of("swapId", Type.STRING, swapId),
        )

        val hash = evmService.transaction(
            "revertCommit",
            contractAddress,
            transactionOptions,
            parameters
        )

        val receipt = evmService.waitForTransaction(hash, transactionOptions)
        return receipt.status
    }


    fun commitmentHash(swapId: String): String {
        val parameters = listOf(
            Parameter.of("swapId", Type.STRING, swapId),
        )
        val hash = evmService.call(
            "commitmentHash",
            contractAddress,
            CallOptions(
                EvmOptions(
                    rpcUrl,
                    ""
                )
            ),
            Type.BYTES,
            parameters
        )

        return hash
    }


    fun recoverSigner(messageHash: String, signature: String): String {
        val parameters = listOf(
            Parameter.of("messageHash", Type.BYTES, messageHash),
            Parameter.of("signature", Type.BYTES, signature),
        )
        val signer = evmService.call(
            "recoverSigner",
            contractAddress,
            CallOptions(
                EvmOptions(
                    rpcUrl,
                    ""
                )
            ),
            Type.ADDRESS,
            parameters
        )

        return signer
    }


}