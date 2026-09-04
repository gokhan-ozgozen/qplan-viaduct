package semantics.contract

import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import model.ListEngineResult
import model.ObjectEngineResult
import model.emptyFragmentOf
import model.fragmentFrom
import model.objectOf
import model.operationSelectionsFrom
import model.outputValue
import model.requireObjectField
import model.testing.TestWorld
import model.testing.fieldResolverOf
import org.junit.jupiter.api.Test
import semantics.shared.OperationContext
import viaduct.engine.api.EngineObjectData

/** Contract for engine-provided parent backedges and transitive ancestor demand. */
interface ParentFieldResolverContract : ResolverContract {
    @Test
    fun `parent demand crosses nested-list child fields and reaches grandparents`() {
        val world =
            TestWorld.fromSDL(
                selectiveResolvers = selectiveResolvers,
                schemaSDL =
                    """
                    directive @parent on FIELD_DEFINITION
                    type Query { organization: Organization }
                    type Organization { name: String, company: Company }
                    type Company { parent: Organization @parent, users: [[User]] }
                    type User { parent: Company @parent, organizationName: String }
                    """.trimIndent(),
                fieldResolvers = { schema ->
                    mapOf(
                        schema.requireObjectField("Query", "organization") to
                            fieldResolverOf(schema.emptyFragmentOf("Query")) { _, _ ->
                                schema.objectOf("Organization") { "name" setTo "Engineering" }
                            },
                        schema.requireObjectField("Organization", "company") to
                            fieldResolverOf(schema.emptyFragmentOf("Organization")) { _, _ ->
                                schema.objectOf("Company")
                            },
                        schema.requireObjectField("Company", "users") to
                            fieldResolverOf(schema.emptyFragmentOf("Company")) { _, _ ->
                                listOf(listOf(schema.objectOf("User"), schema.objectOf("User")))
                            },
                        schema.requireObjectField("User", "organizationName") to
                            fieldResolverOf(
                                schema.fragmentFrom(
                                    "fragment ignored on User { parent { parent { name } } }",
                                ),
                            ) { input, _ ->
                                val company = assertIs<EngineObjectData.Sync>(input.outputValue("parent"))
                                val organization =
                                    assertIs<EngineObjectData.Sync>(company.outputValue("parent"))
                                organization.outputValue("name")
                            },
                    )
                },
            ).assumptions
        val selections =
            world.operationSelectionsFrom(
                "query { organization { company { users { organizationName } } } }",
            )
        val result =
            resolve(
                operation = OperationContext(world),
                root = world.objectOf("Query"),
                selections = selections,
            )
        val organization =
            assertIs<ObjectEngineResult>(
                result.getCell(world.schema.contractKey("Query", "organization")).get(),
            )
        val company =
            assertIs<ObjectEngineResult>(
                organization.getCell(world.schema.contractKey("Organization", "company")).get(),
            )
        val outer =
            assertIs<ListEngineResult>(
                company.getCell(world.schema.contractKey("Company", "users")).get(),
            )
        val inner = assertIs<ListEngineResult>(outer.single().get())

        inner.forEach { userCell ->
            val user = assertIs<ObjectEngineResult>(userCell.get())
            assertEquals(
                "Engineering",
                user.getCell(world.schema.contractKey("User", "organizationName")).get(),
            )
            assertSame(
                company,
                user.getCell(world.schema.contractKey("User", "parent")).get(),
            )
        }
        assertSame(
            organization,
            company.getCell(world.schema.contractKey("Company", "parent")).get(),
        )
    }
}
