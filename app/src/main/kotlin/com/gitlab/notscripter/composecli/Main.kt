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

    override suspend fun run() = Unit
}

suspend fun main(args: Array<String>) =
    Compose().subcommands(Init(), Sync(), Run(), ListTemplates(), Launcher()).main(args)
