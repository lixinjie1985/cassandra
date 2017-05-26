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
public class JacksonJsonRow {
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
            insertJsonRow(session);
            selectJsonRow(session);

        } finally {
            if (cluster != null) cluster.close();
        }
    }

    private static void createSchema(Session session) {
        session.execute("CREATE KEYSPACE IF NOT EXISTS examples " +
                "WITH replication = {'class': 'SimpleStrategy', 'replication_factor': 1}");
        session.execute("CREATE TABLE IF NOT EXISTS examples.json_jackson_row(" +
                "id int PRIMARY KEY, name text, age int)");
    }

    // Mapping a User instance to a table row using INSERT JSON
    private static void insertJsonRow(Session session) {
        // Build and execute a simple statement
        Statement stmt = insertInto("examples", "json_jackson_row")
                .json(new User(1, "alice", 30));
        session.execute(stmt);

        // The JSON object can be a bound value if the statement is prepared
        // (we use a local variable here for the sake of example, but in a real application you would cache and reuse
        // the prepared statement)
        PreparedStatement pst = session.prepare(
                insertInto("examples", "json_jackson_row").json(bindMarker("user")));
        session.execute(pst.bind()
                .set("user", new User(2, "bob", 35), User.class));
    }

    // Retrieving User instances from table rows using SELECT JSON
    private static void selectJsonRow(Session session) {

        // Reading the whole row as a JSON object
        Statement stmt = select().json()
                .from("examples", "json_jackson_row")
                .where(in("id", 1, 2));

        ResultSet rows = session.execute(stmt);

        for (Row row : rows) {
            // SELECT JSON returns only one column for each row, of type VARCHAR,
            // containing the row as a JSON payload
            User user = row.get(0, User.class);
            System.out.printf("Retrieved user: %s%n", user);
        }
    }

    @SuppressWarnings("unused")
    public static class User {

        private final int id;

        private final String name;

        private final int age;

        @JsonCreator
        public User(@JsonProperty("id") int id, @JsonProperty("name") String name, @JsonProperty("age") int age) {
            this.id = id;
            this.name = name;
            this.age = age;
        }

        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public int getAge() {
            return age;
        }

        @Override
        public String toString() {
            return String.format("%s (id %d, age %d)", name, id, age);
        }
    }
}
