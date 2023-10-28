package xyz.znix.xftl.modding

import net.vhati.modmanager.core.ModUtilities
import xyz.znix.xftl.VanillaDatafile
import java.io.ByteArrayInputStream
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.util.*
import java.util.logging.Logger
import java.util.zip.ZipFile


/**
 * Dynamically applies patches from Slipstream mods to files, as they're loaded.
 *
 * A lot of this is copied from net.vhati.modmanager.core.ModPatchThread, which
 * isn't included in xftl as rewriting the stuff we need here is easier.
 */
class SlipstreamPatcher(private val vanilla: VanillaDatafile) {
    private val log: Logger = Logger.getLogger(javaClass.name)

    val files = HashMap<String, FileSource>()

    /**
     * Files should be case-insensitive (using the English locale, since that's
     * what almost all mods will be tested against), this maps the lower-case
     * name of a file to the one that's first been seen.
     */
    private val toOriginalCase = HashMap<String, String>()

    init {
        // Register all the files from vanilla
        for (file in vanilla.getAllFiles()) {
            // Use checkCase to populate the toOriginalCase map
            files[checkCase(file.name)] = VanillaFileSource(vanilla, file)
        }
    }

    fun patch(mods: List<SlipstreamMod>) {
        if (mods.isEmpty()) {
            log.info("No mods installed.")
            return
        }

        log.info("Loading mods: ${mods.joinToString(", ") { it.name }}")

        // Track modified innerPaths in case they're clobbered.
        val moddedItems: MutableList<String> = ArrayList()

        for (mod in mods) {
            log.info("Installing mod: ${mod.name}")

            for (filePath in mod.entries) {
                if (isJunkFile(filePath))
                    continue
                processRegularEntry(mod, filePath, moddedItems)
            }
        }

        //log.info( "Adding XSL Transforms to temporary database..." );
        val stylesheets = HashMap<String, ByteArray>()
        for (mod in mods) {
            log.info("Applying XSLT transforms of mod: ${mod.name}")

            // First, read in all the stylesheets, as they can be used
            // as libraries by each other.
            // It's not ideal to read stuff in now - it's better to load
            // it on-demand - but the XSLT files shouldn't be very big, and
            // it'd be a pretty invasive change to load the libraries on-demand.
            for (filePath in mod.entries) {
                if (isJunkFile(filePath))
                    continue

                if (!filePath.endsWith(".xsl")) {
                    continue
                }

                val innerPath = checkCase(filePath)
                if (stylesheets.containsKey(filePath)) {
                    log.warning(String.format("Clobbering earlier stylesheet: %s", innerPath))
                }
                stylesheets[filePath] = mod.openFile(filePath).use { it.readAllBytes() }
                log.info("Added stylesheet to temporary database: $innerPath")
                if (!moddedItems.contains(innerPath)) {
                    moddedItems.add(innerPath)
                }
                // copied from below
            }

            // same basic form as above
            var noTransformsYet = true
            for (filePath in mod.entries) {
                if (isJunkFile(filePath))
                    continue

                if (!filePath.endsWith(".xsl")) {
                    continue
                }

                if (noTransformsYet) {
                    noTransformsYet = false
                    log.info("Applying XSL Transforms...")
                }

                var innerPath = filePath.removeSuffix(".xsl") + ".xml"
                innerPath = checkCase(innerPath)
                val oldFileSource = files[innerPath]
                if (oldFileSource == null) {
                    val padding = String(CharArray(23)).replace("\u0000", " ")
                    log.warning(
                        String.format(
                            "Could not find base file: %s\n%sAssuming %s is an XSL library",
                            innerPath,
                            padding,
                            filePath
                        )
                    )
                    continue
                }

                log.info(String.format("Creating transform mapping for file: %s", innerPath))
                files[innerPath] = object : FileSource {
                    override fun open(): InputStream {
                        oldFileSource.open().use { mainStream ->
                            mod.openFile(filePath).use { append ->
                                return ModUtilities.transformXMLFile(
                                    mainStream,
                                    append,
                                    "UTF-8",
                                    innerPath, // What mod did this file come from?
                                    mod.name + ":" + filePath,
                                    stylesheets
                                )
                            }
                        }
                    }
                }
                if (!moddedItems.contains(innerPath)) {
                    moddedItems.add(innerPath)
                }
            }

            if (noTransformsYet) {
                log.info("Mod had no transforms, nothing applied")
            }
        }
    }

