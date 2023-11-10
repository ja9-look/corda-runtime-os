package net.corda.ledger.persistence.utxo.impl

import net.corda.orm.DatabaseTypeProvider
import net.corda.orm.DatabaseTypeProvider.Companion.POSTGRES_TYPE_FILTER
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.slf4j.LoggerFactory

@Suppress("unused")
@Component(service = [ UtxoQueryProvider::class ])
class PostgresUtxoQueryProvider @Activate constructor(
    @Reference(target = POSTGRES_TYPE_FILTER)
    databaseTypeProvider: DatabaseTypeProvider
): AbstractUtxoQueryProvider() {
    init {
        LoggerFactory.getLogger(this::class.java).info("Activated for {}", databaseTypeProvider.databaseType)
    }

    override val persistTransaction: String
        get() = """
            INSERT INTO {h-schema}utxo_transaction(id, privacy_salt, account_id, created, status, updated, metadata_hash)
                VALUES (:id, :privacySalt, :accountId, :createdAt, :status, :updatedAt, :metadataHash)
            ON CONFLICT(id) DO
                UPDATE SET status = EXCLUDED.status, updated = EXCLUDED.updated
            WHERE utxo_transaction.status = EXCLUDED.status OR utxo_transaction.status = '$UNVERIFIED'"""
            .trimIndent()

    // do we want to have an updated column? If you receive different outputs then the data changes
    // is the merkle proof consistent regardless of what components are contained and what is filtered out?
    override val persistFilteredTransaction: String
//        get() = """
//            INSERT INTO {h-schema}utxo_filtered_transaction(id, merkle_proof, created, status, metadata_hash)
//                VALUES (:id, :merkleProof, :createdAt, :status, :metadataHash)
//            ON CONFLICT(id) DO NOTHING"""
//            .trimIndent()
        get() = """
                    INSERT INTO {h-schema}utxo_filtered_transaction(id, merkle_proof, created, metadata_hash)
                        VALUES (:id, :merkleProof, :createdAt, :metadataHash)
                    ON CONFLICT(id) DO NOTHING"""
            .trimIndent()

    override val persistTransactionMetadata: String
        get() = """
            INSERT INTO {h-schema}utxo_transaction_metadata(hash, canonical_data, group_parameters_hash, cpi_file_checksum)
                VALUES (:hash, :canonicalData, :groupParametersHash, :cpiFileChecksum)
            ON CONFLICT DO NOTHING"""
            .trimIndent()

    override val persistTransactionSource: String
        get() = """
            INSERT INTO {h-schema}utxo_transaction_sources(
                transaction_id, group_idx, leaf_idx, source_state_transaction_id, source_state_idx)
            VALUES(
                :transactionId, :groupIndex, :leafIndex, :sourceStateTransactionId, :sourceStateIndex)
            ON CONFLICT DO NOTHING"""
            .trimIndent()

    override val persistTransactionComponentLeaf: String
        get() = """
            INSERT INTO {h-schema}utxo_transaction_component(transaction_id, group_idx, leaf_idx, data, hash)
                VALUES(:transactionId, :groupIndex, :leafIndex, :data, :hash)
            ON CONFLICT DO NOTHING"""
            .trimIndent()

    override fun persistVisibleTransactionOutput(consumed: Boolean): String {
        return """INSERT INTO {h-schema}utxo_visible_transaction_output(
                transaction_id, group_idx, leaf_idx, type, token_type, token_issuer_hash, token_notary_x500_name,
                token_symbol, token_tag, token_owner_hash, token_amount, created, consumed, custom_representation)
            VALUES(
                :transactionId, :groupIndex, :leafIndex, :type, :tokenType, :tokenIssuerHash, :tokenNotaryX500Name,
                :tokenSymbol, :tokenTag, :tokenOwnerHash, :tokenAmount, :createdAt, 
                ${if (consumed) ":consumedAt" else "null"}, 
                CAST(:customRepresentation as JSONB)
            ) ON CONFLICT DO NOTHING"""
            .trimIndent()
    }

    override val persistTransactionSignature: String
        get() = """
            INSERT INTO {h-schema}utxo_transaction_signature(
                transaction_id, signature_idx, signature, pub_key_hash, created)
            VALUES (
                :transactionId, :signatureIdx, :signature, :publicKeyHash, :createdAt)
            ON CONFLICT DO NOTHING"""
            .trimIndent()

    override val persistSignedGroupParameters: String
        get() = """
            INSERT INTO {h-schema}utxo_group_parameters(
                hash, parameters, signature_public_key, signature_content, signature_spec, created)
            VALUES (
                :hash, :parameters, :signature_public_key, :signature_content, :signature_spec, :createdAt)
            ON CONFLICT DO NOTHING"""
            .trimIndent()
}
