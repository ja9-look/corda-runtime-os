package net.corda.ledger.utxo.flow.impl.persistence

enum class LedgerPersistenceMetricOperationName {

    FindGroupParameters,
    FindSignedLedgerTransactionWithStatus,
    FindTransactionIdsAndStatuses,
    FindTransactionWithStatus,
    FindWithNamedQuery,
    PersistSignedGroupParametersIfDoNotExist,
    PersistTransaction,
    PersistTransactionIfDoesNotExist,
    ResolveStateRefs,
    UpdateTransactionStatus
}