    /**
     * Process a non-XSLT entry.
     */
    private fun processRegularEntry(
        mod: SlipstreamMod,
        filePath: String,
        moddedItems: MutableList<String>
    ) {
        var innerPath = filePath

        if (filePath.endsWith(".xml.append") || filePath.endsWith(".append.xml")) {
            innerPath = filePath.replace("[.](?:xml[.]append|append[.]xml)$".toRegex(), ".xml")
            innerPath = checkCase(innerPath)

            val previous = files[innerPath]

            if (previous == null) {
                log.warning(String.format("Non-existent innerPath wasn't appended: %s", innerPath))
                return
            }

            files[innerPath] = object : FileSource {
                override fun open(): InputStream {
                    // Don't abort if there's something wrong with the XML, keep going.
                    val globalPanic = false

                    return previous.open().use { original ->
                        mod.openFile(filePath).use { append ->
                            ModUtilities.patchXMLFile(
                                original,
                                append,
                                "UTF-8",
                                globalPanic,
                                innerPath, // What mod did this file come from?
                                mod.name + ":" + filePath
                            )
                        }
                    }
                }
            }

            if (!moddedItems.contains(innerPath)) {
                moddedItems.add(innerPath)
            }
            return
        }
        if (filePath.endsWith(".xml.rawappend") || filePath.endsWith(".rawappend.xml")) {
            innerPath = filePath.replace(
                "[.](?:xml[.]rawappend|rawappend[.]xml)$".toRegex(),
                ".xml"
            )
            innerPath = checkCase(innerPath)
            val original = files[innerPath]
            if (original == null) {
                log.warning(String.format("Non-existent innerPath wasn't raw appended: %s", innerPath))
                return
            }

            log.warning(String.format("Appending xml as raw text: %s", innerPath))
            files[innerPath] = object : FileSource {
                override fun open(): InputStream {
                    original.open().use { mainStream ->
                        mod.openFile(filePath).use { append ->
                            return ModUtilities.appendXMLFile(
                                mainStream,
                                append,
                                "UTF-8",
                                innerPath, // What mod did this file come from?
                                mod.name + ":" + filePath
                            )
                        }
                    }
                }
            }

            if (!moddedItems.contains(innerPath)) {
                moddedItems.add(innerPath)
            }
            return
        }
        if (filePath.endsWith(".xml.rawclobber") || filePath.endsWith(".rawclobber.xml")) {
            innerPath = filePath.replace(
                "[.](?:xml[.]rawclobber|rawclobber[.]xml)$".toRegex(),
                ".xml"
            )
            innerPath = checkCase(innerPath)
            log.warning(String.format("Copying xml as raw text: %s", innerPath))

            // Slipstream fiddles around with the line endings here,
            // we don't care as our parsers can tolerate either line ending.
            files[innerPath] = ModFileSource(filePath, mod)

            if (!moddedItems.contains(innerPath)) {
                moddedItems.add(innerPath)
            } else {
                log.warning(String.format("Clobbering earlier mods: %s", innerPath))
            }
            return
        }
        if (filePath.endsWith(".xml")) {
            innerPath = checkCase(innerPath)

            files[innerPath] = object : FileSource {
                override fun open(): InputStream {
                    val baseStream = mod.openFile(filePath)
                    return ModUtilities.rebuildXMLFile(
                        baseStream, "UTF-8",
                        mod.name + ":" + filePath,
                        true
                    )
                }
            }

            if (!moddedItems.contains(innerPath)) {
                moddedItems.add(innerPath)
            } else {
                log.warning(String.format("Clobbering earlier mods: %s", innerPath))
            }
            return
        }
        if (filePath.endsWith(".txt")) {
            innerPath = checkCase(innerPath)

            // Don't care about newlines, our parsers can handle both
            files[innerPath] = ModFileSource(filePath, mod)

            if (!moddedItems.contains(innerPath)) {
                moddedItems.add(innerPath)
            } else {
                log.warning(String.format("Clobbering earlier mods: %s", innerPath))
            }
            return
        }

        innerPath = checkCase(innerPath)
        if (!moddedItems.contains(innerPath)) {
            moddedItems.add(innerPath)
        } else {
            log.warning(String.format("Clobbering earlier mods: %s", innerPath))
        }

        files[innerPath] = ModFileSource(filePath, mod)
    }


