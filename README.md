# S.W.O.R.D.
Schema-Wide Object Reverse Designer

S.W.O.R.D. is a command-line code generator that reverse-engineers an existing relational database schema and produces a ready-to-use JPA model layer.

It connects to a live database, inspects tables, primary keys, foreign keys, and column types, and then generates:
- JPA entities (with Lombok)
- Optional DTO classes
- Optional MapStruct mappers
- Optional Spring Data repositories

All code is written to your local filesystem using your chosen base package and output directory.



## 1. What "S.W.O.R.D." means

**S.W.O.R.D. = Schema-Wide Object Reverse Designer**

- **Schema-Wide**  
  The tool analyzes an entire schema/catalog at once, not just a single table.
- **Object**  
  The output is a full Java object model: entities, DTOs, mappers, repositories.
- **Reverse**  
  The process goes from DB → code (reverse engineering).
- **Designer**  
  The generator does not just dump code. It makes structural decisions (naming, relationships, fetch modes, etc.) and applies conventions.



## 2. Supported databases

At startup, S.W.O.R.D. lets you select a JDBC driver / vendor:

- PostgreSQL
- MariaDB
- MySQL
- Microsoft SQL Server
- H2
- IBM DB2

The wizard asks the necessary connection info (host, port, username, password, database name, etc.) depending on the vendor.



## 3. Wizard flow

When you run the JAR, S.W.O.R.D. launches an interactive wizard in your terminal.

### Step 1 — Choose DB vendor

You pick one of the supported database types.

### Step 2 — Connection info

You are prompted for:
- Host (with default)
- Port (with DB-specific default)
- Username
- Password
- Database name (or equivalent, e.g. Postgres database, SQL Server database, etc.)

We explicitly ask for the logical database name instead of assuming it equals the username.

The tool then attempts a live connection using these settings.

### Step 3 — Catalog / Schema selection

Different RDBMS expose catalogs, schemas, or both.  
S.W.O.R.D. detects what makes sense for the selected DB and lets you choose from the discovered values:
- Catalog (if applicable)
- Schema (if applicable)

For example:
- PostgreSQL → typically choose a schema (e.g. `public`, `app_schema`, …)
- SQL Server → typically choose both DB name and schema (`dbo`)
- MySQL / MariaDB → typically choose a catalog (= database)
- H2 → typically choose a schema (e.g. `PUBLIC`)
- DB2 → catalogs/schemas as reported by metadata

The chosen catalog/schema pair defines which tables will be reverse-engineered.

### Step 4 — Base package and output path

You are asked:
- **Base package** (for example: `org.cheetah.fracas.entities`)
- **Output path** (filesystem path where generated sources will be written)

The generator normalizes the base package.  
Example:
- Input: `org/cheetah/fracas/entities`
- Normalized: `org.cheetah.fracas.entities`

> The output folder is exactly the path you provide, not `<path>/<package>`.  
> The generator writes Java source folders under that path according to package structure.

### Step 5 — Foreign key mode

You choose how to represent FK columns in entities:

1. **Scalar mode (default)**  
   Each FK column is generated as a plain scalar field.  
   Example: a `CUSTOMER_ID` FK becomes  
   `private Long customerId;`

   No `@ManyToOne`, no JPA navigation.  
   This avoids lazy-loading issues and keeps entities lightweight.

2. **Relation mode**  
   Each FK column is replaced with an association and proper JPA annotations.

   - Unique FK column → `@OneToOne(fetch = FetchType.LAZY|EAGER)`
   - Non-unique FK column → `@ManyToOne(fetch = FetchType.LAZY|EAGER)`

   The generator will also create the inverse side on the parent entity:
   - If the FK on the child is unique → parent gets `@OneToOne(mappedBy="...")`
   - Otherwise → parent gets `@OneToMany(mappedBy="...") Set<ChildEntity> ...`

#### Relation fetch mode
If you choose **Relation mode**, you are then asked to pick the fetch strategy for these `@ManyToOne` / `@OneToOne` relations:

- `LAZY` (default)
- `EAGER`

Collections on the inverse side (`Set<ChildEntity>`) are always generated `LAZY`.

If you choose **Scalar mode**, you are **not** asked about fetch mode (because there are no JPA relations).

### Step 6 — Generate DTOs?

You can choose whether to also generate DTOs and MapStruct mappers.

- DTOs live in a sibling package called `...dtos`
- Mappers live in a sibling package called `...mappers`

(See section 4 below.)

### Step 7 — Generate repositories?

You can choose whether to generate Spring Data repositories.

- Repositories live in a sibling package called `...repositories`

(See section 5 below.)

### Step 8 — YAML mapping file (optional)

You can optionally pass a path to a YAML file that customizes naming
for tables and columns.

After collecting all answers, S.W.O.R.D. runs the generation and prints how many entities were created.



