package dev.orion.sdk.internal

import dev.orion.sdk.AgentErrorCode
import dev.orion.sdk.OrionException
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.PosixFilePermission

internal object NativeLibraryLoader {

    private const val LIBRARY_NAME: String = "orion_kotlin"
    private const val RESOURCE_PREFIX: String = "/META-INF/orion/native"
    private const val SYSTEM_PATH_FALLBACK_PROPERTY: String =
        "dev.orion.sdk.native.allowSystemLibraryPath"

    private val loaded: Unit by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        loadNativeLibrary()
    }

    internal fun load(): Unit = loaded

    internal fun resourcePath(
        operatingSystemName: String = System.getProperty("os.name"),
        architectureName: String = System.getProperty("os.arch"),
    ): String {

        val operatingSystem = normalizedOperatingSystem(operatingSystemName)
        val architecture = normalizedArchitecture(architectureName)
        val fileName = nativeLibraryFileName(operatingSystem)

        return "$RESOURCE_PREFIX/$operatingSystem/$architecture/$fileName"

    }

    private fun loadNativeLibrary(): Unit {

        try {
            loadPackagedLibrary(resourcePath())
        } catch (packagedError: OrionException) {
            if (!java.lang.Boolean.getBoolean(SYSTEM_PATH_FALLBACK_PROPERTY)) {
                throw packagedError
            }

            try {
                System.loadLibrary(LIBRARY_NAME)
            } catch (systemPathError: UnsatisfiedLinkError) {
                systemPathError.addSuppressed(packagedError)
                throw OrionException(
                    message = "failed to load Orion JNI library from its SDK resource or java.library.path",
                    code = AgentErrorCode.CONFIGURATION,
                    cause = systemPathError,
                )
            }
        }

    }

    private fun loadPackagedLibrary(resourcePath: String): Unit {

        val resource = NativeLibraryLoader::class.java.getResourceAsStream(resourcePath)
            ?: throw OrionException(
                message = "Orion JNI resource '$resourcePath' is missing; this SDK artifact does not support " +
                    "${System.getProperty("os.name")}/${System.getProperty("os.arch")}",
                code = AgentErrorCode.CONFIGURATION,
            )

        try {
            resource.use { input ->
                val directory = createSecureTemporaryDirectory()
                directory.toFile().deleteOnExit()
                val library = createSecureLibraryFile(directory, resourcePath.substringAfterLast('/'))

                input.copyToLibrary(library)
                library.toFile().deleteOnExit()
                System.load(library.toAbsolutePath().toString())
            }
        } catch (error: IOException) {
            throw OrionException(
                message = "failed to extract Orion JNI resource '$resourcePath'",
                code = AgentErrorCode.CONFIGURATION,
                cause = error,
            )
        } catch (error: UnsatisfiedLinkError) {
            throw OrionException(
                message = "failed to load Orion JNI resource '$resourcePath'",
                code = AgentErrorCode.CONFIGURATION,
                cause = error,
            )
        } catch (error: SecurityException) {
            throw OrionException(
                message = "access was denied while loading Orion JNI resource '$resourcePath'",
                code = AgentErrorCode.CONFIGURATION,
                cause = error,
            )
        }

    }

    private fun createSecureTemporaryDirectory(): Path {

        val directory = Files.createTempDirectory("orion-kotlin-native-")

        applyPosixPermissions(
            directory,
            setOf(
                PosixFilePermission.OWNER_READ,
                PosixFilePermission.OWNER_WRITE,
                PosixFilePermission.OWNER_EXECUTE,
            ),
        )

        return directory

    }

    private fun createSecureLibraryFile(directory: Path, fileName: String): Path {

        val library = Files.createFile(directory.resolve(fileName))

        applyPosixPermissions(
            library,
            setOf(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE),
        )

        return library

    }

    private fun InputStream.copyToLibrary(destination: Path): Unit {

        Files.newOutputStream(destination, StandardOpenOption.TRUNCATE_EXISTING).use { output ->
            copyTo(output)
        }

    }

    private fun applyPosixPermissions(path: Path, permissions: Set<PosixFilePermission>): Unit {

        try {
            Files.setPosixFilePermissions(path, permissions)
        } catch (_: UnsupportedOperationException) {
            // Randomized JVM temporary paths retain the platform's native access controls.
        }

    }

    private fun normalizedOperatingSystem(name: String): String = when {
        name.contains("mac", ignoreCase = true) -> "macos"
        name.contains("linux", ignoreCase = true) -> "linux"
        name.contains("windows", ignoreCase = true) -> "windows"
        else -> throw OrionException(
            message = "unsupported operating system for Orion JNI: '$name'",
            code = AgentErrorCode.CONFIGURATION,
        )
    }

    private fun normalizedArchitecture(name: String): String = when (name.lowercase()) {
        "aarch64", "arm64" -> "aarch64"
        "amd64", "x86_64", "x64" -> "x86_64"
        else -> throw OrionException(
            message = "unsupported architecture for Orion JNI: '$name'",
            code = AgentErrorCode.CONFIGURATION,
        )
    }

    private fun nativeLibraryFileName(operatingSystem: String): String = when (operatingSystem) {
        "macos" -> "liborion_kotlin.dylib"
        "linux" -> "liborion_kotlin.so"
        "windows" -> "orion_kotlin.dll"
        else -> throw OrionException(
            message = "unsupported operating system for Orion JNI: '$operatingSystem'",
            code = AgentErrorCode.CONFIGURATION,
        )
    }

}
