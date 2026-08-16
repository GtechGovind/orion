package dev.orion.sdk.internal

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class NativeLibraryLoaderTest {

    @Test
    fun packagedHostLibraryIsPresentAndLoadable(): Unit {

        val resourcePath = NativeLibraryLoader.resourcePath()

        NativeLibraryLoader::class.java.getResourceAsStream(resourcePath).use { resource ->
            assertNotNull(resource, "expected native resource $resourcePath")
        }

        NativeLibraryLoader.load()

    }

    @Test
    fun resourcePathsNormalizeCommonPlatformAliases(): Unit {

        assertEquals(
            "/META-INF/orion/native/macos/aarch64/liborion_kotlin.dylib",
            NativeLibraryLoader.resourcePath("Mac OS X", "arm64"),
        )
        assertEquals(
            "/META-INF/orion/native/linux/x86_64/liborion_kotlin.so",
            NativeLibraryLoader.resourcePath("Linux", "amd64"),
        )
        assertEquals(
            "/META-INF/orion/native/windows/x86_64/orion_kotlin.dll",
            NativeLibraryLoader.resourcePath("Windows 11", "x86_64"),
        )

    }

}