## 4. Generated code: Entities, DTOs, and Mappers

### 4.1 Entities

For each physical table, S.W.O.R.D. generates a JPA entity class.

Example input table name:
`USERS`

Default entity name:
`User`

Naming rules:
- Table name is singularized.
- CamelCase is preserved where it exists in the original name.
  - `USER_ACCOUNT` → `UserAccount`
  - `user_account` → `UserAccount`
  - `USERACCOUNT` → `Useraccount`
- If there is a custom mapping in the YAML configuration (see section 6), that overrides the default.

Each entity is annotated with:
```java
@Entity
@Table(name = "ACTUAL_TABLE_NAME")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Generated(value = "S.W.O.R.D.", date = "<timestamp>")
```

#### Primary keys
- If the table has a single-column PK:
  - The field is annotated with `@Id`.
  - If the column is auto-generated (identity/autoincrement/sequence), the field also gets `@GeneratedValue(...)`.
  - The PK field is marked with `@EqualsAndHashCode.Include` and `@ToString.Include`.

- If the table has a composite PK (more than one PK column):
  - The entity gets:
    ```java
    @EmbeddedId
    private <EntityName>Id id;
    ```
  - `<EntityName>Id` is generated as `@Embeddable`, implements `Serializable`, and mirrors the PK columns with `@Column(name="...")`.
  - PK fields inside `<EntityName>Id` are also annotated with `@EqualsAndHashCode.Include` and `@ToString.Include`.

#### Columns
Every physical column becomes a private field with `@Column(name="DB_COL")`.

PostgreSQL `json` / `jsonb` columns:
```java
@Column(name = "JSONB_COL", columnDefinition = "jsonb")
@JdbcTypeCode(SqlTypes.JSON)
private Map<String, Object> jsonbCol;
```

#### Foreign keys and relationships
Depends on FK mode:

**Scalar mode (default)**  
- FK columns remain scalar:
  ```java
  @Column(name = "CUSTOMER_ID")
  private Long customerId;
  ```
- No `@ManyToOne`, no `@JoinColumn`.
- The parent side (`@OneToMany`, etc.) is NOT generated.

**Relation mode**  
- For each single-column FK:
  - If the FK column in the child table is UNIQUE → `@OneToOne`
  - Otherwise → `@ManyToOne`
- `fetch = FetchType.LAZY` by default, unless you chose EAGER.
- The parent entity also receives the inverse association:
  - Unique FK → `@OneToOne(mappedBy="...")`
  - Non-unique FK → `@OneToMany(mappedBy="...") Set<ChildEntity> ...`
  - Collections on the inverse side are always `LAZY`.

This produces bidirectional navigation between entities.

> Many-to-many is not generated automatically.

#### Lombok-generated toString / equals / hashCode
Only fields explicitly annotated with `@ToString.Include` and `@EqualsAndHashCode.Include` are considered.
This avoids infinite recursion on relationships and prevents large binary fields from being printed.



### 4.2 DTOs

If you select "Generate DTOs", S.W.O.R.D. creates a DTO for each entity in a sibling package.

Given entity package:
```text
org.cheetah.fracas.entities
```
DTOs go in:
```text
org.cheetah.fracas.dtos
```

For entity `User`, DTO is `UserDto`.

DTO structure:
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString(onlyExplicitlyIncluded = true)
@EqualsAndHashCode(onlyExplicitlyIncluded = true)
@Generated(...)
public class UserDto {
    // one field per physical column in the table
}
```

Notes:
- The DTO always uses scalar FK fields (e.g. `Long customerId`) even if the entity was generated with relations.
- DTOs have no JPA annotations.



### 4.3 MapStruct mapper

For each entity `User`, S.W.O.R.D. generates `UserMapper` in:
```text
org.cheetah.fracas.mappers
```

The mapper looks like:
```java
@Mapper(componentModel = "spring")
@Generated(...)
public interface UserMapper {

    UserDto toDto(User entity);

    User toEntity(UserDto dto);
}
```

This gives you entity ↔ DTO projection as Spring beans.



## 5. Generated repositories (optional)

If you answer `y` to “Generate repositories? [y/N]”, S.W.O.R.D. generates a Spring Data repository interface for each entity.

Repositories live in:
```text
org.cheetah.fracas.repositories
```

### 5.1 Naming convention

Repositories are named in the plural form of the entity name, to reflect they manage sets/collections.

- Entity `User` → `UsersRepository`
- Entity `IncidentLog` → `IncidentLogsRepository`
- Entity `Jobs` → `JobsRepository` (already ends in `s`, no extra `s`)

Rule:
- If the entity simple name already ends with `s` or `S`, keep it.
- Otherwise append `s`.

### 5.2 Base interface

Each repository extends `JpaRepository<Entity, IdType>` and is annotated with `@Repository` and `@Generated(...)`.

Example:
```java
@Repository
@Generated(...)
public interface UsersRepository
        extends JpaRepository<User, Long> {
    ...
}
```

The `IdType` is:
- the Java type of the `@Id` field in case of single-column PK, or
- the generated `<EntityName>Id` class in case of composite PK.

### 5.3 Finder methods

For every *non-primary-key scalar* field in the entity, the repository interface includes a paginated finder method:

```java
Page<User> findByStatus(String status, Pageable pageable);

