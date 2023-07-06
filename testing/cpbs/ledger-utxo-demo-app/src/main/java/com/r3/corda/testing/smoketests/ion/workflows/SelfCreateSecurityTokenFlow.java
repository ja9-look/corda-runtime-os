package com.r3.corda.testing.smoketests.ion.workflows;

import com.r3.corda.testing.smoketests.ion.contracts.SecurityTokenContract;
import com.r3.corda.testing.smoketests.ion.states.SecurityToken;
import net.corda.v5.application.flows.ClientRequestBody;
import net.corda.v5.application.flows.ClientStartableFlow;
import net.corda.v5.application.flows.CordaInject;
import net.corda.v5.application.flows.FlowEngine;
import net.corda.v5.application.marshalling.JsonMarshallingService;
import net.corda.v5.application.membership.MemberLookup;
import net.corda.v5.base.annotations.Suspendable;
import net.corda.v5.base.exceptions.CordaRuntimeException;
import net.corda.v5.ledger.common.NotaryLookup;
import net.corda.v5.ledger.utxo.UtxoLedgerService;
import net.corda.v5.ledger.utxo.transaction.UtxoSignedTransaction;
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionBuilder;
import net.corda.v5.membership.MemberInfo;
import net.corda.v5.membership.NotaryInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;

public abstract class SelfCreateSecurityTokenFlow implements ClientStartableFlow {

    private final static Logger log = LoggerFactory.getLogger(SelfCreateSecurityTokenFlow.class);

    @CordaInject
    public JsonMarshallingService jsonMarshallingService;

    @CordaInject
    public MemberLookup memberLookup;

    // Injects the UtxoLedgerService to enable the flow to make use of the Ledger API.
    @CordaInject
    public UtxoLedgerService ledgerService;

    @CordaInject
    public NotaryLookup notaryLookup;

    // FlowEngine service is required to run SubFlows.
    @CordaInject
    public FlowEngine flowEngine;


    @Suspendable
    @Override
    public String call(ClientRequestBody requestBody) {

        log.info("SelfCreateSecurityTokenFlow.call() called");

        try {
            // Obtain the deserialized input arguments to the flow from the requestBody.
            SelfCreateSecurityTokenFlowArgs flowArgs = requestBody.getRequestBodyAs(jsonMarshallingService, SelfCreateSecurityTokenFlowArgs.class);

            // Get MemberInfos for the Vnode running the flow and the otherMember.
            MemberInfo myInfo = memberLookup.myInfo();

            SecurityToken output = new SecurityToken(flowArgs.getCusip(), flowArgs.getQuantity(),myInfo.getName(), myInfo.getName(), Arrays.asList(myInfo.getLedgerKeys().get(0)));

            // Obtain the Notary name and public key.
            NotaryInfo notary = notaryLookup.getNotaryServices().iterator().next();
            /*
            PublicKey notaryKey = null;
            for(MemberInfo memberInfo: memberLookup.lookup()){
                if(Objects.equals(
                        memberInfo.getMemberProvidedContext().get("corda.notary.service.name"),
                        notary.getName().toString())) {
                    notaryKey = memberInfo.getLedgerKeys().get(0);
                    break;
                }
            }
            // Note, in Java CorDapps only unchecked RuntimeExceptions can be thrown not
            // declared checked exceptions as this changes the method signature and breaks override.
            if(notaryKey == null) {
                throw new CordaRuntimeException("No notary PublicKey found");
            }
            */
            // Use UTXOTransactionBuilder to build up the draft transaction.
            UtxoTransactionBuilder txBuilder = ledgerService.createTransactionBuilder()
                    .setNotary(notary.getName())
                    .setTimeWindowBetween(Instant.now(), Instant.now().plusMillis(Duration.ofDays(1).toMillis()))
                    .addOutputState(output)
                    .addCommand(new SecurityTokenContract.Create())
                    .addSignatories(output.getParticipants());

            // Convert the transaction builder to a UTXOSignedTransaction and sign with this Vnode's first Ledger key.
            // Note, toSignedTransaction() is currently a placeholder method, hence being marked as deprecated.
            @SuppressWarnings("DEPRECATION")
            UtxoSignedTransaction signedTransaction = txBuilder.toSignedTransaction();

            // Call FinalizeBilateral which will finalise the transaction.
            // If successful the flow will return a String of the created transaction id,
            // if not successful it will return an error message.
            return flowEngine.subFlow(new FinalizeTxFlow.FinalizeTx(signedTransaction, Arrays.asList()));
        }
        // Catch any exceptions, log them and rethrow the exception.
        catch (Exception e) {
            log.warn("Failed to process utxo flow for request body " + requestBody + " because: " + e.getMessage());
            throw new CordaRuntimeException(e.getMessage());
        }
    }
}
/*
RequestBody for triggering the flow via http-rpc:
{
    "clientRequestId": "issueSecurityToken-1",
    "flowClassName": "com.r3.developers.csdetemplate.ION.SelfCreateSecurityTokenFlow",
    "requestData": {
        "cusip":"USCA765248",
        "quantity":"25",
        "holder":"CN=deliverer, OU=Test Dept, O=R3, L=London, C=GB",
        "tokenForger":"CN=deliverer, OU=Test Dept, O=R3, L=London, C=GB"
        }
}

RequestBody for triggering the flow via http-rpc:
{
    "clientRequestId": "list-2",
    "flowClassName": "com.r3.developers.csdetemplate.ION.QueryAllStates",
    "requestData": {}
}
*/
/*

Response body
Download
{
  "holdingIdentityShortHash": "007EE8C5F3EC",
  "clientRequestId": "list-3",
  "flowId": "89a12f8e-e0c4-495d-82a0-a5b804bfa2d5",
  "flowStatus": "COMPLETED",
  "flowResult": "
  [[{\"tradeStatus\":\"APPROVED\",\"shrQty\":25,\"securityID\":\"USCA765248\",\"settlementAmt\":15.0,\"deliver\":\"CN=deliverer, OU=Test Dept, O=R3, L=London, C=GB\",\"receiver\":\"CN=receiver, OU=Test Dept, O=R3, L=London, C=GB\",\"settlementDelivererId\":\"delivererIdXXXX\",\"settlementReceiverID\":\"receiverIDXXXX\",\"dtcc\":\"CN=dtcc, OU=Test Dept, O=R3, L=NewYork, C=US\",\"dtccObserver\":\"CN=dtccObserver, OU=Test Dept, O=R3, L=Washington, C=US\",\"setlmntCrncyCd\":\"WhateverThisIs\",\"sttlmentlnsDlvrrRefId\":\"WhateverThisIsToo\",\"linearId\":\"22036b21-746d-42d5-ac35-b86706b45b54\"}],
  [{\"cusip\":\"USCA765248\",\"quantity\":25,\"holder\":\"CN=deliverer, OU=Test Dept, O=R3, L=London, C=GB\",\"tokenForger\":\"CN=deliverer, OU=Test Dept, O=R3, L=London, C=GB\"}]]",
  "flowError": null,
  "timestamp": "2023-02-16T02:41:35.501738Z"
}

*/