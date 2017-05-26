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
import static com.datastax.driver.core.querybuilder.QueryBuilder.*;

/**
 * @author lixinjie
 * @since 2017-05-26
 */
public class JacksonJsonColumn {
	static String[] CONTACT_POINTS = {"127.0.0.1"};
    static int PORT = 9042;

    public static void main(String[] args) {
        Cluster cluster = null;
        try {

            // A codec to convert JSON payloads into User instances;
            // this codec is declared in the driver-extras module
            TypeCodec<User> userCodec = new JacksonJsonCodec<User>(User.class);

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
        session.execute("CREATE TABLE IF NOT EXISTS examples.json_jackson_column(" +
                "id int PRIMARY KEY, json text)");
    }

    // Mapping a User instance to a table column
    private static void insertJsonColumn(Session session) {

        User alice = new User("alice", 30);
        User bob = new User("bob", 35);

        // Build and execute a simple statement
        Statement stmt = insertInto("examples", "json_jackson_column")
                .value("id", 1)
                // the User object will be converted into a String and persisted into the VARCHAR column "json"
                .value("json", alice);
        session.execute(stmt);

        // The JSON object can be a bound value if the statement is prepared
        // (we use a local variable here for the sake of example, but in a real application you would cache and reuse
        // the prepared statement)
        PreparedStatement pst = session.prepare(
                insertInto("examples", "json_jackson_column")
                        .value("id", bindMarker("id"))
                        .value("json", bindMarker("json")));
        session.execute(pst.bind()
                .setInt("id", 2)
                .set("json", bob, User.class));
    }

    // Retrieving User instances from a table column
    private static void selectJsonColumn(Session session) {

        Statement stmt = select()
                .from("examples", "json_jackson_column")
                .where(in("id", 1, 2));

        ResultSet rows = session.execute(stmt);

        for (Row row : rows) {
            int id = row.getInt("id");
            // retrieve the JSON payload and convert it to a User instance
            User user = row.get("json", User.class);
            // it is also possible to retrieve the raw JSON payload
            String json = row.getString("json");
            System.out.printf("Retrieved row:%n" +
                            "id           %d%n" +
                            "user         %s%n" +
                            "user (raw)   %s%n%n",
                    id, user, json);

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
