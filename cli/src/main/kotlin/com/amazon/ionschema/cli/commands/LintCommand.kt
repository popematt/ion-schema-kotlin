/*
 * Copyright 2022 Amazon.com, Inc. or its affiliates. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License").
 * You may not use this file except in compliance with the License.
 * A copy of the License is located at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * or in the "license" file accompanying this file. This file is distributed
 * on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
 * express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.amazon.ionschema.cli.commands

import com.amazon.ion.system.IonSystemBuilder
import com.amazon.ionschema.IonSchemaSystemBuilder
import com.amazon.ionschema.Type
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.core.context
import com.github.ajalt.clikt.core.subcommands
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.*
import com.github.ajalt.clikt.parameters.types.enum
import java.io.File
import kotlin.system.exitProcess

class LintCommand : CliktCommand(
    help = "Run linter rules for a schema",
    epilog = """
        ```
        Example usage:
        ```
    """.trimIndent()
) {

    /**
     * TODO: Move from an enum to Ion configuration files
     *
     * Format:
     * ```ion
     * rule::{
     *   name: SchemaShouldStartWithExplicitVersionMarker,
     *   category: best_practice,
     *   help: ''' ... '''
     *   isl:(
     *     type::{
     *       name: schema,
     *       type: document,
     *       ordered_elements: [
     *         { occurs: required, type: symbol, regex: "^\${'$'}ion_schema_\d+_\d+${'$'}" },
     *         { occurs: range::[0, max], type: ${'$'}any },
     *       ]
     *     }
     *   )
     * }
     * ```
     *
     * 'category' is an indicator of whether the rule is a 'best_practice' or the rule represents an issue
     * that could prevent the schema from behaving as expected ('correctness') or it's just a 'style' choice.
     */
    enum class Rules(val isl: String, val help: String = "") {
        SchemaShouldStartWithExplicitVersionMarker("""
            type::{
              name: schema,
              type: document,
              ordered_elements: [
                { occurs: required, type: symbol, regex: "^\\${'$'}ion_schema_\\d+_\\d+${'$'}" },
                { occurs: range::[0, max], type: ${'$'}any },                              
              ]
            }
        """),
        TypeNamesShouldNotBeEmpty("""
            type::{
              name: named_type,
              annotation: closed::required::[type],
              type: struct,
            }
            
            type::{
              name: schema,
              type: document,
              element: {
                one_of: [
                  { not: named_type },                              
                  { 
                    type: named_type, 
                    fields: { name: { codepoint_length: range::[1, max],},},
                  },
                ]
              }
            }
        """),
        TypeNamesShouldNotHaveWhiteSpace("""
            type::{
              name: named_type,
              annotation: closed::required::[type],
              type: struct,
            }
            
            type::{
              name: schema,
              type: document,
              element: {
                one_of: [
                  { not: named_type },                              
                  { 
                    type: named_type, 
                    fields: { name: { regex: "^\\S*${'$'}" },},
                  },
                ]
              }
            }
        """),
        TypesShouldHaveAtLeastOneConstraint("""
            type::{
              name: named_type,
              type: struct,
              fields: { name: { type: symbol, occurs: required } },
              annotation: closed::required::[type],
            }
            
            type::{
              name: schema,
              type: document,
              element: {
                one_of: [
                  { not: named_type },                              
                  { 
                    type: named_type, 
                    fields: { name: symbol },
                    container_length: range::[2, max],
                  },
                ]
              }
            }
        """),
    }

    init {
        context {
            subcommands(
                // ExplainRuleCommand()
            )
        }
    }

    private val ion = IonSystemBuilder.standard().build()
    val iss = IonSchemaSystemBuilder.standard()
        .withIonSystem(ion)
        .allowTransitiveImports(false)
        .build()

    val schemaFile: String by argument()

    val disabledRules: List<Rules> by option("-d", "--disable").enum<Rules>().multiple()

    override fun run() {

        val rules = Rules.values()
            .filter { it !in disabledRules }
            .map {
                val ruleSchema = iss.newSchema(it.isl)
                it to ruleSchema.getType("schema")!!
            }
            .toList()

        val file = File(schemaFile).canonicalFile

        val files = file.walk()
            .filter { it.isFile }
            .filter { it.path.endsWith(".isl") }
            .toList()

        val hasViolations = files
            .flatMap { f ->
                val schemaDocument = try {
                    iss.ionSystem.loader.load(f)
                } catch (e: Exception) {
                    return@flatMap listOf("${f.absolutePath} : Invalid Ion: $e")
                }
                rules.flatMap { (rule, type) ->
                    val violations = type.validate(schemaDocument)

                    val locations = if (violations.isValid()) emptyList() else listOf("1,1")

                    locations.map { loc ->
                        "${f.absolutePath} $loc : $rule"
                    }.onEach {
                        echo(it, err = true)
                    }
                }
            }
            .any()

        // exitProcess(if (hasViolations) 1 else 0)
    }
}
