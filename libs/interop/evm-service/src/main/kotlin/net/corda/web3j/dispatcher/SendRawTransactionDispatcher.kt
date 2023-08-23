package net.corda.web3j.dispatcher

import java.math.BigInteger
import net.corda.data.interop.evm.EvmRequest
import net.corda.data.interop.evm.EvmResponse
import net.corda.data.interop.evm.request.Transaction
import net.corda.web3j.EthereumConnector
import net.corda.web3j.GenericResponse
import net.corda.web3j.constants.GET_CHAIN_ID
import net.corda.web3j.constants.GET_TRANSACTION_COUNT
import net.corda.web3j.constants.LATEST
import net.corda.web3j.constants.SEND_RAW_TRANSACTION
import net.corda.web3j.constants.TEMPORARY_PRIVATE_KEY
import net.corda.web3j.encoder.TransactionEncoder
import org.web3j.crypto.Credentials
import org.web3j.crypto.RawTransaction
import org.web3j.service.TxSignServiceImpl
import org.web3j.utils.Numeric

/**
 * Dispatcher used to send transaction.
 *
 * @param evmConnector The evmConnector class used to make rpc calls to the node
 */
class SendRawTransactionDispatcher(private val evmConnector: EthereumConnector) : EvmDispatcher {
    private val encoder = TransactionEncoder()

    // This is used in absence of the crypto worker being able to sign these transactions for use
    private val temporaryPrivateKey = TEMPORARY_PRIVATE_KEY

    // Temporary Max Fee Per Gas Multiplier


    override fun dispatch(evmRequest: EvmRequest): EvmResponse {
        val request = evmRequest.payload as Transaction
        val data = encoder.encode(request.function, request.parameters)


        val transactionCountResponse = evmConnector.send<GenericResponse>(
            evmRequest.rpcUrl, GET_TRANSACTION_COUNT, listOf(evmRequest.from, LATEST)
        )
        val nonce = BigInteger.valueOf(Integer.decode(transactionCountResponse.result.toString()).toLong())
        val chainId = evmConnector.send<GenericResponse>(evmRequest.rpcUrl, GET_CHAIN_ID, emptyList<String>())
        val parsedChainId = Numeric.toBigInt(chainId.result.toString()).toLong()


        val transaction = RawTransaction.createTransaction(
            parsedChainId,
            nonce,
            Numeric.toBigInt(request.options.gasLimit),
            evmRequest.to,
            BigInteger.valueOf(request.options.value.toLong()),
            data,
            Numeric.toBigInt(request.options.maxPriorityFeePerGas),
            Numeric.toBigInt(request.options.maxFeePerGas)
        )

        val signer = Credentials.create(temporaryPrivateKey)
        val signed = TxSignServiceImpl(signer).sign(transaction, parsedChainId)
        val tReceipt = evmConnector.send<GenericResponse>(
            evmRequest.rpcUrl, SEND_RAW_TRANSACTION, listOf(Numeric.toHexString(signed))
        )

        return EvmResponse(tReceipt.result.toString())

    }
}