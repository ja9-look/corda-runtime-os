package net.corda.ledger.utxo.flow.impl.persistence

import net.corda.v5.application.persistence.CordaPersistenceException
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef

/**
 * [UtxoLedgerStateQueryService] allows to find states, [StateRef]s
 */
interface UtxoLedgerStateQueryService {
    /**
     * Resolve [StateRef]s to [StateAndRef]s
     *
     * @param stateRefs The [StateRef]s to be resolved.
     * @return The resolved [StateAndRef]s.
     *
     * @throws CordaPersistenceException if an error happens during resolve operation.
     */
    @Suspendable
    fun resolveStateRefs(stateRefs: Iterable<StateRef>): List<StateAndRef<*>>
}