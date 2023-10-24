package xyz.znix.xftl.sys

import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import org.lwjgl.system.Platform
import org.lwjgl.system.Pointer
import org.lwjgl.system.libffi.FFICIF
import org.lwjgl.system.libffi.LibFFI
import org.lwjgl.system.windows.WindowsLibrary
import xyz.znix.xftl.game.SaveProfile
import java.nio.file.Path

sealed interface PlatformSpecific {
    /**
     * The directory the user's saves should be stored in.
     */
    val saveGamePath: Path

    val saveProfilePath: Path get() = saveGamePath.resolve(SaveProfile.PROFILE_NAME)
    val modsDirectory: Path get() = saveGamePath.resolve("mods")

    companion object {
        @Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
        @JvmField
        val INSTANCE: PlatformSpecific = when (Platform.get()) {
            Platform.WINDOWS -> WindowsPlatform()
            Platform.MACOSX -> MacPlatform()
            Platform.LINUX -> LinuxPlatform()
        }
    }
}

private class WindowsPlatform : PlatformSpecific {
    private val shell32 = WindowsLibrary("Shell32.dll")

    /**
     * The value of the Windows FOLDERID_SavedGames known folder.
     */
    private val userSavedGames: Path

    override val saveGamePath: Path

    init {
        // Call SHGetKnownFolderPath to find the SavedGames folder.
        // If the user has adjusted this path, this respects those changes.
        // Unfortunately, LWJGL doesn't bind this so we have to go through FFI.
        val fnGetKnownFolderPath = shell32.getFunctionAddress("SHGetKnownFolderPath")

        MemoryStack.stackPush().use { mem ->
            // Set up the call
            val cif = FFICIF(mem.malloc(FFICIF.SIZEOF))

            val argTypes = mem.pointers(
                LibFFI.ffi_type_pointer, // rfid
                LibFFI.ffi_type_uint32,  // dwFlags
                LibFFI.ffi_type_pointer, // hToken
                LibFFI.ffi_type_pointer, // ppszPath
            )

            val abi = when {
                Pointer.BITS32 -> LibFFI.FFI_STDCALL
                else -> LibFFI.FFI_DEFAULT_ABI
            }

            val prepResult = LibFFI.ffi_prep_cif(
                cif,
                abi,
                LibFFI.ffi_type_uint32, // Return HRESULT
                argTypes
            )
            check(prepResult == LibFFI.FFI_OK)

            // Make the call

            // FOLDERID_SavedGames
            val guid = mem.malloc(16)
            guid.putInt(0x4c5c32ff)
            guid.putShort(0xbb9d.toShort())
            guid.putShort(0x43b0.toShort())
            guid.put(0xb5.toByte())
            guid.put(0xb4.toByte())
            guid.put(0x2d.toByte())
            guid.put(0x72.toByte())
            guid.put(0xe5.toByte())
            guid.put(0x4e.toByte())
            guid.put(0xaa.toByte())
            guid.put(0xa4.toByte())
            guid.flip()

            val resultBuf = mem.malloc(4)
            val outStrPtr = mem.mallocPointer(1)
            val argsValues = mem.pointers(
                MemoryUtil.memAddress(guid),
                0, // Flags
                0, // 0 = Current user
                MemoryUtil.memAddress(outStrPtr),
            )
            val argsBuf = mem.mallocPointer(argsValues.remaining())
            for (i in 0 until argsValues.remaining()) {
                argsBuf.put(i, MemoryUtil.memAddress(argsValues) + MemoryStack.POINTER_SIZE * i)
            }

            LibFFI.ffi_call(cif, fnGetKnownFolderPath, resultBuf, argsBuf)

            // Handle the result
            val result = resultBuf.getInt()
            if (result != 0) {
                error("Failed to read save directory: HRESULT=$result (decimal)")
            }

            val stringPtr = outStrPtr.get(0)
            val pathString = MemoryUtil.memUTF16(stringPtr)
            userSavedGames = Path.of(pathString)

            // We're supposed to free the string pointer using CoTaskMemFree.
            // Don't bother because it's only a little memory leak, and it'd
            // be mildly inconvenient to do so.
        }

        saveGamePath = userSavedGames.resolve("Project Wormhole")
    }
}

private class MacPlatform : PlatformSpecific {
    // TODO use the proper path
    override val saveGamePath: Path = Path.of("fixme-wormhole-saves")
}

private class LinuxPlatform : PlatformSpecific {
    override val saveGamePath: Path

    init {
        val dataHome = System.getenv("XDG_DATA_HOME")?.let { Path.of(it) }
            ?: Path.of(System.getProperty("user.home")).resolve(".local/share")

        saveGamePath = dataHome.resolve("ProjectWormhole")
    }
}
