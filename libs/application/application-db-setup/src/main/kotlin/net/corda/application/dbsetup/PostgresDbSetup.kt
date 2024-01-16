package net.corda.application.dbsetup

import net.corda.db.admin.impl.ClassloaderChangeLog
import net.corda.db.admin.impl.LiquibaseSchemaMigratorImpl
import net.corda.db.core.DbPrivilege
import net.corda.db.core.OSGiDataSourceFactory
import net.corda.db.schema.CordaDb
import net.corda.db.schema.DbSchema
import net.corda.libs.configuration.SmartConfigFactory
import org.slf4j.LoggerFactory

// TODO This class bootstraps database, duplicating functionality available via CLI
// As it duplicates some classes from tools/plugins/initial-config/src/main/kotlin/net/corda/cli/plugin/, it requires
// refactoring, but first we need an input from the DevX team, whether this is the right approach or developers should
// use CLI instead

@Suppress("LongParameterList")
class PostgresDbSetup(
    private val dbUrl: String,
    private val superUser: String,
    private val superUserPassword: String,
    private val dbAdmin: String,
    private val dbAdminPassword: String,
    private val dbName: String,
    private val isDbBusType: Boolean,
    smartConfigFactory: SmartConfigFactory
) : DbSetup {

    // TODO-[CORE-16419]: isolate StateManager database from the Cluster database
    companion object {
        private const val DB_DRIVER = "org.postgresql.Driver"

        // It is mandatory to supply a schema name in this list so the Db migration step can always set a valid schema
        // search path, which is required for the public schema too. For the public schema use "PUBLIC".
        private val changelogFiles: Map<String, String> = mapOf(
            "net/corda/db/schema/config/db.changelog-master.xml" to "config",
            "net/corda/db/schema/messagebus/db.changelog-master.xml" to "messagebus",
            "net/corda/db/schema/rbac/db.changelog-master.xml" to "rbac",
            "net/corda/db/schema/crypto/db.changelog-master.xml" to "crypto",
            "net/corda/db/schema/statemanager/db.changelog-master.xml" to "state_manager",
        )

        private val log = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    private val configEntityFactory = ConfigEntityFactory(smartConfigFactory)

    private val dbAdminUrl by lazy {
        "$dbUrl?user=$dbAdmin&password=$dbAdminPassword"
    }

    private val dbSuperUserUrl by lazy {
        "$dbUrl?user=$superUser&password=$superUserPassword"
    }

    override fun run() {
        if (!dbInitialised()) {
            log.info("Initialising DB.")
            initDb()
            runDbMigration()
            populateConfigDb()
            createUserConfig("admin", "admin")
            createDbUsersAndGrants()
            if (isDbBusType) {
                messageBusConnection().use {
                    DbMessageBusSetup.createTopicsOnDbMessageBus(it)
                }
            }
        } else {
            log.info("Table config.config exists in $dbSuperUserUrl, skipping DB initialisation.")
        }
    }

    private fun populateConfigDb() {
        configConnection().use { connection ->
            connection.createStatement().execute(
                configEntityFactory.createConfiguration(
                    CordaDb.RBAC.persistenceUnitName,
                    "rbac_user_$dbName",
                    "rbac_password",
                    dbUrl,
                    DbPrivilege.DML
                ).toInsertStatement()
            )
            connection.createStatement().execute(
                configEntityFactory.createConfiguration(
                    CordaDb.Crypto.persistenceUnitName,
                    "crypto_user_$dbName",
                    "crypto_password",
                    dbUrl,
                    DbPrivilege.DML
                ).toInsertStatement()
            )
            connection.createStatement().execute(
                configEntityFactory.createConfiguration(
                    CordaDb.VirtualNodes.persistenceUnitName,
                    dbAdmin,
                    dbAdminPassword,
                    dbUrl,
                    DbPrivilege.DDL
                ).toInsertStatement()
            )
            connection.createStatement().execute(
                configEntityFactory.createCryptoConfig().toInsertStatement()
            )
        }
    }

    private fun superUserConnection() =
        OSGiDataSourceFactory.create(DB_DRIVER, dbSuperUserUrl, superUser, superUserPassword).connection

    private fun adminConnection() =
        OSGiDataSourceFactory.create(DB_DRIVER, dbAdminUrl, dbAdmin, dbAdminPassword).connection

    private fun configConnection() =
        OSGiDataSourceFactory.create(
            DB_DRIVER,
            dbAdminUrl,
            dbAdmin,
            dbAdminPassword
        ).connection

    private fun messageBusConnection() =
        OSGiDataSourceFactory.create(
            DB_DRIVER,
            dbAdminUrl,
            dbAdmin,
            dbAdminPassword
        ).connection.also { it.autoCommit = false }

    private fun rbacConnection() =
        OSGiDataSourceFactory.create(DB_DRIVER, dbAdminUrl, dbAdmin, dbAdminPassword).connection

    private fun dbInitialised(): Boolean {
        superUserConnection()
            .use { connection ->
                connection.createStatement().executeQuery(
                    "SELECT EXISTS (SELECT FROM pg_tables WHERE schemaname = 'config' AND tablename = 'config');"
                )
                    .use {
                        if (it.next()) {
                            return it.getBoolean(1)
                        }
                    }
            }
        return false
    }

    private fun initDb() {
        log.info("Create user $dbAdmin in $dbName in $dbSuperUserUrl.")
        superUserConnection()
            .use { connection ->
                val schemata = changelogFiles.values.joinToString(separator = ", ")
                connection.createStatement().execute(
                    // NOTE: this is different to the cli as this is set up to be using the official postgres image
                    //   instead of the Bitnami. The official image doesn't already have the "user" user.
                    """
                        CREATE USER "$dbAdmin" WITH ENCRYPTED PASSWORD '$dbAdminPassword';
                        ALTER ROLE "$dbAdmin" NOSUPERUSER CREATEDB CREATEROLE INHERIT LOGIN;
                        ALTER DATABASE "$dbName" OWNER TO "$dbAdmin";
                        ALTER SCHEMA public OWNER TO "$dbAdmin";
                        ALTER ROLE "$dbAdmin" SET search_path TO $schemata;
                    """.trimIndent()
                )
            }
    }

    private fun runDbMigration() {
        log.info("Run DB migrations in $dbAdminUrl.")
        adminConnection()
            .use { connection ->
                changelogFiles.forEach { (file, schema) ->
                    val changeLogResourceFiles = setOf(DbSchema::class.java).mapTo(LinkedHashSet()) { klass ->
                        ClassloaderChangeLog.ChangeLogResourceFiles(
                            klass.packageName,
                            listOf(file),
                            klass.classLoader
                        )
                    }
                    val dbChange = ClassloaderChangeLog(changeLogResourceFiles)
                    val schemaMigrator = LiquibaseSchemaMigratorImpl()
                    createDbSchemaIfRequired(schema)
                    connection.createStatement().execute("SET search_path TO $schema;")
                    schemaMigrator.updateDb(connection, dbChange, schema)
                }
            }
    }

    private fun createDbSchemaIfRequired(schema: String) {
        log.info("Create SCHEMA $schema.")
        adminConnection()
            .use { connection ->
                connection.createStatement().execute("CREATE SCHEMA IF NOT EXISTS $schema;")
            }
    }

    private fun createUserConfig(user: String, password: String) {
        log.info("Create user config for $user")
        rbacConnection()
            .use { connection ->
                connection.createStatement().execute(buildRbacConfigSql(user, password, "Setup Script"))
            }
    }

    private fun createDbUsersAndGrants() {
        val sql = """
            CREATE SCHEMA IF NOT EXISTS crypto;
            
            CREATE USER rbac_user_$dbName WITH ENCRYPTED PASSWORD 'rbac_password';
            GRANT USAGE ON SCHEMA rbac to rbac_user_$dbName;
            GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA rbac to rbac_user_$dbName;
            CREATE USER crypto_user_$dbName WITH ENCRYPTED PASSWORD 'crypto_password';
            GRANT USAGE ON SCHEMA crypto to crypto_user_$dbName;
            GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA crypto to crypto_user_$dbName;
        """.trimIndent()

        adminConnection()
            .use { connection ->
                connection.createStatement().execute(sql)
            }
        superUserConnection()
            .use { connection ->
                val sqlStmt = """
                    ALTER ROLE "rbac_user_$dbName" SET search_path TO rbac;
                    ALTER ROLE "crypto_user_$dbName" SET search_path TO crypto, state_manager;
                """.trimIndent()
                connection.createStatement().execute(sqlStmt)
            }
    }
}
