package org.eop.cassandra.json;

import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonStructure;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.CodecRegistry;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.Statement;
import com.datastax.driver.extras.codecs.json.Jsr353JsonCodec;
import static com.datastax.driver.core.querybuilder.QueryBuilder.*;

/**
 * @author lixinjie
 * @since 2017-05-26
 */
public class Jsr353JsonRow {
	static String[] CONTACT_POINTS = {"127.0.0.1"};
    static int PORT = 9042;

    public static void main(String[] args) {
        Cluster cluster = null;
        try {

            // A codec to convert JSON payloads into JsonObject instances;
            // this codec is declared in the driver-extras module
            Jsr353JsonCodec userCodec = new Jsr353JsonCodec();

            cluster = Cluster.builder()
                    .addContactPoints(CONTACT_POINTS).withPort(PORT)
                    .withCodecRegistry(new CodecRegistry().register(userCodec))
                    .build();

            Session session = cluster.connect();

            createSchema(session);
            insertJsonRow(session);
            selectJsonRow(session);

        } finally {
            if (cluster != null) cluster.close();
        }
    }

    private static void createSchema(Session session) {
        session.execute("CREATE KEYSPACE IF NOT EXISTS examples " +
                "WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1}");
        session.execute("CREATE TABLE IF NOT EXISTS examples.json_jsr353_row(" +
                "id int PRIMARY KEY, name text, age int)");
    }

    // Mapping a User instance to a table row using INSERT JSON
    private static void insertJsonRow(Session session) {

        JsonObject alice = Json.createObjectBuilder()
                .add("id", 1)
                .add("name", "alice")
                .add("age", 30)
                .build();

        JsonObject bob = Json.createObjectBuilder()
                .add("id", 2)
                .add("name", "bob")
                .add("age", 35)
                .build();

        // Build and execute a simple statement
        Statement stmt = insertInto("examples", "json_jsr353_row")
                .json(alice);
        session.execute(stmt);

        // The JSON object can be a bound value if the statement is prepared
        // (we use a local variable here for the sake of example, but in a real application you would cache and reuse
        // the prepared statement)
        PreparedStatement pst = session.prepare(
                insertInto("examples", "json_jsr353_row").json(bindMarker("user")));
        session.execute(pst.bind()
                // note that the codec requires that the type passed to the set() method
                // be always JsonStructure, and not a subclass of it, such as JsonObject
                .set("user", bob, JsonStructure.class));
    }

    // Retrieving User instances from table rows using SELECT JSON
    private static void selectJsonRow(Session session) {

        // Reading the whole row as a JSON object
        Statement stmt = select().json()
                .from("examples", "json_jsr353_row")
                .where(in("id", 1, 2));

        ResultSet rows = session.execute(stmt);

        for (Row row : rows) {
            // SELECT JSON returns only one column for each row, of type VARCHAR,
            // containing the row as a JSON payload.
            // Note that the codec requires that the type passed to the get() method
            // be always JsonStructure, and not a subclass of it, such as JsonObject,
            // hence the need to downcast to JsonObject manually
            JsonObject user = (JsonObject) row.get(0, JsonStructure.class);
            System.out.printf("Retrieved user: %s%n", user);
        }
    }
}
