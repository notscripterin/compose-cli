package com.gitlab.notscripter.composecli.commands

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextStyles.*
import com.gitlab.notscripter.composecli.compose.getTemplateDir
import com.gitlab.notscripter.composecli.compose.t
import com.gitlab.notscripter.composecli.compose.updateTemplate
import java.io.File
import java.nio.file.Files

class Init : SuspendingCliktCommand() {
    override fun help(context: Context) =
        "  Kickstart a new Jetpack Compose app from your terminal."

    private val projectNameArg by argument().optional()
    private val projectIdArg by argument().optional()
    private val projectPathArg by argument().optional()
    private val templateNameArg by argument().optional()

    private val projectNameOpt by option("-n", "--name").help("App name (e.g., MyComposeApp)")
    private val projectIdOpt by
        option("-p", "--package").help("Package name (e.g., com.example.app)")
    private val projectPathOpt by
        option("-l", "--location").help("Where to create the app (default: current directory)")
    private val templateNameOpt by
        option("-t", "--template").help("Template to use (use `compose list-templates` to see all)")

    override suspend fun run() {
        val projectName = projectNameArg ?: projectNameOpt
        val projectId = projectIdArg ?: projectIdOpt
        val projectPath = projectPathArg ?: projectPathOpt
        val templateName = templateNameArg ?: templateNameOpt

        if (projectName == null) {
            error("project name is reqruied")
        } else if (projectId == null) {
            error("project id is reqruied")
        }

        val templateDir = getTemplateDir(templateName ?: "EmptyActivity")

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
