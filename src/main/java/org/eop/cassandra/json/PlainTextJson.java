package org.eop.cassandra.json;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.Statement;
import static com.datastax.driver.core.querybuilder.QueryBuilder.*;

/**
 * @author lixinjie
 * @since 2017-05-26
 */
public class PlainTextJson {
	static String[] CONTACT_POINTS = {"127.0.0.1"};
    static int PORT = 9042;

    public static void main(String[] args) {
        Cluster cluster = null;
        try {
            cluster = Cluster.builder()
                    .addContactPoints(CONTACT_POINTS).withPort(PORT)
                    .build();
            Session session = cluster.connect();

            createSchema(session);

            insertWithCoreApi(session);
            selectWithCoreApi(session);

            insertWithQueryBuilder(session);
            selectWithQueryBuilder(session);
        } finally {
            if (cluster != null) cluster.close();
        }
    }

    private static void createSchema(Session session) {
        session.execute("CREATE KEYSPACE IF NOT EXISTS examples " +
                "WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1}");
        session.execute("CREATE TABLE IF NOT EXISTS examples.querybuilder_json(" +
                "id int PRIMARY KEY, name text, specs map<text, text>)");
    }

    /**
     * Demonstrates data insertion with the "core" API, i.e. providing the full query strings.
     */
    private static void insertWithCoreApi(Session session) {
        // Bind in a simple statement:
        session.execute("INSERT INTO examples.querybuilder_json JSON ?",
                "{ \"id\": 1, \"name\": \"Mouse\", \"specs\": { \"color\": \"silver\" } }");

        // Bind in a prepared statement:
        // (we use a local variable here for the sake of example, but in a real application you would cache and reuse
        // the prepared statement)
        PreparedStatement pst = session.prepare("INSERT INTO examples.querybuilder_json JSON :payload");
        session.execute(pst.bind()
                .setString("payload", "{ \"id\": 2, \"name\": \"Keyboard\", \"specs\": { \"layout\": \"qwerty\" } }"));

        // fromJson lets you provide individual columns as JSON:
        session.execute("INSERT INTO examples.querybuilder_json " +
                        "(id, name, specs)  VALUES (?, ?, fromJson(?))",
                3, "Screen", "{ \"size\": \"24-inch\" }");
    }

    /**
     * Demonstrates data retrieval with the "core" API, i.e. providing the full query strings.
     */
    private static void selectWithCoreApi(Session session) {
        // Reading the whole row as a JSON object:
        Row row = session.execute("SELECT JSON * FROM examples.querybuilder_json WHERE id = ?", 1).one();
        System.out.printf("Entry #1 as JSON: %s%n", row.getString("[json]"));

        // Extracting a particular column as JSON:
        row = session.execute("SELECT id, toJson(specs) AS json_specs FROM examples.querybuilder_json WHERE id = ?", 2)
                .one();
        System.out.printf("Entry #%d's specs as JSON: %s%n",
                row.getInt("id"), row.getString("json_specs"));
    }

    /**
     * Same as {@link #insertWithCoreApi(Session)}, but using {@link com.datastax.driver.core.querybuilder.QueryBuilder}
     * to construct the queries.
     */
    private static void insertWithQueryBuilder(Session session) {
        // Simple statement:
        Statement stmt = insertInto("examples", "querybuilder_json")
                .json("{ \"id\": 1, \"name\": \"Mouse\", \"specs\": { \"color\": \"silver\" } }");
        session.execute(stmt);

        // Prepare and bind:
        // (again, cache the prepared statement in a real application)
        PreparedStatement pst = session.prepare(
                insertInto("examples", "querybuilder_json").json(bindMarker("payload")));
        session.execute(pst.bind()
                .setString("payload", "{ \"id\": 2, \"name\": \"Keyboard\", \"specs\": { \"layout\": \"qwerty\" } }"));

        // fromJson on a single column:
        stmt = insertInto("examples", "querybuilder_json")
                .value("id", 3)
                .value("name", "Screen")
                .value("specs", fromJson("{ \"size\": \"24-inch\" }"));
        session.execute(stmt);
    }

    /**
     * Same as {@link #selectWithCoreApi(Session)}, but using {@link com.datastax.driver.core.querybuilder.QueryBuilder}
     * to construct the queries.
     */
    private static void selectWithQueryBuilder(Session session) {
        // Reading the whole row as a JSON object:
        Statement stmt = select().json()
                .from("examples", "querybuilder_json")
                .where(eq("id", 1));
        Row row = session.execute(stmt).one();
        System.out.printf("Entry #1 as JSON: %s%n", row.getString("[json]"));

        // Extracting a particular column as JSON:
        stmt = select()
                .column("id")
                .toJson("specs").as("json_specs")
                .from("examples", "querybuilder_json")
                .where(eq("id", 2));
        row = session.execute(stmt).one();
        System.out.printf("Entry #%d's specs as JSON: %s%n",
                row.getInt("id"), row.getString("json_specs"));
    }
}