Page<User> findByEmail(String email, Pageable pageable);
```

Key details:
- Primary key fields (annotated with `@Id`) are NOT used to generate finder methods.
- Fields that are part of an `@EmbeddedId` are also NOT used.
- Relationship fields (`@ManyToOne`, `@OneToOne`, `@OneToMany`, etc.) are NOT used.
- Only direct scalar columns become finder methods.

All generated finder methods:
- return `Page<Entity>`
- take `(FieldType fieldValue, Pageable pageable)`



## 6. YAML customization

You can optionally point the wizard to a YAML file that customizes naming for tables and columns.

This file lets you define:
- the entity class name for a given DB table
- the Java property names for specific columns

### Structure

```yaml
tables:
  USERS:
    entity: User
    columns:
      ID_USER: userId
      FIRST_NAME: firstName
      LAST_NAME: lastName

  INCIDENT_LOGS:
    entity: IncidentLog
    columns:
      INCIDENT_ID: incidentId
      MESSAGE: message
      CREATED_AT: createdAt
```

Meaning:
- `tables.<TABLE_NAME>.entity`  
  Overrides the generated Java class name for that table.
  Example:  
  `USERS -> User`  
  `INCIDENT_LOGS -> IncidentLog`

- `tables.<TABLE_NAME>.columns.<COLUMN_NAME>`  
  Overrides the generated Java field name for that specific column.
  Example:
  `FIRST_NAME -> firstName` (rather than `firstName` guessed by casing rules, or something ugly like `first_name`)

Any table/column not listed in the YAML will fall back to the automatic naming rules.

### YAML file path

The wizard will ask:

```text
YAML mapping file path (optional):
```

You can pass an absolute or relative path.  
If left empty, no YAML overrides are applied.



## 7. Package layout

Suppose you chose base entity package:
```text
org.cheetah.fracas.entities
```
and output directory:
```text
/your/output/path
```

S.W.O.R.D. will generate:

Entities  
`org.cheetah.fracas.entities`
- `User`
- `UserId` (if composite PK)
- `IncidentLog`
- ...

DTOs  
`org.cheetah.fracas.dtos`
- `UserDto`
- `IncidentLogDto`
- ...

Mappers  
`org.cheetah.fracas.mappers`
- `UserMapper`
- `IncidentLogMapper`
- ...

Repositories  
`org.cheetah.fracas.repositories`
- `UsersRepository`
- `IncidentLogsRepository`
- `JobsRepository`
- ...

> Note the pluralized repository names.


## 8. Tech stack assumptions

Generated code assumes:
- Java 21+
- Spring Boot runtime
- Spring Data JPA
- Hibernate / Jakarta Persistence (`jakarta.persistence.*`)
- Lombok (for getters/setters/builders/etc.)
- MapStruct (if DTO/mappers are enabled)
- JPA `FetchType.LAZY` on collections
- JDBC drivers for the chosen database

The generator itself runs inside a Spring Boot CLI app and triggers generation on application startup via Spring events.


## 9. Typical usage flow

1. Run the JAR.
2. Pick DB vendor.
3. Enter host / port / username / password.
4. Enter logical database name.
5. Connection is tested.
6. Choose catalog/schema from the discovered metadata.
7. Enter base package and output path.
8. Choose FK mode: Scalar or Relation.
9. (If Relation) choose fetch mode: Lazy or Eager.
10. Choose whether to generate DTOs + mappers.
11. Choose whether to generate repositories.
12. Optionally provide YAML mapping file path.
13. Generation runs and writes all `.java` files to disk under the chosen path.


## 10. Limitations / notes

- Only single-column foreign keys are modeled in relationships.  
  Composite FKs currently become just scalar fields; no `@ManyToOne` is generated for them.
- Many-to-many relationships are not generated yet.
- Repositories only generate finder methods for non-PK scalar columns.
- `equals`, `hashCode`, and `toString` are restricted via Lombok's `onlyExplicitlyIncluded = true` to avoid recursion loops and massive dumps.
- Column type mapping uses heuristic rules. Some vendor-specific or exotic types might need manual cleanup.
- PostgreSQL sequence detection is based on `nextval('sequence'::regclass)` in `COLUMN_DEF`.
- JSON / JSONB detection for PostgreSQL emits `@JdbcTypeCode(SqlTypes.JSON)` accordingly, assuming Hibernate with JSON support.


---
Happy slicing. 🗡
