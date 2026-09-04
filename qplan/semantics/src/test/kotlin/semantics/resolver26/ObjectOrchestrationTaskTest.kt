package semantics.resolver26

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import model.ObjectEngineResult
import model.fragmentFrom
import model.objectOf
import model.requireObjectField
import model.requireQueryTypeDef
import model.requireType
import model.selectionForestOf
import model.testing.TestWorld
import semantics.shared.OperationContext
import viaduct.graphql.schema.ViaductSchema
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ObjectOrchestrationTaskTest {
    @Test
    fun `later parent demand propagates past an existing parent cell`() =
        runBlocking(resolver26CoroutineContext()) {
            coroutineScope {
                val world =
                    TestWorld
                        .fromSDL(
                            selectiveResolvers = true,
                            schemaSDL =
                                """
                                directive @parent on FIELD_DEFINITION
                                type Query { organization: Organization }
                                type Organization { title: String, company: Company }
                                type Company { parent: Organization @parent, name: String, user: User }
                                type User { parent: Company @parent }
                                """.trimIndent(),
                        ).assumptions
                val baseOperation = OperationContext(world)
                val operation =
                    Resolver26OperationContext(
                        base = baseOperation,
                        requestScope = this,
                        resolverObserver =
                            baseOperation.resolverObserver.withResolver26Applications {},
                    )
                val root =
                    ObjectEngineResult.of(
                        world.schema.requireQueryTypeDef(),
                        mutable = true,
                    )
                val organization =
                    ObjectEngineResult.of(
                        world.schema.requireType("Organization") as ViaductSchema.Object,
                        mutable = true,
                    )
                val company =
                    ObjectEngineResult.of(
                        world.schema.requireType("Company") as ViaductSchema.Object,
                        mutable = true,
                    )
                val user =
                    ObjectEngineResult.of(
                        world.schema.requireType("User") as ViaductSchema.Object,
                        mutable = true,
                    )
                val organizationOccurrence =
                    OEROccurrenceContext(
                        root = root,
                        path =
                            listOf(
                                ObjectEngineResult.GroundKey.of(
                                    world.schema.requireObjectField("Query", "organization"),
                                    emptyMap(),
                                ),
                            ),
                        target = organization,
                    )
                val companyOccurrence =
                    OEROccurrenceContext(
                        root = root,
                        path =
                            organizationOccurrence.coordinate(
                                ObjectEngineResult.GroundKey.of(
                                    world.schema.requireObjectField("Organization", "company"),
                                    emptyMap(),
                                ),
                            ),
                        target = company,
                        parent = organizationOccurrence,
                    )
                val userOccurrence =
                    OEROccurrenceContext(
                        root = root,
                        path =
                            companyOccurrence.coordinate(
                                ObjectEngineResult.GroundKey.of(
                                    world.schema.requireObjectField("Company", "user"),
                                    emptyMap(),
                                ),
                            ),
                        target = user,
                        parent = companyOccurrence,
                    )
                val organizationTask =
                    ObjectOrchestrationTask(
                        operation = operation,
                        occurrence = organizationOccurrence,
                        source =
                            world.schema.objectOf("Organization") {
                                "title" setTo "Engineering"
                            },
                        initialDemand = selectionForestOf(),
                    )
                val companyTask =
                    ObjectOrchestrationTask(
                        operation = operation,
                        occurrence = companyOccurrence,
                        source = world.schema.objectOf("Company") { "name" setTo "Airbnb" },
                        initialDemand = selectionForestOf(),
                    )
                val userTask =
                    ObjectOrchestrationTask(
                        operation = operation,
                        occurrence = userOccurrence,
                        source = world.schema.objectOf("User"),
                        initialDemand =
                            world.schema
                                .fragmentFrom("fragment ignored on User { parent { name } }")
                                .subselections,
                    )

                organizationTask.prepare()
                companyTask.prepare()
                userTask.prepare()

                assertTrue(
                    user.isCellSet(
                        ObjectEngineResult.ParentKey.of(
                            world.schema.requireObjectField("User", "parent"),
                        ),
                    ),
                )
                assertFalse(
                    organization.isCellSet(
                        ObjectEngineResult.GroundKey.of(
                            world.schema.requireObjectField("Organization", "title"),
                            emptyMap(),
                        ),
                    ),
                )

                userTask.addDemand(
                    world.schema
                        .fragmentFrom(
                            "fragment ignored on User { parent { parent { title } } }",
                        ).subselections,
                )

                val companyParent =
                    company
                        .getCell(
                            ObjectEngineResult.ParentKey.of(
                                world.schema.requireObjectField("Company", "parent"),
                            ),
                        ).getValue().get()
                assertSame(organization, companyParent)
                assertEquals(
                    "Engineering",
                    organization
                        .getCell(
                            ObjectEngineResult.GroundKey.of(
                                world.schema.requireObjectField("Organization", "title"),
                                emptyMap(),
                            ),
                        ).getValue().get(),
                )
            }
        }

    @Test
    fun `object orchestration validates source and target types at construction`() =
        runBlocking(resolver26CoroutineContext()) {
            coroutineScope {
                val world =
                    TestWorld
                        .fromSDL(
                            """
                            type Query {
                              item: Item
                            }

                            type Item {
                              value: Int
                            }
                            """.trimIndent(),
                        ).assumptions
                val baseOperation = OperationContext(world)
                val operation =
                    Resolver26OperationContext(
                        base = baseOperation,
                        requestScope = this,
                        resolverObserver =
                            baseOperation.resolverObserver.withResolver26Applications {},
                    )
                val root =
                    ObjectEngineResult.of(
                        world.schema.requireQueryTypeDef(),
                        mutable = true,
                    )
                val target =
                    ObjectEngineResult.of(
                        world.schema.requireType("Item") as ViaductSchema.Object,
                        mutable = true,
                    )

                assertFailsWith<IllegalArgumentException> {
                    ObjectOrchestrationTask(
                        operation = operation,
                        occurrence =
                            OEROccurrenceContext(
                                root = root,
                                path =
                                    listOf(
                                        ObjectEngineResult.GroundKey.of(
                                            world.schema.requireObjectField("Query", "item"),
                                            emptyMap(),
                                        ),
                                    ),
                                target = target,
                            ),
                        source = world.resolverRegistry.createRootQueryInput(),
                        initialDemand = selectionForestOf(),
                    )
                }
            }
        }
}
