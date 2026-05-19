package com.google.research.air.cosmo.fulfillment

/**
 * Restricts `filtered_skill_schema.textproto` to enabled skills (cosmo `generatedActionSchemas`).
 */
object SkillSchemaFilter {
    fun filterSchema(
        fullSchema: String,
        enabledSkillIds: Set<String>
    ): String {
        if (enabledSkillIds.isEmpty()) return fullSchema
        val allowedEnums =
            enabledSkillIds
                .map { skillId -> "ACTION_TYPE_${skillId.uppercase()}" }
                .toSet()
        val enumPattern = Regex("""enum:\s*"(ACTION_TYPE_[A-Z_]+)"""")
        val matches = enumPattern.findAll(fullSchema).map { it.groupValues[1] }.toList()
        if (matches.isEmpty()) return fullSchema
        val filteredEnums = matches.filter { it in allowedEnums }
        if (filteredEnums.size == matches.size) return fullSchema
        var body = fullSchema
        matches.forEach { enumName ->
            if (enumName !in allowedEnums) {
                body = body.replace("""enum: "$enumName" """, "")
            }
        }
        return body
    }
}
