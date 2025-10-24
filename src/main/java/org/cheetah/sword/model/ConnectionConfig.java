package org.cheetah.sword.model;

import java.nio.file.Path;

/**
 * Carries:
 * - Database connection info (dbType, host, port, username, password, dbName)
 * - Logical selection info (catalog, schema)
 * - Code generation preferences (basePackage, outputPath, fkMode, relationFetch, generateDto)
 *
 * dbType          Database vendor/type chosen by the user.
 * host / port     Network location of the database.
 * username        Username for authentication.
 * password        Password for authentication.
 * dbName          Logical DB / catalog / initial database name provided by the user.
 *
 * catalog         Catalog chosen at runtime (may be null).
 * schema          Schema chosen at runtime (may be null).
 *
 * basePackage     Base Java package for generated entities.
 * outputPath      Filesystem root path where .java files will be written.
 *
 * fkMode          Foreign key modeling mode:
 *                 - SCALAR   -> Long customerId
 *                 - RELATION -> Customer customer (@ManyToOne / @OneToOne)
 *
 * relationFetch   Fetch strategy for @ManyToOne / @OneToOne:
 *                 - LAZY  (default)
 *                 - EAGER
 *                 Collections (@OneToMany) are always LAZY.
 *
 * generateDto     If true, DTOs and MapStruct mappers will also be generated.
 */
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

    private FkMode fkMode = FkMode.SCALAR;
    private RelationFetch relationFetch = RelationFetch.LAZY;

    private boolean generateDto = false;

    // dbType
    public DbType getDbType() {
        return dbType;
    }

    public void setDbType(DbType dbType) {
        this.dbType = dbType;
    }

    // host
    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    // port
    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    // username
    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    // password
    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    // dbName
    public String getDbName() {
        return dbName;
    }

    public void setDbName(String dbName) {
        this.dbName = dbName;
    }

    // catalog
    public String getCatalog() {
        return catalog;
    }

    public void setCatalog(String catalog) {
        this.catalog = catalog;
    }

    // schema
    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    // basePackage
    public String getBasePackage() {
        return basePackage;
    }

    public void setBasePackage(String basePackage) {
        this.basePackage = basePackage;
    }

    // outputPath
    public Path getOutputPath() {
        return outputPath;
    }

    public void setOutputPath(Path outputPath) {
        this.outputPath = outputPath;
    }

    // fkMode
    public FkMode getFkMode() {
        return fkMode;
    }

    public void setFkMode(FkMode fkMode) {
        this.fkMode = fkMode;
    }

    // relationFetch
    public RelationFetch getRelationFetch() {
        return relationFetch;
    }

    public void setRelationFetch(RelationFetch relationFetch) {
        this.relationFetch = relationFetch;
    }

    // generateDto
    public boolean getGenerateDto() {
        return generateDto;
    }

    public void setGenerateDto(boolean generateDto) {
        this.generateDto = generateDto;
    }
}