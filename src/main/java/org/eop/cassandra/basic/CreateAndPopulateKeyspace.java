package org.eop.cassandra.basic;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;

/**
 * @author lixinjie
 * @since 2017-05-26
 */
public class CreateAndPopulateKeyspace {

	private Cluster cluster;

	private Session session;
	
	public static void main(String[] args) {
		CreateAndPopulateKeyspace client = new CreateAndPopulateKeyspace();
		client.connect();
        //client.createSchema();
        //client.loadData();
		client.querySchema();
		client.close();
	}

	public void connect() {

        cluster = Cluster.builder()
                .addContactPoints("192.168.95.57", "192.168.95.52").withPort(9042)
                .build();
        System.out.printf("Connected to cluster: %s%n", cluster.getMetadata().getClusterName());
        session = cluster.connect();
    }

	public void createSchema() {

        session.execute("CREATE KEYSPACE IF NOT EXISTS simplex WITH replication " +
                "= {'class':'SimpleStrategy', 'replication_factor':1};");

        session.execute(
                "CREATE TABLE IF NOT EXISTS simplex.songs (" +
                        "id uuid PRIMARY KEY," +
                        "title text," +
                        "album text," +
                        "artist text," +
                        "tags set<text>," +
                        "data blob" +
                        ");");

        session.execute(
                "CREATE TABLE IF NOT EXISTS simplex.playlists (" +
                        "id uuid," +
                        "title text," +
                        "album text, " +
                        "artist text," +
                        "song_id uuid," +
                        "PRIMARY KEY (id, title, album, artist)" +
                        ");");
    }

	public void loadData() {

        session.execute(
                "INSERT INTO simplex.songs (id, title, album, artist, tags) " +
                        "VALUES (" +
                        "756716f7-2e54-4715-9f00-91dcbea6cf50," +
                        "'La Petite Tonkinoise'," +
                        "'Bye Bye Blackbird'," +
                        "'Joséphine Baker'," +
                        "{'jazz', '2013'})" +
                        ";");

        session.execute(
                "INSERT INTO simplex.playlists (id, song_id, title, album, artist) " +
                        "VALUES (" +
                        "2cc9ccb7-6221-4ccb-8387-f22b6a1b354d," +
                        "756716f7-2e54-4715-9f00-91dcbea6cf50," +
                        "'La Petite Tonkinoise'," +
                        "'Bye Bye Blackbird'," +
                        "'Joséphine Baker'" +
                        ");");
	}
	
	public void querySchema() {

        ResultSet results = session.execute(
                "SELECT * FROM simplex.playlists " +
                        "WHERE id = 2cc9ccb7-6221-4ccb-8387-f22b6a1b354d;");

        System.out.printf("%-30s\t%-20s\t%-20s%n", "title", "album", "artist");
        System.out.println("-------------------------------+-----------------------+--------------------");

        for (Row row : results) {

            System.out.printf("%-30s\t%-20s\t%-20s%n",
                    row.getString("title"),
                    row.getString("album"),
                    row.getString("artist"));

        }

	}
	
	public void close() {
        session.close();
        cluster.close();
	}
}
