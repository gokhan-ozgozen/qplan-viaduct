package model

import model.testing.GJSchema
import model.testing.TestWorld
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertSame

class ParentFieldsTest {
    @Test
    fun `parent fields create parent keys and a list-producing transpose`() {
        val assumptions = TestWorld.fromSDL(PARENT_SCHEMA).assumptions
        val schema = assumptions.schema
        val parentField = schema.requireObjectField("Child", "parent")
        val producerField = schema.requireObjectField("Parent", "children")

        val key = ObjectEngineResult.GroundKey.of(parentField, emptyMap())
        val relation = assumptions.parentFieldRelations.relation(parentField)

        assertIs<ObjectEngineResult.ParentKey>(key)
        assertSame(parentField, relation?.parentField)
        assertSame(producerField, relation?.producerField)
        assertEquals(setOf(parentField), assumptions.parentFieldRelations.parentFields(producerField))
    }

    @Test
    fun `parent transpose rejects an argument-bearing child producer`() {
        val schema =
            GJSchema.fromSDL(
                """
                directive @parent on FIELD_DEFINITION
                type Query { parent: Parent }
                type Parent { child(id: ID!): Child }
                type Child { parent: Parent @parent }
                """.trimIndent(),
            )

        assertFailsWith<IllegalArgumentException> {
            ParentFieldRelations.of(schema)
        }
    }

    private companion object {
        val PARENT_SCHEMA =
            """
            directive @parent on FIELD_DEFINITION
            type Query { parent: Parent }
            type Parent { children: [[Child]] }
            type Child { parent: Parent @parent }
            """.trimIndent()
    }
}
