package net.corda.ledger.utxo.impl

import net.corda.ledger.utxo.impl.transaction.factory.UtxoTransactionBuilderFactory
import net.corda.v5.base.annotations.Suspendable
import net.corda.v5.ledger.utxo.ContractState
import net.corda.v5.ledger.utxo.StateAndRef
import net.corda.v5.ledger.utxo.StateRef
import net.corda.v5.ledger.utxo.UtxoLedgerService
import net.corda.v5.ledger.utxo.transaction.UtxoTransactionBuilder
import net.corda.v5.serialization.SingletonSerializeAsToken
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ServiceScope

@Component(service = [UtxoLedgerService::class, SingletonSerializeAsToken::class], scope = ServiceScope.PROTOTYPE)
class UtxoLedgerServiceImpl @Activate constructor(
    @Reference(service = UtxoTransactionBuilderFactory::class)
    private val utxoTransactionBuilderFactory: UtxoTransactionBuilderFactory
) : UtxoLedgerService, SingletonSerializeAsToken {

    @Suspendable
    override fun getTransactionBuilder(): UtxoTransactionBuilder {
        return utxoTransactionBuilderFactory.create()
    }

    override fun <T : ContractState> resolve(stateRefs: Iterable<StateRef>): List<StateAndRef<T>> {
        TODO("Not yet implemented")
    }

    override fun <T : ContractState> resolve(stateRef: StateRef): StateAndRef<T> {
        TODO("Not yet implemented")
    }

    override fun <T : ContractState> verify(stateAndRefs: Iterable<StateAndRef<T>>) {
        TODO("Not yet implemented")
    }

    override fun <T : ContractState> verify(stateAndRef: StateAndRef<T>) {
        TODO("Not yet implemented")
    }
}
