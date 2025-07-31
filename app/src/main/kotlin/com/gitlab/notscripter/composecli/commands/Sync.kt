package com.gitlab.notscripter.composecli.commands

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextStyles.*
import com.gitlab.notscripter.composecli.compose.shln

class Sync : SuspendingCliktCommand() {
    override fun help(context: Context) = "🔄  Gradle sync, minus the progress bar pain"

    override suspend fun run() {
        shln("./gradlew --refresh-dependencies", "Syncing...")
    }
}
