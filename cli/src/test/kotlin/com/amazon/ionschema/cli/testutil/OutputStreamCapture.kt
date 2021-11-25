package com.amazon.ionschema.cli.testutil

import java.io.ByteArrayOutputStream
import java.io.OutputStream
import java.io.PrintStream
import java.util.Objects

/**
 * Captures StdOut so that tests can make assertions about the output.
 *
 */
fun <T> captureStdOut(fn: OutputStreamCapture.() -> T) {
    val out = System.out
    val wrappedOut = OutputStreamCapture(out)
    try {
        System.setOut(PrintStream(wrappedOut, true))
        wrappedOut.fn()
    } finally {
        System.setOut(out)
    }
}

/**
 * Wraps an OutputStream, passing on any output but also recording it so it can be inspected later.
 */
class OutputStreamCapture(private val wrapped: OutputStream) : OutputStream() {
    private val capture = ByteArrayOutputStream()

    override fun write(b: Int) {
        capture.write(b)
        wrapped.write(b)
    }

    val output: String
        get() = capture.toString()

    override fun write(b: ByteArray) {
        write(b, 0, b.size)
    }

    override fun write(b: ByteArray, off: Int, len: Int) {
        Objects.checkFromIndexSize(off, len, b.size)
        // len == 0 condition implicitly handled by loop bounds
        for (i in 0 until len) {
            write(b[off + i].toInt())
        }
    }

    override fun close() {
        capture.close()
        wrapped.close()
    }

    override fun flush() {
        capture.flush()
        wrapped.flush()
    }
}
