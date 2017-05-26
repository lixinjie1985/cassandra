package org.eop.cassandra.basic;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.ResultSet;
import com.datastax.driver.core.Row;
import com.datastax.driver.core.Session;

/**
 * @author lixinjie
 * @since 2017-05-26
 */
public class ReadCassandraVersion {

	public static void main(String[] args) {
		// The Cluster object is the main entry point of the driver.
        // It holds the known state of the actual Cassandra cluster (notably the Metadata).
        // This class is thread-safe, you should create a single instance (per target Cassandra cluster), and share
		// it throughout your application.
		Cluster cluster = Cluster.builder().addContactPoints("192.168.95.57", "192.168.95.52").withPort(9042).build();
		// The Session is what you use to execute queries. Likewise, it is thread-safe and should be reused.
		Session session = cluster.connect();
		// We use execute to send a query to Cassandra. This returns a ResultSet, which is essentially a collection
		// of Row objects.
		ResultSet rs = session.execute("select release_version from system.local");
		// Extract the first row (which is the only one in this case).
		Row row = rs.one();
		// Extract the value of the first (and only) column from the row.
		String releaseVersion = row.getString("release_version");

		System.out.printf("Cassandra version is: %s%n", releaseVersion);
		
		// Close the cluster after we’re done with it. This will also close any session that was created from this
        // cluster.
        // This step is important because it frees underlying resources (TCP connections, thread pools...). In a
        // real application, you would typically do this at shutdown (for example, when undeploying your webapp).
        if (cluster != null) {
        	cluster.close();
        }
	}

}
