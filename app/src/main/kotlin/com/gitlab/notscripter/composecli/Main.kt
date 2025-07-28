package com.gitlab.notscripter.composecli

import com.github.ajalt.clikt.command.SuspendingCliktCommand
import com.github.ajalt.clikt.command.main
import com.github.ajalt.clikt.core.Context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.prompt
import com.github.ajalt.mordant.animation.coroutines.animateInCoroutine
import com.github.ajalt.mordant.rendering.TextColors.*
import com.github.ajalt.mordant.rendering.TextColors.Companion.rgb
import com.github.ajalt.mordant.rendering.TextStyles.*
import com.github.ajalt.mordant.table.horizontalLayout
import com.github.ajalt.mordant.terminal.Terminal
import com.github.ajalt.mordant.widgets.Spinner
import com.github.ajalt.mordant.widgets.progress.progressBarContextLayout
import kotlinx.coroutines.delay

fun sh(command: String): String =
    Runtime.getRuntime()
        .exec(arrayOf("bash", "-c", command))
        .inputStream
        .bufferedReader()
        .readText()

data class Template(val name: String, val desc: String, val url: String)

class Compose : SuspendingCliktCommand() {
    override fun help(context: Context) = "A tool for compose and compose multiplatform"

    override suspend fun run() = Unit
}

class Init : SuspendingCliktCommand() {
    override fun help(context: Context) = "Create new compose project"

    private val name by option().prompt("Enter name for your project")
    private val id by option().prompt("Enter package id (org.example.myapp)")
    private val kmp by option("-kmp", "--kotlin-multi-platform")

    val t = Terminal()

    override suspend fun run() {

        t.println(brightRed("You can use any of the standard ANSI colors"))

        val style = (bold + black + strikethrough)
        t.println(cyan("You ${(green on white)("can ${style("nest")} styles")} arbitrarily"))

        t.println(rgb("#b4eeb4")("You can also use true color and color spaces like HSL"))
        horizontalLayout {
            cell("Spinner:")
            cell(Spinner.Dots(initial = 2))
        }

        val progress =
            progressBarContextLayout<String> {
                    text { "Status: $context" }
                    progressBar()
                    completed()
                }
                .animateInCoroutine(t, context = "Starting", total = 4, completed = 1)

        launch { progress.execute() }

        val states = listOf("Downloading", "Extracting", "Done")
        for (state in states) {
            delay(2)
            progress.update {
                context = state
                completed += 1
            }
        }

        // val templates =
        //     listOf(
        //         Template("compose-template", "Android with compose",
        // "https://gitlab.com/notscripter/compose-template.git"),
        //     )
        //
        // echo("\nAvailable Templates:\n")
        // templates.forEachIndexed { index, t ->
        //     echo("[${index + 1}] ${t.name.padEnd(10)} - ${t.desc}")
        // }
        //
        // echo("\nChoose a template [1-${templates.size}]: ")
        // val choice = readln().toIntOrNull()
        //
        // val selected = templates.getOrNull((choice ?: -1) - 1)
        // if (selected == null) {
        //     echo("❌ Invalid selection. Exiting.")
        //     return
        // }
        //
        // val home = System.getProperty("user.home")
        // val source = File("$home/.namaste/templates/${selected.name}")
        // val target = File("./$name")
        //
        // echo("⏳ Copying ${selected.name} template to ${target.name} ...")
        // // source.copyRecursively(target, overwrite = true)
        // echo("✅ Done! Project created at ./${target.name}")
        // echo(kmp)
    }
}

suspend fun main(args: Array<String>) = Compose().subcommands(Init()).main(args)
