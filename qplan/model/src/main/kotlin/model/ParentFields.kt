package model

import viaduct.graphql.schema.ViaductSchema

/** The canonical schema directive that marks an engine-provided parent field. */
const val PARENT_DIRECTIVE_NAME: String = "parent"

/** Whether this canonical field is provided by traversing to the containing object's parent. */
fun ViaductSchema.Field.isParentField(): Boolean =
    hasAppliedDirective(PARENT_DIRECTIVE_NAME)

/**
 * One validated inverse pair between a child object's `@parent` field and its producing field.
 *
 * [parentField] points from the produced child object to its containing parent object.
 * [producerField] is the argument-free field on that parent object whose result contains the child.
 * The producer may return the child through any finite list nesting.
 *
 * Equality is undefined. Both fields are canonical definitions from one schema.
 */
sealed interface ParentFieldRelation {
    val parentField: ViaductSchema.ObjectField
    val producerField: ViaductSchema.ObjectField
}

/**
 * The parent-field relation and its parent-to-child transpose for one canonical schema.
 *
 * Equality is undefined. A parent field has exactly one producer. One producer may be transposed
 * to more than one parent field declared on its child type.
 */
sealed interface ParentFieldRelations {
    fun relation(parentField: ViaductSchema.ObjectField): ParentFieldRelation?

    fun parentFields(producerField: ViaductSchema.ObjectField): Set<ViaductSchema.ObjectField>

    companion object {
        /**
         * Derives and validates the complete relation from [schema].
         *
         * Qplan deliberately restricts a child-producing field paired with `@parent` to have no
         * arguments. Singular, list, and nested-list child outputs are all admitted.
         */
        fun of(schema: ViaductSchema): ParentFieldRelations {
            val objectFields =
                schema.types.values
                    .filterIsInstance<ViaductSchema.Object>()
                    .flatMap { type -> type.fields.filterIsInstance<ViaductSchema.ObjectField>() }
            val relations =
                objectFields
                    .filter(ViaductSchema.Field::isParentField)
                    .associateWith { parentField ->
                        require(parentField.args.isEmpty()) {
                            "Parent field ${parentField.coordinate()} must not have arguments"
                        }
                        require(!parentField.type.isList) {
                            "Parent field ${parentField.coordinate()} must not return a list"
                        }
                        val parentTarget =
                            parentField.type.baseTypeDef as? ViaductSchema.CompositeTypeDef
                                ?: throw IllegalArgumentException(
                                    "Parent field ${parentField.coordinate()} must return a composite type",
                                )
                        val childType = parentField.containingDef
                        val producers =
                            objectFields.filter { candidate ->
                                !candidate.isParentField() &&
                                    candidate.type.baseTypeDef == childType &&
                                    parentTarget.possibleObjectTypes.containsAll(
                                        candidate.containingDef.possibleObjectTypes,
                                    )
                            }
                        require(producers.size == 1) {
                            "Parent field ${parentField.coordinate()} requires exactly one compatible " +
                                "producer for ${childType.name}; found " +
                                producers.joinToString { producer -> producer.coordinate() }
                        }
                        val producer = producers.single()
                        require(producer.args.isEmpty()) {
                            "Parent-field child producer ${producer.coordinate()} must not have arguments"
                        }
                        ParentFieldRelationImpl(parentField, producer)
                    }
            return ParentFieldRelationsImpl(relations)
        }
    }
}

private class ParentFieldRelationImpl(
    override val parentField: ViaductSchema.ObjectField,
    override val producerField: ViaductSchema.ObjectField,
) : ParentFieldRelation

private class ParentFieldRelationsImpl(
    private val byParentField: Map<ViaductSchema.ObjectField, ParentFieldRelation>,
) : ParentFieldRelations {
    private val byProducerField: Map<ViaductSchema.ObjectField, Set<ViaductSchema.ObjectField>> =
        byParentField.values
            .groupBy(ParentFieldRelation::producerField, ParentFieldRelation::parentField)
            .mapValues { (_, fields) -> fields.toSet() }

    override fun relation(parentField: ViaductSchema.ObjectField): ParentFieldRelation? =
        byParentField[parentField]

    override fun parentFields(
        producerField: ViaductSchema.ObjectField,
    ): Set<ViaductSchema.ObjectField> = byProducerField[producerField].orEmpty()
}

private fun ViaductSchema.Field.coordinate(): String = "${containingDef.name}.$name"
