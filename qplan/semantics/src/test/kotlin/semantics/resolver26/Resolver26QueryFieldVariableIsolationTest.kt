package semantics.resolver26

import model.Arguments
import model.ObjectEngineResult
import model.emptyFragmentOf
import model.fragmentFrom
import model.operationSelectionsFrom
import model.requireObjectField
import model.testing.TestWorld
import model.testing.fieldResolverOf
import model.testing.fromQueryField
import semantics.contract.get
import semantics.contract.selectionValues
import kotlin.test.Test
import kotlin.test.assertEquals

class Resolver26QueryFieldVariableIsolationTest {
    // Original: execution/src/test/kotlin/execution/viaductfeaturetests/FromFieldVariablesFeatureTest.kt:620
    @Test
    fun `from query field -- simple`() {
        val queryFragmentSource =
            "fragment QueryInput on Query { y(b: ${'$'}b), z }"
        val testWorld =
            TestWorld.fromSDL(
                selectiveResolvers = true,
                schemaSDL =
                    """
                    type Query {
                      x: Int
                      y(b: Int): Int
                      z: Int
                    }
                    """.trimIndent(),
                fieldResolvers = { schema ->
                    val queryX = schema.requireObjectField("Query", "x")
                    val queryY = schema.requireObjectField("Query", "y")
                    val queryZ = schema.requireObjectField("Query", "z")
                    mapOf(
                        queryX to
                            fieldResolverOf(
                                objectFragment = schema.emptyFragmentOf("Query"),
                                queryFragment = schema.fragmentFrom(queryFragmentSource),
                            ) { _, queryValue, _ ->
                                (queryValue.selectionValues().getValue("y") as Int) * 5
                            },
                        queryY to
                            fieldResolverOf(schema.emptyFragmentOf("Query")) { _, arguments ->
                                (arguments.fieldValues.getValue("b") as Int) * 3
                            },
                        queryZ to
                            fieldResolverOf(schema.emptyFragmentOf("Query")) { _, _ -> 2 },
                    )
                },
                variableProviders = { schema ->
                    val queryX = schema.requireObjectField("Query", "x")
                    mapOf(
                        Arguments.Variable.of(queryX, "b") to
                            schema.fromQueryField(
                                queryFragmentSource = queryFragmentSource,
                                responsePath = listOf("z"),
                                variableField = queryX,
                            ),
                    )
                },
            )
        val world = testWorld.assumptions
        val result =
            context(world) {
                resolve(world.operationSelectionsFrom("query { x }"))
            }

        assertEquals(
            30,
            result
                .getCell(
                    ObjectEngineResult.GroundKey.of(
                        world.schema.requireObjectField("Query", "x"),
                        emptyMap(),
                    ),
                ).get(),
        )
    }
}