    /**
     * Checks if an innerPath exists, ignoring letter case.
     *
     * If there is no collision, the innerPath is added to the known lists.
     * A warning will be logged if a path with differing case exists.
     *
     * @return the existing path (if different), or innerPath
     */
    private fun checkCase(innerPath: String): String {
        // Use the original case, if we're replacing a file
        val lower = innerPath.lowercase(Locale.UK)
        var originalCase = toOriginalCase[lower]

        if (originalCase != null && innerPath != originalCase) {
            log.warning("Modded file's case doesn't match existing path: \"$innerPath\" vs \"$originalCase\"")
        }

        // If we haven't seen this filename before, register it
        if (originalCase == null) {
            originalCase = innerPath
            toOriginalCase[lower] = innerPath
        }

        return originalCase
    }

    private fun isJunkFile(filePath: String): Boolean {
        // Silently discard anything in a __MACOSX directory, as there
        // can be lots and lots of files there.
        if (filePath.contains("__MACOSX/")) {
            return true
        }

        if (ModUtilities.isJunkFile(filePath)) {
            log.warning(String.format("Skipping junk file: %s", filePath))
            return true
        }

        return false
    }
}

/**
 * Represents a mod applied via the Slipstream patching process.
 *
 * This doesn't have to be a real Slipstream .ftl file: it could be a directory
 * of an un-zipped mod during development, or it could be assets taken from a
 * Java-based mod.
 */
interface SlipstreamMod {
    val name: String

    /**
     * The file paths of all the entries inside the mod.
     */
    val entries: List<String>

    fun openFile(name: String): InputStream
}

/**
 * Represents some way of obtaining a file - either loading it directly from
 * the FTL assets, from a mod, or some combination thereof (via patching it).
 */
interface FileSource {
    fun open(): InputStream
}

class VanillaFileSource(val df: VanillaDatafile, val file: VanillaDatafile.Entry) : FileSource {
    override fun open(): InputStream {
        // TODO open and return a stream. This mostly matters for images.
        val bytes = df.read(file)
        return ByteArrayInputStream(bytes)
    }
}

class SlipstreamZipMod(val file: File) : SlipstreamMod, Closeable {
    override val name: String get() = file.name
    override val entries: List<String>

    private val zip = ZipFile(file)

    init {
        entries = zip.stream()
            .filter { !it.isDirectory }
            .map { it.name }
            // Non-standard zips, this is copied over from Slipstream.
            // I'm not entirely sure whether this is still needed, but there's
            // no harm in keeping it.
            .map { it.replace('\\', '/') }
            .toList()
    }

    override fun openFile(name: String): InputStream {
        val entry = zip.getEntry(name) ?: error("Missing entry '$name' for slipstream mod '$file'")
        return zip.getInputStream(entry)
    }

    override fun close() {
        zip.close()
    }
}

class ModFileSource(val path: String, val mod: SlipstreamMod) : FileSource {
    override fun open(): InputStream {
        return mod.openFile(path)
    }
}
