package xyz.znix.xftl.devmenu

import xyz.znix.xftl.sys.PlatformSpecific
import java.nio.file.Files
import java.nio.file.Path
import java.util.stream.Collectors

/**
 * Reads and writes the Slipstream mod set - the mods folder and its
 * order.txt, which lists the enabled mods in load order.
 */
object DevMods {
    val modDirectory: Path get() = PlatformSpecific.INSTANCE.modsDirectory

    private val orderFile: Path get() = modDirectory.resolve("order.txt")

    /** Every mod file or folder present in the mods directory. */
    fun availableMods(): List<String> {
        if (!Files.isDirectory(modDirectory))
            return emptyList()
        return Files.list(modDirectory).use { stream ->
            stream.map { it.fileName.toString() }
                .filter { it != "order.txt" }
                .filter {
                    it.endsWith(".ftl") || it.endsWith(".zip") ||
                        Files.isDirectory(modDirectory.resolve(it))
                }
                .sorted()
                .collect(Collectors.toList())
        }
    }

    /** The enabled mods, in load order, as listed in order.txt. */
    fun loadOrder(): List<String> {
        if (!Files.isRegularFile(orderFile))
            return emptyList()
        return Files.readAllLines(orderFile)
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("//") }
    }

    /** Write the enabled mods, in order, to order.txt. */
    fun saveOrder(enabled: List<String>) {
        Files.createDirectories(modDirectory)
        val lines = ArrayList<String>()
        lines += "// Tachyon mod order - one mod file per line, top loads first."
        lines += enabled
        Files.write(orderFile, lines)
    }
}
