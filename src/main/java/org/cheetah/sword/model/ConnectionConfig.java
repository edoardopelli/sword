package org.cheetah.sword.model;

import java.nio.file.Path;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Carries: - Database connection info (dbType, host, port, username, password,
 * dbName) - Logical selection info (catalog, schema) - Code generation
 * preferences (basePackage, outputPath, fkMode, relationFetch, generateDto)
 *
 * dbType Database vendor/type chosen by the user. host / port Network location
 * of the database. username Username for authentication. password Password for
 * authentication. dbName Logical DB / catalog / initial database name provided
 * by the user.
 *
 * catalog Catalog chosen at runtime (may be null). schema Schema chosen at
 * runtime (may be null).
 *
 * basePackage Base Java package for generated entities. outputPath Filesystem
 * root path where .java files will be written.
 *
 * fkMode Foreign key modeling mode: - SCALAR -> Long customerId - RELATION ->
 * Customer customer (@ManyToOne / @OneToOne)
 *
 * relationFetch Fetch strategy for @ManyToOne / @OneToOne: - LAZY (default) -
 * EAGER Collections (@OneToMany) are always LAZY.
 *
 * generateDto If true, DTOs and MapStruct mappers will also be generated.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConnectionConfig {

	private DbType dbType;
	private String host;
	private int port;
	private String username;
	private String password;
	private String dbName;

	private String catalog;
	private String schema;

	private String basePackage;
	private Path outputPath;

	@Builder.Default
	private FkMode fkMode = FkMode.SCALAR;
	@Builder.Default
	private RelationFetch relationFetch = RelationFetch.LAZY;

	@Builder.Default
	@Getter
	private boolean generateDto = false;

	@Builder.Default
	@Getter
	private boolean generateRepositories = false;
	
	@Getter
	@Builder.Default
	private boolean generateServices = false;
	
	@Getter
	@Builder.Default
	private boolean generateControllers = false;
	
	private String yamlConfigPath;

}