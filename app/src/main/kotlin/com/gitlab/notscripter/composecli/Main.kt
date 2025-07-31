package com.gitlab.notscripter.composecli

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextStyles.*
import com.gitlab.notscripter.composecli.commands.*

class Compose : SuspendingCliktCommand(name = "compose") {
    override fun help(context: Context) =
        "Compose Cli — for Android devs who live in the terminal  "

    // override fun help(context: Context) =
    //     """
    //     Sick of clicking around Android Studio? Same.
    //
    //     Compose lets you scaffold, run, and manage Jetpack Compose apps from your terminal —
    // fast, clean, and keyboard-only.
    // """
    //         .trimIndent()

    // init {
    //     context {
    //         helpFormatter = CliktHelpFormatter(showDefaultValues = true) { section ->
    //             when (section) {
    //                 HelpFormatter.Section.ARGUMENTS -> section
    //                 HelpFormatter.Section.OPTIONS -> section
    //                 HelpFormatter.Section.COMMANDS -> section
    //                 HelpFormatter.Section.EPILOG -> """
    //                     Because Android dev shouldn't need 16GB RAM just to say "Hello, World."
    // 😎
    //                 """.trimIndent()
    //                 else -> section
    //             }
    //         }
    //     }
    // }

    override suspend fun run() = Unit
}

suspend fun main(args: Array<String>) =
    Compose().subcommands(Init(), Sync(), Run(), ListTemplates(), Launcher()).main(args)
