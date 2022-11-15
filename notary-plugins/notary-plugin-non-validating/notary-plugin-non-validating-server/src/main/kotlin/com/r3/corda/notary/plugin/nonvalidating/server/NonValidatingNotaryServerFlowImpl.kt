package com.r3.corda.notary.plugin.nonvalidating.server

import com.r3.corda.notary.plugin.common.toNotarisationResponse
import com.r3.corda.notary.plugin.common.validateRequestSignature
import com.r3.corda.notary.plugin.common.NotarisationRequest
import com.r3.corda.notary.plugin.common.NotarisationResponse
import com.r3.corda.notary.plugin.common.NotaryErrorGeneralImpl
import com.r3.corda.notary.plugin.nonvalidating.api.NonValidatingNotarisationPayload
import net.corda.v5.application.crypto.DigitalSignatureVerificationService
import net.corda.v5.application.flows.CordaInject
import net.corda.v5.application.flows.InitiatedBy
import net.corda.v5.application.flows.ResponderFlow
import net.corda.v5.application.membership.MemberLookup
import net.corda.v5.application.messaging.FlowSession
import net.corda.v5.application.serialization.SerializationService
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.base.util.debug
import net.corda.v5.base.util.loggerFor
import net.corda.v5.ledger.common.Party
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction
import net.corda.v5.ledger.utxo.uniqueness.client.LedgerUniquenessCheckerClientService
import org.slf4j.Logger
import java.lang.IllegalStateException

/**
 * The server-side implementation of the non-validating notary logic.
 * This will be initiated by the client side of this notary plugin: [NonValidatingNotaryClientFlowImpl]
 */
// TODO CORE-7292 What is the best way to define the protocol
// TODO CORE-7249 Currently we need to `spy` this flow because some of the logic is missing and we need to "mock" it.
//  Mockito needs the class the be open to spy it. We need to remove `open` qualifier when we have an actual logic.
@InitiatedBy(protocol = "non-validating-notary")
open class NonValidatingNotaryServerFlowImpl() : ResponderFlow {

    @CordaInject
    private lateinit var clientService: LedgerUniquenessCheckerClientService

    @CordaInject
    private lateinit var serializationService: SerializationService

    @CordaInject
    private lateinit var signatureVerifier: DigitalSignatureVerificationService

    @CordaInject
    private lateinit var memberLookup: MemberLookup

    private companion object {
        val logger: Logger = loggerFor<NonValidatingNotaryServerFlowImpl>()
    }

    internal constructor(
        clientService: LedgerUniquenessCheckerClientService,
        serializationService: SerializationService,
        signatureVerifier: DigitalSignatureVerificationService,
        memberLookup: MemberLookup
    ) : this() {
        this.clientService = clientService
        this.serializationService = serializationService
        this.signatureVerifier = signatureVerifier
        this.memberLookup = memberLookup
    }

    /**
     * The main logic is implemented in this function.
     *
     * The logic is very simple in a few steps:
     * 1. Receive and unpack payload from client
     * 2. Run initial validation (signature etc.)
     * 3. Run verification
     * 4. Request uniqueness checking using the [LedgerUniquenessCheckerClientService]
     * 5. Send the [NotarisationResponse][com.r3.corda.notary.plugin.common.response.NotarisationResponse]
     * back to the client including the specific
     * [NotaryError][net.corda.v5.ledger.notary.plugin.core.NotaryError] if applicable
     */
    @Suspendable
    override fun call(session: FlowSession) {
        try {
            val requestPayload = session.receive(NonValidatingNotarisationPayload::class.java)

            val txDetails = validateRequest(session, requestPayload)
            val request = NotarisationRequest(txDetails.inputs, txDetails.id)

            // TODO This shouldn't ever fail but should add an error handling
            // TODO Discuss this with MGM team but should we able to look up members by X500 name?
            val otherMemberInfo = memberLookup.lookup(session.counterparty)!!
            val otherParty = Party(session.counterparty, otherMemberInfo.sessionInitiationKey)

            validateRequestSignature(
                request,
                otherParty,
                serializationService,
                signatureVerifier,
                requestPayload.requestSignature
            )

            verifyTransaction(requestPayload)

            val uniquenessResponse = clientService.requestUniquenessCheck(
                txDetails.id.toString(),
                txDetails.inputs.map { it.toString() },
                txDetails.references.map { it.toString() },
                txDetails.numOutputs,
                txDetails.timeWindow.from,
                txDetails.timeWindow.until
            )

            logger.debug {
                "Uniqueness check completed for transaction with Tx [${txDetails.id}], " +
                        "result is: ${uniquenessResponse.result}"
            }

            session.send(uniquenessResponse.toNotarisationResponse())
        } catch (e: Exception) {
            logger.warn("Error while processing request from client. Cause: $e")
            session.send(NotarisationResponse(
                emptyList(),
                NotaryErrorGeneralImpl("Error while processing request from client. Reason: ${e.message}", e)
            ))
        }
    }

    /**
     * This function will validate the request payload received from the notary client.
     *
     * @throws IllegalStateException if the request could not be validated.
     *
     * TODO CORE-7249 This function doesn't do much now since we cannot pre-validate anymore, should we remove this?
     */
    @Suspendable
    @Suppress("TooGenericExceptionCaught")
    // TODO CORE-7249 Remove `open` qualifier when we have an actual logic. Mockito needs this function to be open in
    //  order to be mockable (via spy).
    internal open fun validateRequest(otherSideSession: FlowSession,
                                      requestPayload: NonValidatingNotarisationPayload
    ): NonValidatingNotaryTransactionDetails {

        val transactionParts = extractParts(requestPayload)
        logger.debug {
            "Received a notarisation request for Tx [${transactionParts.id}] from [${otherSideSession.counterparty}]"
        }

        return transactionParts
    }

    /**
     * A helper function that constructs an instance of [NonValidatingNotaryTransactionDetails] from the given transaction.
     *
     * TODO CORE-7249 For now this is basically a dummy function. In the old C5 world this function extracted
     *  the data from either the `NotaryChangeWireTransaction` or the `FilteredTransaction` which
     *  do not exist for now.
     */
    @Suspendable
    private fun extractParts(requestPayload: NonValidatingNotarisationPayload): NonValidatingNotaryTransactionDetails {
        val signedTx = requestPayload.transaction as UtxoSignedTransaction
        val ledgerTx = signedTx.toLedgerTransaction()

        return NonValidatingNotaryTransactionDetails(
            signedTx.id,
            requestPayload.numOutputs,
            ledgerTx.timeWindow,
            ledgerTx.inputStateAndRefs.map { it.ref },
            ledgerTx.referenceInputStateAndRefs.map { it.ref }
        )
    }

    /**
     * A non-validating plugin specific verification logic.
     *
     * @throws IllegalStateException if the transaction could not be verified.
     *
     * TODO CORE-7249 This function is not doing anything for now, as FilteredTransaction doesn't exist
     *  and that's the only verification logic we need in the plugin server.
     */
    @Suspendable
    @Suppress(
        "NestedBlockDepth",
        "TooGenericExceptionCaught",
        "ThrowsCount",
        "Unused_Parameter" // TODO CORE-7249 Remove once this function is actually utilised
    )
    // TODO CORE-7249 Remove `open` qualifier when we have an actual logic. Mockito needs this function to be open in
    //  order to be mockable (via spy).
    internal open fun verifyTransaction(requestPayload: NonValidatingNotarisationPayload) {}
}
