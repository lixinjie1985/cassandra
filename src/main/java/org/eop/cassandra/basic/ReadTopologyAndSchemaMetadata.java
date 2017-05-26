package org.eop.cassandra.basic;

import com.datastax.driver.core.Cluster;
import com.datastax.driver.core.Host;
import com.datastax.driver.core.KeyspaceMetadata;
import com.datastax.driver.core.Metadata;
import com.datastax.driver.core.TableMetadata;

/**
 * @author lixinjie
 * @since 2017-05-26
 */
public class ReadTopologyAndSchemaMetadata {

	public static void main(String[] args) {
		Cluster cluster = Cluster.builder().addContactPoints("192.168.95.57", "192.168.95.52").withPort(9042).build();
		Metadata metadata = cluster.getMetadata();
		System.out.printf("Connected to cluster: %s%n", metadata.getClusterName());
		System.out.println("---------------------------------------------------------------");
		 for (Host host : metadata.getAllHosts()) {
             System.out.printf("Datatacenter: %s; Host: %s; Rack: %s%n",
                     host.getDatacenter(), host.getAddress(), host.getRack());
		 }
		 System.out.println("---------------------------------------------------------------"); 
		 for (KeyspaceMetadata keyspace : metadata.getKeyspaces()) {
             for (TableMetadata table : keyspace.getTables()) {
                 System.out.printf("Keyspace: %s; Table: %s%n",
                         keyspace.getName(), table.getName());
             }
		 }
		 
		if (cluster != null) {
			cluster.close();
		}
	}

}
