package tech.justdev.infrastructure.persistence.jooq

import org.jooq.SQLDialect
import org.jooq.impl.DSL
import tech.justdev.infrastructure.persistence.jooq.Tables.MEMBERS

/** Exercises the first member lookup's SQL construction without opening a database connection. */
fun main() {
    val query =
        DSL
            .using(SQLDialect.POSTGRES)
            .select(MEMBERS.EMAIL, MEMBERS.CREATED_AT)
            .from(MEMBERS)
            .where(MEMBERS.EMAIL.eq("first-member@example.com"))

    check(
        query.sql ==
            "select \"public\".\"members\".\"email\", \"public\".\"members\".\"created_at\" " +
            "from \"public\".\"members\" where \"public\".\"members\".\"email\" = ?",
    ) { "Unexpected member lookup SQL: ${query.sql}" }
    check(query.bindValues == listOf("first-member@example.com")) {
        "Unexpected member lookup parameters: ${query.bindValues}"
    }
    println("jOOQ native smoke test passed")
}
