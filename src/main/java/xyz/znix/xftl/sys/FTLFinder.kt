package xyz.znix.xftl.sys

import java.io.IOException
import java.nio.file.Files
import java.nio.file.Path

/**
 * Searches for FTL installations.
 */
object FTLFinder {
    private val STEAM_LOCATIONS = listOf(
        "C:\\Program Files (x86)\\Steam\\SteamApps\\libraryfolders.vdf",
        "~/.local/share/Steam/steamapps/libraryfolders.vdf",
        // TODO apparently there's a different location for new Linux installs?
        // TODO Mac support
    )

    // TODO epic store

    fun findInstallations(): List<Path> {
        return findSteam()
    }

    fun findRunningInstance(): Path? {
        return PlatformSpecific.INSTANCE.findRunningInstanceDat()
    }

    private fun findSteam(): List<Path> {
        // Steam stores a list of library locations in VDF files
        // Search those to try and find an FTL installation
        val userDir = System.getProperty("user.home")
        val vdfPaths = STEAM_LOCATIONS
            .map { it.replace("~", userDir) }
            .map { Path.of(it) }

        val result = ArrayList<Path>()

        for (vdfPath in vdfPaths) {
            try {
                result.addAll(searchSteamVDF(vdfPath))
            } catch (ex: IOException) {
                println("Exception while searching Steam VDF file for FTL installation: " + ex.localizedMessage)
                ex.printStackTrace()
            }
        }

        return result
    }

    private fun searchSteamVDF(vdf: Path): List<Path> {
        if (!Files.isRegularFile(vdf))
            return emptyList()

        val result = ArrayList<Path>()

        for (line in Files.readAllLines(vdf)) {
            val parts = line.trim().split(' ', '\t').filter { it.isNotBlank() }

            // Try anything starting with path - if it's not a library
            // path that's fine too, we just won't find an ftl.dat file.
            if (parts[0] != "\"path\"")
                continue

            val steamDir = parts.getOrNull(1)?.trim('"')?.let { Path.of(it) } ?: continue
            val ftlDat = steamDir.resolve("steamapps/common/FTL Faster Than Light/data/ftl.dat")
            if (!Files.isRegularFile(ftlDat))
                continue

            result.add(ftlDat)
        }

        return result
    }
}

// Put this here to make testing this a bit more convenient
private fun main() {
    println("TESTING: Searching for FTL installations")
    val result = FTLFinder.findInstallations()
    println(result)
    println("Running instance:")
    println(FTLFinder.findRunningInstance())
}
