package com.gitlab.notscripter.composecli.commands

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.arguments.optional
import com.github.ajalt.clikt.parameters.options.help
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.optionalValue
import com.github.ajalt.clikt.parameters.options.prompt
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

    private val projectNameOpt by
        option("-n", "--name").prompt().help("App name (e.g., MyComposeApp)").optionalValue()
    private val projectIdOpt by
        option("-p", "--package").prompt().help("Package name (e.g., com.example.app)")
    private val projectPathOpt by
        option("-l", "--location").help("Where to create the app (default: current directory)")
    private val templateNameOpt by
        option("-t", "--template")
            .prompt()
            .help("Template to use (use `compose list-templates` to see all)")

    private val projectNameArg by argument("name").optional()
    private val projectIdArg by argument("package").optional()
    private val projectPathArg by argument("location").optional()
    private val templateNameArg by argument("template").optional()

    override suspend fun run() {
        val projectName: String? = projectNameOpt ?: projectNameArg
        val projectId: String? = projectIdOpt ?: projectIdArg
        val projectPath = projectPathOpt ?: projectPathArg
        val templateName: String? = templateNameOpt ?: templateNameArg

        if (projectName == null) {
            t.println(red("Project name is reqruied"))
            return
        } else if (projectId == null) {
            t.println(red("Project id is reqruied"))
            return
        } else if (templateName == null) {
            t.println(red("Tempate name is reqruied"))
            return
        }

        val templateDir = getTemplateDir(templateName ?: "EmptyActivity")

        if (templateDir == null || !templateDir.exists()) {
            t.println(red("Template not found"))
            return
        }

        val tempDir = Files.createTempDirectory("compose-cli-template").toFile()
        templateDir.copyRecursively(tempDir, overwrite = true)

        val updateTemplateOutput = updateTemplate(templateDir, tempDir, projectName, projectId)
        if (!updateTemplateOutput) return

        val destination = File(projectPath ?: projectName)
        if (destination.exists()) {
            t.println(red("Directory already exists"))
            return
        }

        tempDir.copyRecursively(destination, overwrite = true)
        t.println(green("✔️ Project '${projectName}' created"))
    }
}
