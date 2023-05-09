package net.corda.libs.packaging

import jdk.security.jarsigner.JarSigner
import net.corda.crypto.core.SecureHashImpl
import net.corda.libs.packaging.core.exception.CordappManifestException
import net.corda.libs.packaging.core.exception.DependencyMetadataException
import net.corda.libs.packaging.core.exception.PackagingException
import net.corda.libs.packaging.internal.ZipTweaker
import net.corda.libs.packaging.internal.v2.CpkLoaderV2
import net.corda.libs.packaging.testutils.TestUtils.ALICE
import net.corda.utilities.outputStream
import net.corda.utilities.readAll
import net.corda.v5.crypto.DigestAlgorithmName
import net.corda.v5.crypto.SecureHash
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Disabled
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.nio.file.CopyOption
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.util.Base64
import java.util.jar.JarFile
import java.util.stream.Collectors
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlin.io.path.name

// This is to avoid extracting the CPK archive in every single test case,
// no test case writes anything to the filesystem, nor alters the state of the test class instance;
// this makes it safe to use the same instance for all test cases (test case execution order is irrelevant)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CPKTests {

    private lateinit var testDir: Path

    private lateinit var workflowCPKPath: Path
    private lateinit var processedWorkflowCPKPath: Path
    private lateinit var workflowCPK: Cpk
    private lateinit var cordappJarPath: Path
    private lateinit var referenceExtractionPath: Path
    private lateinit var nonJarFile: Path

    private lateinit var workflowCPKLibraries: Map<String, SecureHash>

    private val cordaDevCertSummaryHash = run {
        val certFactory = CertificateFactory.getInstance("X.509")

        val cordaDevCert = certFactory.generateCertificate(
            this::class.java.classLoader.getResourceAsStream("corda_dev_cpk.cer")
                ?: throw IllegalStateException("corda_dev_cpk.cer not found")
        ) as X509Certificate

        val sha256Name = DigestAlgorithmName.SHA2_256.name
        SecureHashImpl(
            sha256Name,
            run {
                val md = MessageDigest.getInstance(sha256Name)
                md.update(cordaDevCert.subjectX500Principal.name.toByteArray())
                md.digest()
            }
        )
    }

    @BeforeAll
    fun setup(@TempDir junitTestDir: Path) {
        testDir = junitTestDir

        workflowCPKPath = Path.of(URI(System.getProperty("com.r3.corda.packaging.test.workflow.cpk")))
        processedWorkflowCPKPath = testDir.resolve(workflowCPKPath.fileName)
        workflowCPK = Files.newInputStream(workflowCPKPath).use {
            CpkReader.readCpk(it, processedWorkflowCPKPath, workflowCPKPath.toString())
        }
        cordappJarPath = Path.of(URI(System.getProperty("com.r3.corda.packaging.test.workflow.cordapp")))
        nonJarFile = Files.createFile(testDir.resolve("someFile.bin"))
        workflowCPKLibraries = System.getProperty("com.r3.corda.packaging.test.workflow.libs").split(' ')
            .stream().map { jarFilePath ->
                val filePath = Path.of(URI(jarFilePath))
                Path.of(PackagingConstants.CPK_LIB_FOLDER_V2).resolve(filePath.fileName)
                    .toString() to computeSHA256Digest(
                    Files.newInputStream(filePath)
                )
            }.collect(
            Collectors.toUnmodifiableMap({
                // silly hack for Windows - file path uses \ resource path uses /
                it.first.replace('\\', '/')
            }, { it.second })
        )
        workflowCPKLibraries.forEach {
            println("workflowCPKLibraries: ${it.key}|${it.value}")
        }
        referenceExtractionPath = testDir.resolve("unzippedCPK")
        referenceUnzipMethod(workflowCPKPath, referenceExtractionPath)
    }

    companion object {
        private val DUMMY_HASH =
            Base64.getEncoder().encodeToString(SecureHashImpl(DigestAlgorithmName.SHA2_256.name, ByteArray(32)).getBytes())

        /** Unpacks the [zip] to an autogenerated temporary directory, and returns the directory's path. */
        fun referenceUnzipMethod(source: Path, destination: Path) {

            // We retrieve the relative paths of the zip entries.
            val zipEntryNames = ZipFile(source.toFile()).use { zipFile ->
                zipFile.entries().toList().map(ZipEntry::getName)
            }

            // We create a filesystem to copy the zip entries to a temporary directory.
            FileSystems.newFileSystem(source, null).use { fs ->
                zipEntryNames
                    .map(fs::getPath)
                    .filterNot(Path::isDirectory)
                    .forEach { path ->
                        val newDir = destination.resolve(path.toString())
                        Files.createDirectories(newDir.parent)
                        path.copyTo(newDir)
                    }
            }
        }

        private fun computeSHA256Digest(stream: InputStream, buffer: ByteArray = ByteArray(DEFAULT_BUFFER_SIZE)): SecureHash {
            val h = hash(DigestAlgorithmName.SHA2_256) { md ->
                var size = 0
                while (true) {
                    val read = stream.read(buffer)
                    if (read < 0) {
                        println("Stream size: $size")
                        break
                    }
                    size += read
                    md.update(buffer, 0, read)
                }
            }
            println("Hash: $h")
            return h
        }
    }

    private fun tweakCordappJar(destination: Path, cordappJarTweaker: ZipTweaker) =
        cordappJarTweaker.run(Files.newInputStream(workflowCPKPath), Files.newOutputStream(destination))

    private fun tweakDependencyMetadataFile(destination: Path, json: String) {
        val tweaker = object : ZipTweaker() {
            override fun tweakEntry(
                inputStream: ZipInputStream,
                outputStream: ZipOutputStream,
                currentEntry: ZipEntry,
                buffer: ByteArray
            ) =
                if (currentEntry.name == PackagingConstants.CPK_DEPENDENCIES_FILE_ENTRY_V2) {
                    val source = {
                        ByteArrayInputStream(json.toByteArray())
                    }
                    writeZipEntry(outputStream, source, currentEntry.name, buffer, currentEntry.method)
                    AfterTweakAction.DO_NOTHING
                }
                else if (isSignatureFile(currentEntry)) AfterTweakAction.DO_NOTHING
                else AfterTweakAction.WRITE_ORIGINAL_ENTRY
        }

        // Sign the jar
        val notSigned = destination.resolveSibling(destination.name + "-not-signed")
        tweakCordappJar(notSigned, tweaker)
        signJar(notSigned, destination)
    }

    @Test
    fun `Verify hashes of jars in the lib folder of workflow cpk`() {
        for (libraryFileName in workflowCPK.metadata.libraries) {
            val libraryHash = workflowCPK.getResourceAsStream(libraryFileName).use(::computeSHA256Digest)
            Assertions.assertEquals(
                workflowCPKLibraries[libraryFileName], libraryHash,
                "The hash of library dependency '$libraryFileName' of cpk file $workflowCPKPath from CPK.Metadata " +
                    "isn't consistent with the content of the file"
            )
        }
    }

    @Test
    fun `Verify hash of cpk file`() {
        val hash = Files.newInputStream(workflowCPKPath).use { computeSHA256Digest(it) }
        Assertions.assertEquals(
            hash, workflowCPK.metadata.fileChecksum,
            "The cpk hash from CPK.Metadata differs from the actual hash of the .cpk file"
        )
    }

    @Test
    fun `Verify library files are correct`() {
        Assertions.assertEquals(workflowCPKLibraries.size, workflowCPK.metadata.libraries.size)
        for (library in workflowCPK.metadata.libraries) {
            val libraryHash = try {
                println("Resource: $library")
                workflowCPK.getResourceAsStream(library).use(::computeSHA256Digest)
            } catch (e: IOException) {
                Assertions.fail(e)
            }
            Assertions.assertEquals(
                libraryHash, workflowCPKLibraries[library],
                "The hash of library dependency '$library' of cpk file $workflowCPKPath from CPK.libraryUris " +
                    "isn't consistent with the content of the file"
            )
        }
    }

    @Test
    fun `Verify cordapp signature`() {
        Assertions.assertEquals(
            sequenceOf(cordaDevCertSummaryHash).summaryHash(),
            workflowCPK.metadata.cpkId.signerSummaryHash
        )
    }

    @Test
    fun `throws if CorDapp JAR has no manifest`() {
        val modifiedWorkflowCPK = testDir.resolve("tweaked.cpk")
        val tweaker = object : ZipTweaker() {
            override fun tweakEntry(
                inputStream: ZipInputStream,
                outputStream: ZipOutputStream,
                currentEntry: ZipEntry,
                buffer: ByteArray
            ) =
                if (currentEntry.name == JarFile.MANIFEST_NAME) AfterTweakAction.DO_NOTHING
                else AfterTweakAction.WRITE_ORIGINAL_ENTRY
        }
        tweakCordappJar(modifiedWorkflowCPK, tweaker)
        assertThrows<CordappManifestException> {
            Files.newInputStream(modifiedWorkflowCPK).use {
                CpkLoaderV2().loadMetadata(it.readAllBytes(),
                    cpkLocation = modifiedWorkflowCPK.toString(),
                    verifySignature = false
                )
            }
        }
    }

    private fun isSignatureFile(currentEntry: ZipEntry) = currentEntry.name.startsWith("META-INF/")
            && (currentEntry.name.endsWith(".SF")
            || currentEntry.name.endsWith(".RSA")
            || currentEntry.name.endsWith(".EC"))

    private fun signJar(modifiedWorkflowCPK: Path, modifiedWorkflowCPKSigned: Path) {
        ZipFile(modifiedWorkflowCPK.toFile()).use { inputFile ->
            modifiedWorkflowCPKSigned.outputStream().use { outputStream ->
                JarSigner.Builder(ALICE.privateKeyEntry).build().sign(inputFile, outputStream)
            }
        }
    }

    @Test
    fun `does not complain if a CPK dependencies file lists no dependencies`() {
        val modifiedWorkflowCPK = testDir.resolve("tweaked.cpk")
        val json = "{\"formatVersion\":\"2.0\",\"dependencies\":[]}"
        tweakDependencyMetadataFile(modifiedWorkflowCPK, json)
        Assertions.assertDoesNotThrow {
            CpkLoaderV2().loadMetadata(modifiedWorkflowCPK.readAll(),
                cpkLocation = modifiedWorkflowCPK.toString(), verifySignature = false
            )
        }
    }

    @Test
    @Disabled("Need to implement file hash before we can check this is invalid")
    fun `throws if a CPK dependencies file lists a dependency with no signers`() {
        val modifiedWorkflowCPK = testDir.resolve("tweaked.cpk")
        val json = "{\"formatVersion\":\"2.0\",\"dependencies\":[" +
                "{\"name\":\"DUMMY_NAME\",\"version\":\"DUMMY_VERSION\"}]}"
        tweakDependencyMetadataFile(modifiedWorkflowCPK, json)
        assertThrows<DependencyMetadataException> {
            CpkLoaderV2().loadMetadata(modifiedWorkflowCPK.readAll(),
                cpkLocation = modifiedWorkflowCPK.toString(), verifySignature = false
            )
        }
    }

    @Test
    fun `signature verification fails if archive has been tampered with`() {
        val modifiedWorkflowCPK = testDir.resolve("tweaked.cpk")
        val tweaker = object : ZipTweaker() {
            override fun tweakEntry(
                inputStream: ZipInputStream,
                outputStream: ZipOutputStream,
                currentEntry: ZipEntry,
                buffer: ByteArray
            ) =
                when (currentEntry.name) {
                    PackagingConstants.CPK_DEPENDENCIES_FILE_ENTRY_V2 -> {
                        writeZipEntry(
                            outputStream,
                            "modified"::byteInputStream,
                            currentEntry.name,
                            buffer,
                            currentEntry.method
                        )
                        AfterTweakAction.DO_NOTHING
                    }
                    else -> AfterTweakAction.WRITE_ORIGINAL_ENTRY
                }
        }
        tweakCordappJar(modifiedWorkflowCPK, tweaker)

        Assertions.assertThrows(SecurityException::class.java) {
            CpkLoaderV2().loadMetadata(modifiedWorkflowCPK.readAll(), null, verifySignature = true)
        }
    }

    @Test
    fun `throws if archive is not a jar file at all`() {
        assertThrows<PackagingException> {
            CpkLoaderV2().loadMetadata(nonJarFile.readAll(),
                nonJarFile.toString(),
                jarSignatureVerificationEnabledByDefault()
            )
        }
        assertThrows<PackagingException> {
            Files.newInputStream(nonJarFile).use { CpkReader.readCpk(it, processedWorkflowCPKPath, nonJarFile.toString()) }
        }
    }

    @Test
    fun `signers summary hash is computed correctly`() {
        val md = MessageDigest.getInstance(DigestAlgorithmName.SHA2_256.name)
        md.update(cordaDevCertSummaryHash.toString().toByteArray())
        val expectedHash = SecureHashImpl(DigestAlgorithmName.SHA2_256.name, md.digest())
        Assertions.assertEquals(expectedHash, workflowCPK.metadata.cpkId.signerSummaryHash)
    }
}

/** @see Files.copy */
fun Path.copyTo(target: Path, vararg options: CopyOption): Path = Files.copy(this, target, *options)

/** @see Files.isDirectory */
fun Path.isDirectory(vararg options: LinkOption): Boolean = Files.isDirectory(this, *options)
