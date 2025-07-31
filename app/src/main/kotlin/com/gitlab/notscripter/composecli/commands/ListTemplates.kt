package com.gitlab.notscripter.composecli

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextStyles.*
import com.gitlab.notscripter.composecli.compose.listTemplates
import com.gitlab.notscripter.composecli.compose.t

class ListTemplates : SuspendingCliktCommand() {
    override fun help(context: Context) =
        "  Peek at available project templates (starter kits for devs)"

    override suspend fun run() {
        val templates = listTemplates()

        t.println("Available templates:")
        templates.forEach { t.println(it) }
    }
}
