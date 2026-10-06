package br.com.wdc.shopping.view.react.supports

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths

/**
 * Descobre o diretório de trabalho com que o serviço foi chamado.
 *
 * Ele é **obrigatório** e vem de fora: o argumento `--workdir=<pasta>` ou, na falta dele, a variável de
 * ambiente `SHOPPING_WORKDIR`. Não há padrão: subir o serviço sem dizer onde ele trabalha é erro, e não um
 * convite a criar uma pasta onde quer que o processo esteja.
 */
object WorkDirectory {

    const val ARGUMENT = "--workdir="
    const val ENVIRONMENT_VARIABLE = "SHOPPING_WORKDIR"

    /**
     * @throws IllegalArgumentException se o diretório não foi informado, ou se não existe
     */
    fun resolve(args: Array<String>, environment: (String) -> String? = System::getenv): Path {
        val informed = args.firstOrNull { it.startsWith(ARGUMENT) }?.substring(ARGUMENT.length)?.trim()?.takeIf { it.isNotEmpty() }
            ?: environment(ENVIRONMENT_VARIABLE)?.trim()?.takeIf { it.isNotEmpty() }
            ?: throw IllegalArgumentException(
                "Diretório de trabalho não informado. Use o argumento $ARGUMENT<pasta> ou a variável de ambiente " +
                    "$ENVIRONMENT_VARIABLE. É a pasta com config/, data/, log/, tmp/ e deployment/ " +
                    "(em desenvolvimento: fontes/work)."
            )
        val path = Paths.get(informed).toAbsolutePath().normalize()
        require(Files.isDirectory(path)) { "O diretório de trabalho informado não existe: $path" }
        return path
    }

    /** Os argumentos que não são o do diretório de trabalho. */
    fun otherArguments(args: Array<String>): List<String> = args.filterNot { it.startsWith(ARGUMENT) }
}
