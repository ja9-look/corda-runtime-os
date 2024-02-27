package net.corda.cli.plugins.network

import net.corda.cli.plugins.network.utils.PrintUtils.verifyAndPrintError
import net.corda.cli.plugins.network.utils.inferCpiName
import net.corda.cli.plugins.packaging.CreateCpiV2
import net.corda.libs.cpiupload.endpoints.v1.CpiUploadRestResource
import net.corda.sdk.network.MemberRole
import net.corda.sdk.network.RegistrationContext
import net.corda.sdk.packaging.CpiUploader
import net.corda.sdk.rest.RestClientUtils
import net.corda.v5.base.exceptions.CordaRuntimeException
import picocli.CommandLine.Command
import picocli.CommandLine.Option
import java.io.File

@Command(
    name = "onboard-member",
    description = [
        "Onboard a member",
    ],
    mixinStandardHelpOptions = true,
)
class OnboardMember : Runnable, BaseOnboard() {
    private companion object {
        const val CPI_VERSION = "1.0"
    }

    @Option(
        names = ["--cpb-file", "-b"],
        description = [
            "Location of a CPB file. The plugin will generate a CPI signed with default options when a CPB is " +
                "provided. Use either --cpb-file or --cpi-hash.",
        ],
    )
    var cpbFile: File? = null

    @Option(
        names = ["--role", "-r"],
        description = ["Member role, if any. Multiple roles may be specified"],
    )
    var roles: Set<MemberRole> = emptySet()

    @Option(
        names = ["--set", "-s"],
        description = [
            "Pass a custom key-value pair to the command to be included in the registration context. " +
                "Specified as <key>=<value>. Multiple --set arguments may be provided.",
        ],
    )
    var customProperties: Map<String, String> = emptyMap()

    @Option(
        names = ["--group-policy-file", "-gp"],
        description = [
            "Location of a group policy file (default to ~/.corda/gp/groupPolicy.json).",
            "Relevant only if cpb-file is used",
        ],
    )
    var groupPolicyFile: File =
        File(File(File(File(System.getProperty("user.home")), ".corda"), "gp"), "groupPolicy.json")

    @Option(
        names = ["--cpi-hash", "-h"],
        description = ["The CPI hash of a previously uploaded CPI (use either --cpb-file or --cpi-hash)."],
    )
    var cpiHash: String? = null

    @Option(
        names = ["--pre-auth-token", "-a"],
        description = ["Pre-auth token to use for registration."],
    )
    var preAuthToken: String? = null

    @Option(
        names = ["--wait", "-w"],
        description = ["Wait until member gets approved/declined. False, by default."],
    )
    var waitForFinalStatus: Boolean = false

    override val cpiFileChecksum by lazy {
        if (cpiHash != null) {
            return@lazy cpiHash!!
        }
        if (cpbFile?.canRead() != true) {
            throw OnboardException("Please set either CPB file or CPI hash")
        } else {
            uploadCpb(cpbFile!!)
        }
    }

    private val cpisRoot by lazy {
        File(
            File(
                File(
                    System.getProperty(
                        "user.home",
                    ),
                ),
                ".corda",
            ),
            "cached-cpis",
        )
    }

    private fun uploadCpb(cpbFile: File): String {
        val cpiName = inferCpiName(cpbFile, groupPolicyFile)
        val cpiFile = File(cpisRoot, "$cpiName.cpi")
        println("Creating and uploading CPI using CPB '${cpbFile.name}'")

        val restClient = RestClientUtils.createRestClient(
            CpiUploadRestResource::class,
            insecure = insecure,
            minimumServerProtocolVersion = minimumServerProtocolVersion,
            username = username,
            password = password,
            targetUrl = targetUrl
        )
        val cpisFromCluster = CpiUploader().getAllCpis(restClient = restClient).cpis
        cpisFromCluster.firstOrNull { it.id.cpiName == cpiName && it.id.cpiVersion == CPI_VERSION }?.let {
            println("CPI already exists, using CPI ${it.id}")
            return it.cpiFileChecksum
        }
        if (!cpiFile.exists()) {
            val exitCode = createCpi(cpbFile, cpiFile)
            if (exitCode != 0) {
                throw CordaRuntimeException("Create CPI returned non-zero exit code")
            }
            println("CPI file saved as ${cpiFile.absolutePath}")
        }
        uploadSigningCertificates()
        return uploadCpi(cpiFile, cpiFile.name)
    }

    private fun createCpi(cpbFile: File, cpiFile: File): Int {
        println(
            "Using the cpb file is not recommended." +
                " It is advised to create CPI using the package create-cpi command.",
        )
        cpiFile.parentFile.mkdirs()
        val creator = CreateCpiV2()
        creator.cpbFileName = cpbFile.absolutePath
        creator.groupPolicyFileName = groupPolicyFile.absolutePath
        creator.cpiName = cpiFile.nameWithoutExtension
        creator.cpiVersion = CPI_VERSION
        creator.cpiUpgrade = false
        creator.outputFileName = cpiFile.absolutePath
        creator.signingOptions = createDefaultSingingOptions()
        return creator.call()
    }

    private val ledgerKeyId by lazy {
        assignSoftHsmAndGenerateKey("LEDGER")
    }

    private val notaryKeyId by lazy {
        assignSoftHsmAndGenerateKey("NOTARY")
    }

    override val registrationContext by lazy {
        if (roles.contains(MemberRole.NOTARY)) {
            RegistrationContext().getNotary(
                preAuthToken = preAuthToken,
                roles = roles,
                customProperties = customProperties,
                p2pGatewayUrls = p2pGatewayUrls,
                sessionKeyId = sessionKeyId,
                notaryKeyId = notaryKeyId
            )
        } else {
            RegistrationContext().getMember(
                preAuthToken = preAuthToken,
                roles = roles,
                customProperties = customProperties,
                p2pGatewayUrls = p2pGatewayUrls,
                sessionKeyId = sessionKeyId,
                ledgerKeyId = ledgerKeyId
            )
        }
    }

    override fun run() {
        verifyAndPrintError {
            println("Onboarding member '$name'.")

            configureGateway()

            createTlsKeyIdNeeded()

            if (mtls) {
                println(
                    "Using '$certificateSubject' as client certificate. " +
                        "Onboarding will fail until the the subject is added to the MGM's allowed list. " +
                        "See command: 'allowClientCertificate'.",
                )
            }

            setupNetwork()

            println("Provided registration context: ")
            println(registrationContext)

            register(waitForFinalStatus)

            if (waitForFinalStatus) {
                println("Member '$name' was onboarded.")
            } else {
                println(
                    "Registration request has been submitted. Wait for MGM approval for registration to be finalised." +
                        " MGM may need to approve your request manually.",
                )
            }
        }
    }
}
