#!/usr/bin/env kotlin

// Preprocesses async-profiler snapshot to be able to view and compare it in IntelliJ.
//
// Usage:
// kotlinc -script preprocess-snapshot.kts <path-to-output.zip> <path-to-snapshot.txt/zip>
// 
// Reads the snapshot passed as the second argument (either the snapshot in the "collapsed" async-profiler mode,
// or a zip archive containing such snapshot), processes it and writes the output to a zip archive passed as
// the first argument.

import java.io.BufferedReader
import java.io.File
import java.io.FileOutputStream
import java.io.FileReader
import java.util.zip.ZipEntry
import java.util.zip.ZipFile
import java.util.zip.ZipOutputStream

// Keep only stacks containing this frame.
val ANCHOR = "org/jetbrains/kotlin/cli/jvm/compiler/KotlinToJVMBytecodeCompiler.compileModules\$cli;"

// "false" means merge stacks from all threads and remove thread name, "true" means keep as is.
val KEEP_THREADS = false

// Replace Java 8+ lambdas in the stack frames because they have unstable numbers in the name.
// E.g. "Lambda$12345/67890" -> "Lambda".
// Note that this will merge frames with different lambdas which are invoked at the same location!
val LAMBDA_REGEX = "Lambda\\$(\\d+)/(\\d+)".toRegex()
val LAMBDA_REPLACEMENT = "Lambda"

fun main(args: Array<String>) {
    if (args.size != 2) error("Usage: preprocess-snapshot.kts <path-to-output.zip> <path-to-snapshot.txt/zip>")
    val (output, input) = args.map(::File)
    if (input == output) error("Input and output should be different files")
    val newLine = "\n".toByteArray()

    val (fileName, reader) = if (input.extension == "zip") {
        val zipFile = ZipFile(input)
        val entries = zipFile.entries().toList()
        if (entries.size != 1) error("Zip archive contains more than one entry: ${entries.joinToString(limit = 5)}")
        val entry = entries.single()
        entry.name to zipFile.getInputStream(entry).reader()
    } else {
        input.name to FileReader(input)
    }

    BufferedReader(reader).use { br ->
        if (output.exists()) output.delete()
        ZipOutputStream(FileOutputStream(output)).use { out ->
            val e = ZipEntry(fileName)
            out.putNextEntry(e)
            while (true) {
                val line = br.readLine() ?: break
                val anchor = line.indexOf(ANCHOR)
                if (anchor < 0) continue
                if (KEEP_THREADS) {
                    val threadEnd = line.indexOf("];")
                    check(threadEnd > 0) { "No thread in the frame: $line" }
                    out.write(line.substring(0, threadEnd + 2).toByteArray())
                }
                val result = LAMBDA_REGEX.replace(line.substring(anchor), LAMBDA_REPLACEMENT)
                out.write(result.toByteArray())
                out.write(newLine)
            }
            out.closeEntry()
        }
    }
}

main(args)
