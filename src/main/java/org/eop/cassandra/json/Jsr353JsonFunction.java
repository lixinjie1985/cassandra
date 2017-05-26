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
public class Jsr353JsonFunction {
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
                    .withCodecRegistry(new CodecRegistry()
                            .register(userCodec))
                    .build();

            Session session = cluster.connect();

            createSchema(session);
            insertFromJson(session);
            selectToJson(session);

        } finally {
            if (cluster != null) cluster.close();
        }
    }

    private static void createSchema(Session session) {
        session.execute("CREATE KEYSPACE IF NOT EXISTS examples " +
                "WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1}");
        session.execute("CREATE TYPE IF NOT EXISTS examples.json_jsr353_function_user(" +
                "name text, age int)");
        session.execute("CREATE TABLE IF NOT EXISTS examples.json_jsr353_function(" +
                "id int PRIMARY KEY, user frozen<json_jsr353_function_user>, scores map<varchar,float>)");
    }

    // Mapping JSON payloads to table columns of arbitrary types,
    // using fromJson() function
    private static void insertFromJson(Session session) {

        JsonObject alice = Json.createObjectBuilder()
                .add("name", "alice")
                .add("age", 30)
                .build();

        JsonObject bob = Json.createObjectBuilder()
                .add("name", "bob")
                .add("age", 35)
                .build();

        JsonObject aliceScores = Json.createObjectBuilder()
                .add("call_of_duty", 4.8)
                .add("pokemon_go", 9.7)
                .build();

        JsonObject bobScores = Json.createObjectBuilder()
                .add("zelda", 8.3)
                .add("pokemon_go", 12.4)
                .build();

        // Build and execute a simple statement
        Statement stmt = insertInto("examples", "json_jsr353_function")
                .value("id", 1)
                // client-side, the JsonObject will be converted into a JSON String;
                // then, server-side, the fromJson() function will convert that JSON string
                // into an instance of the json_jsr353_function_user user-defined type (UDT),
                // which will be persisted into the column "user"
                .value("user", fromJson(alice))
                // same thing, but this time converting from
                // a JsonObject to a JSON string, then from this string to a map<varchar,float>
                .value("scores", fromJson(aliceScores));
        session.execute(stmt);

        // The JSON object can be a bound value if the statement is prepared
        // (we use a local variable here for the sake of example, but in a real application you would cache and reuse
        // the prepared statement)
        PreparedStatement pst = session.prepare(
                insertInto("examples", "json_jsr353_function")
                        .value("id", bindMarker("id"))
                        .value("user", fromJson(bindMarker("user")))
                        .value("scores", fromJson(bindMarker("scores"))));
        session.execute(pst.bind()
                .setInt("id", 2)
                // note that the codec requires that the type passed to the set() method
                // be always JsonStructure, and not a subclass of it, such as JsonObject
                .set("user", bob, JsonStructure.class)
                .set("scores", bobScores, JsonStructure.class));
    }

    // Retrieving JSON payloads from table columns of arbitrary types,
    // using toJson() function
    private static void selectToJson(Session session) {

        Statement stmt = select()
                .column("id")
                .toJson("user").as("user")
                .toJson("scores").as("scores")
                .from("examples", "json_jsr353_function")
                .where(in("id", 1, 2));

        ResultSet rows = session.execute(stmt);

        for (Row row : rows) {
            int id = row.getInt("id");
            // retrieve the JSON payload and convert it to a JsonObject instance
            // note that the codec requires that the type passed to the get() method
            // be always JsonStructure, and not a subclass of it, such as JsonObject,
            // hence the need to downcast to JsonObject manually
            JsonObject user = (JsonObject) row.get("user", JsonStructure.class);
            // it is also possible to retrieve the raw JSON payload
            String userJson = row.getString("user");
            // retrieve the JSON payload and convert it to a JsonObject instance
            JsonObject scores = (JsonObject) row.get("scores", JsonStructure.class);
            // it is also possible to retrieve the raw JSON payload
            String scoresJson = row.getString("scores");
            System.out.printf("Retrieved row:%n" +
                            "id           %d%n" +
                            "user         %s%n" +
                            "user (raw)   %s%n" +
                            "scores       %s%n" +
                            "scores (raw) %s%n%n",
                    id, user, userJson, scores, scoresJson);
        }
    }
}
