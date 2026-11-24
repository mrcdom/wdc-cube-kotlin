package br.com.wdc.store.view.react.supports

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Finds the work directory the service was started with.
 *
 * It is **mandatory** and comes from outside: the `--workdir=<folder>` argument or, without it, the
 * `STORE_WORKDIR` environment variable. There is no default: starting the service without saying where it
 * works is an error, not an invitation to create a folder wherever the process happens to be.
 */
object WorkDirectory {

    const val ARGUMENT = "--workdir="
    const val ENVIRONMENT_VARIABLE = "STORE_WORKDIR"

    /**
     * @throws IllegalArgumentException if the directory was not given, or does not exist
     */
    fun resolve(args: Array<String>, environment: (String) -> String? = System::getenv): Path {
        val informed = args.firstOrNull { it.startsWith(ARGUMENT) }?.substring(ARGUMENT.length)?.trim()?.takeIf { it.isNotEmpty() }
            ?: environment(ENVIRONMENT_VARIABLE)?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException(
                "Work directory not given. Use the $ARGUMENT<folder> argument or the environment variable " +
                    "$ENVIRONMENT_VARIABLE. It is the folder with config/, data/, log/, tmp/ and deployment/ " +
                    "(in development: fontes/work)."
            )
        val path = Paths.get(informed).toAbsolutePath().normalize()
        require(Files.isDirectory(path)) { "The given work directory does not exist: $path" }
        return path
    }

    /** The arguments other than the work directory one. */
    fun otherArguments(args: Array<String>): List<String> = args.filterNot { it.startsWith(ARGUMENT) }
}
