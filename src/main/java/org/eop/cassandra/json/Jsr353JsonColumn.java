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
public class Jsr353JsonColumn {
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
            insertJsonColumn(session);
            selectJsonColumn(session);

        } finally {
            if (cluster != null) cluster.close();
        }
    }

    private static void createSchema(Session session) {
        session.execute("CREATE KEYSPACE IF NOT EXISTS examples " +
                "WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1}");
        session.execute("CREATE TABLE IF NOT EXISTS examples.json_jsr353_column(" +
                "id int PRIMARY KEY, json text)");
    }

    // Mapping a JSON object to a table column
    private static void insertJsonColumn(Session session) {

        JsonObject alice = Json.createObjectBuilder()
                .add("name", "alice")
                .add("age", 30)
                .build();

        JsonObject bob = Json.createObjectBuilder()
                .add("name", "bob")
                .add("age", 35)
                .build();

        // Build and execute a simple statement
        Statement stmt = insertInto("examples", "json_jsr353_column")
                .value("id", 1)
                // the JSON object will be converted into a String and persisted into the VARCHAR column "json"
                .value("json", alice);
        session.execute(stmt);

        // The JSON object can be a bound value if the statement is prepared
        // (we use a local variable here for the sake of example, but in a real application you would cache and reuse
        // the prepared statement)
        PreparedStatement pst = session.prepare(
                insertInto("examples", "json_jsr353_column")
                        .value("id", bindMarker("id"))
                        .value("json", bindMarker("json")));
        session.execute(pst.bind()
                .setInt("id", 2)
                // note that the codec requires that the type passed to the set() method
                // be always JsonStructure, and not a subclass of it, such as JsonObject
                .set("json", bob, JsonStructure.class));
    }

    // Retrieving JSON objects from a table column
    private static void selectJsonColumn(Session session) {

        Statement stmt = select()
                .from("examples", "json_jsr353_column")
                .where(in("id", 1, 2));

        ResultSet rows = session.execute(stmt);

        for (Row row : rows) {
            int id = row.getInt("id");
            // retrieve the JSON payload and convert it to a JsonObject instance
            // note that the codec requires that the type passed to the get() method
            // be always JsonStructure, and not a subclass of it, such as JsonObject,
            // hence the need to downcast to JsonObject manually
            JsonObject user = (JsonObject) row.get("json", JsonStructure.class);
            // it is also possible to retrieve the raw JSON payload
            String json = row.getString("json");
            System.out.printf("Retrieved row:%n" +
                            "id           %d%n" +
                            "user         %s%n" +
                            "user (raw)   %s%n%n",
                    id, user, json);
        }
    }
}
