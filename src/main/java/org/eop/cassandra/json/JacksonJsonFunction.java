package org.eop.cassandra.json;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.CodecRegistry;
import com.datastax.driver.core.PreparedStatement;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;
import com.datastax.driver.core.Statement;
import com.datastax.driver.core.TypeCodec;
import com.datastax.driver.extras.codecs.json.JacksonJsonCodec;
import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import static com.datastax.driver.core.querybuilder.QueryBuilder.*;

/**
 * @author lixinjie
 * @since 2017-05-26
 */
public class JacksonJsonFunction {
	static String[] CONTACT_POINTS = {"127.0.0.1"};
    static int PORT = 9042;

    public static void main(String[] args) {
        Cluster cluster = null;
        try {

            // A codec to convert JSON payloads into User instances;
            // this codec is declared in the driver-extras module
            TypeCodec<User> userCodec = new JacksonJsonCodec<User>(User.class);

            // A codec to convert generic JSON payloads into JsonNode instances
            TypeCodec<JsonNode> jsonNodeCodec = new JacksonJsonCodec<JsonNode>(JsonNode.class);

            cluster = Cluster.builder()
                    .addContactPoints(CONTACT_POINTS).withPort(PORT)
                    .withCodecRegistry(new CodecRegistry()
                            .register(userCodec)
                            .register(jsonNodeCodec))
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
        session.execute("CREATE TYPE IF NOT EXISTS examples.json_jackson_function_user(" +
                "name text, age int)");
        session.execute("CREATE TABLE IF NOT EXISTS examples.json_jackson_function(" +
                "id int PRIMARY KEY, user frozen<json_jackson_function_user>, scores map<varchar,float>)");
    }

    // Mapping JSON payloads to table columns of arbitrary types,
    // using fromJson() function
    private static void insertFromJson(Session session) {

        User alice = new User("alice", 30);
        User bob = new User("bob", 35);

        ObjectNode aliceScores = JsonNodeFactory.instance.objectNode()
                .put("call_of_duty", 4.8)
                .put("pokemon_go", 9.7);
        ObjectNode bobScores = JsonNodeFactory.instance.objectNode()
                .put("zelda", 8.3)
                .put("pokemon_go", 12.4);

        // Build and execute a simple statement
        Statement stmt = insertInto("examples", "json_jackson_function")
                .value("id", 1)
                // client-side, the User object will be converted into a JSON String;
                // then, server-side, the fromJson() function will convert that JSON string
                // into an instance of the json_jackson_function_user user-defined type (UDT),
                // which will be persisted into the column "user"
                .value("user", fromJson(alice))
                // same thing, but this time converting from
                // a generic JsonNode to a JSON string, then from this string to a map<varchar,float>
                .value("scores", fromJson(aliceScores));
        session.execute(stmt);

        // The JSON object can be a bound value if the statement is prepared
        // (we use a local variable here for the sake of example, but in a real application you would cache and reuse
        // the prepared statement)
        PreparedStatement pst = session.prepare(
                insertInto("examples", "json_jackson_function")
                        .value("id", bindMarker("id"))
                        .value("user", fromJson(bindMarker("user")))
                        .value("scores", fromJson(bindMarker("scores"))));
        session.execute(pst.bind()
                .setInt("id", 2)
                .set("user", bob, User.class)
                // note that the codec requires that the type passed to the set() method
                // be always JsonNode, and not a subclass of it, such as ObjectNode
                .set("scores", bobScores, JsonNode.class));
    }

    // Retrieving JSON payloads from table columns of arbitrary types,
    // using toJson() function
    private static void selectToJson(Session session) {

        Statement stmt = select()
                .column("id")
                .toJson("user").as("user")
                .toJson("scores").as("scores")
                .from("examples", "json_jackson_function")
                .where(in("id", 1, 2));

        ResultSet rows = session.execute(stmt);

        for (Row row : rows) {
            int id = row.getInt("id");
            // retrieve the JSON payload and convert it to a User instance
            User user = row.get("user", User.class);
            // it is also possible to retrieve the raw JSON payload
            String userJson = row.getString("user");
            // retrieve the JSON payload and convert it to a JsonNode instance
            // note that the codec requires that the type passed to the get() method
            // be always JsonNode, and not a subclass of it, such as ObjectNode
            JsonNode scores = row.get("scores", JsonNode.class);
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

    @SuppressWarnings("unused")
    public static class User {

        private final String name;

        private final int age;

        @JsonCreator
        public User(@JsonProperty("name") String name, @JsonProperty("age") int age) {
            this.name = name;
            this.age = age;
        }

        public String getName() {
            return name;
        }

        public int getAge() {
            return age;
        }

        @Override
        public String toString() {
            return String.format("%s (%s)", name, age);
        }
    }
}
