package net.corda.membership.impl.registration.dummy

import net.corda.crypto.client.CryptoOpsClient
import net.corda.crypto.core.CryptoConsts.Categories.PRE_AUTH
import net.corda.crypto.ecies.EciesParams
import net.corda.crypto.ecies.EciesParamsProvider
import net.corda.crypto.ecies.EncryptedDataWithKey
import net.corda.crypto.ecies.EphemeralKeyPairEncryptor
import net.corda.v5.crypto.ECDSA_SECP256R1_CODE_NAME
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.propertytypes.ServiceRanking
import java.security.PublicKey

interface TestEphemeralKeyPairEncryptor : EphemeralKeyPairEncryptor

@ServiceRanking(Int.MAX_VALUE)
@Component(service = [EphemeralKeyPairEncryptor::class, TestEphemeralKeyPairEncryptor::class])
class TestEphemeralKeyPairEncryptorImpl @Activate constructor(
    @Reference(service = CryptoOpsClient::class)
    private val cryptoOpsClient: CryptoOpsClient
) : TestEphemeralKeyPairEncryptor {
    override fun encrypt(
        otherPublicKey: PublicKey,
        plainText: ByteArray,
        params: EciesParamsProvider
    ): EncryptedDataWithKey {
        val ephemeralKey = cryptoOpsClient.generateKeyPair(
            "tenantId",
            PRE_AUTH,
            "alias",
            ECDSA_SECP256R1_CODE_NAME
        )
        val eciesParamsProvider = params.get(ephemeralKey, otherPublicKey)
        return EncryptedDataWithKey(
            ephemeralKey,
            plainText,
            EciesParams(eciesParamsProvider.salt, eciesParamsProvider.aad)
        )
    }
}