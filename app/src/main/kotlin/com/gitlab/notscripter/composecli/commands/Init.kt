package com.gitlab.notscripter.composecli.commands

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.required
import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextStyles.*
import com.gitlab.notscripter.composecli.compose.getTemplateDir
import com.gitlab.notscripter.composecli.compose.t
import com.gitlab.notscripter.composecli.compose.updateTemplate
import java.io.File
import java.nio.file.Files

class Init : SuspendingCliktCommand() {
    override fun help(context: Context) =
        "🧱  Start a new Compose project (in less time than it takes AS to open)"

    private val projectName by option("-n", "--name").help("Project name").required()
    private val projectId by option("-p", "--package").help("Package Id").required()
    private val projectPath by option("-l", "--location").help("Project path")
    private val templateName by option("-t", "--template").help("Template name")

    override suspend fun run() {
        val templateDir = getTemplateDir(templateName ?: "ComposeTemplate")

        if (!templateDir.exists()) t.println(red("Template not found"))

        val tempDir = Files.createTempDirectory("compose-cli-template").toFile()
        templateDir.copyRecursively(tempDir, overwrite = true)

        updateTemplate(templateDir, tempDir, projectName, projectId)

        val destination = File(projectPath ?: projectName)
        if (destination.exists()) error("Directory already exists")

        tempDir.copyRecursively(destination, overwrite = true)
        t.println(green("✔️ Project '${projectName}' created"))
    }
}
