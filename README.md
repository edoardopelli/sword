# 🗡️ S.W.O.R.D.

**S.W.O.R.D. — Schema-Wide Object Reverse Designer**

S.W.O.R.D. is a command-line tool that performs **reverse engineering** of relational databases into **JPA entities**.  
It automatically inspects a live database schema, infers table relationships, detects composite keys, and generates well-structured, annotated entity classes (optionally with Lombok, `@Embeddable` IDs, and Hibernate JSON mappings).

---

## ⚙️ Acronym Meaning

> **S.W.O.R.D.** stands for **Schema-Wide Object Reverse Designer**

The name reflects its purpose:  
it *cuts through* relational complexity to craft clean, object-oriented representations of your data model.

---

## 🚀 Features

- **Interactive CLI Wizard** — guides you step-by-step to connect to your database and generate entities.  
- **Multi-database support**:
  - PostgreSQL
  - MariaDB
  - MySQL
  - Microsoft SQL Server
  - H2
  - IBM DB2
- **Automatic detection** of:
  - Schemas and catalogs (based on the DB type)
  - Primary and composite keys (`@Id` / `@EmbeddedId`)
  - Foreign keys (`@ManyToOne`, `@OneToMany`)
  - JSON / JSONB columns (`Map<String,Object>` with `@JdbcTypeCode(SqlTypes.JSON)`)
- **Type-safe mapping** from SQL types to Java types (`VARCHAR → String`, `TIMESTAMP → OffsetDateTime`, etc.)
- **Customizable output**:
  - Choose base package (e.g. `com.example.entities`)
  - Choose target path (defaults to the current working directory)
  - Optional Lombok integration
- **Clean, compilable output** ready to plug into Spring Boot or Jakarta EE projects.

---

## 🧭 Getting Started

### 1. Build

```bash
mvn clean package
```

This produces a runnable JAR file under `target/sword.jar`.

### 2. Run the Tool

```bash
java -jar target/sword.jar
```

You’ll be guided through an **interactive setup**:

1. **Select JDBC driver**
   ```
   [1] PostgreSQL
   [2] MariaDB
   [3] MySQL
   [4] Microsoft SQL Server
   [5] H2
   [6] IBM DB2
   Choose database: 1
   ```

2. **Enter connection details**  
   Example for PostgreSQL:
   ```
   Host [default: localhost]: 
   Port [default: 5432]: 
   Username: myuser
   Password: ********
   ```

3. **Select schema/catalog**  
   Depending on the chosen database, S.W.O.R.D. will display available **schemas** or **catalogs**.  
   ```
   Available schemas:
   [1] public
   [2] audit
   Choose schema: 1
   ```

4. **Define base package and output path**  
   ```
   Base package [default: com.example.entities]:
   Output path [default: /path/where/you/launched/sword]:
   ```

5. **Confirm and generate**
   ```
   Generating entities from schema 'public'...
   ✓ ProblemsIncidents.java
   ✓ ProblemsIncidentsId.java
   ✓ Incident.java
   ✓ FailureMode.java
   ...
   Generation complete. 42 entities created.
   ```


## Entity naming

By default, S.W.O.R.D. generates one JPA entity per physical table.

### Default naming strategy

For each table name, S.W.O.R.D. creates a Java class name by:
1. Splitting the table name on underscores / non-alphanumeric characters.
2. Singularizing each chunk in a simple English-ish way (e.g. `users` → `user`, `profiles` → `profile`, `companies` → `company`, `batches` → `batch`).
3. Capitalizing and concatenating the chunks.


The same naming rule is also used to build the `<EntityName>Id` class for composite primary keys.  
So a table called `user_profiles` will generate:
- `UserProfile.java`
- `UserProfileId.java` (if the PK is composite)


### Overriding names with `sword.yml`

You can override the generated class names on a per-table basis without touching code.

Create a file called `sword.yml` **in the same working directory where you run `java -jar sword-...jar`**.

Example `sword.yml`:

```yaml
tables:
  fracas_events: Event
  app_users: AccountUser
  company_branches: Branch
---

## 🏗️ Output Example

Generated entities are stored under your chosen package path:

```
src/
 └─ main/
     └─ java/
         └─ com/
             └─ example/
                 └─ entities/
                     ├─ ProblemsIncidents.java
                     ├─ ProblemsIncidentsId.java
                     ├─ Incident.java
                     └─ FailureMode.java
```

A sample entity:

```java
@Entity
@Table(name = "problems_incidents")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProblemsIncidents {

    @EmbeddedId
    private ProblemsIncidentsId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "incident_id", insertable = false, updatable = false)
    private Incident incident;

    @Column(name = "details", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private Map<String, Object> details;
}
```

---

## 🧩 Planned Enhancements

- [ ] Generate repository interfaces (`JpaRepository`)
- [ ] Generate DTOs and MapStruct mappers
- [ ] Add command-line flags for non-interactive mode
- [ ] Add YAML configuration file for reusable setups
- [ ] Support automatic relation inference for bridge tables
- [ ] Add template engine (Freemarker/Velocity) for custom code style

---

## 🧠 Tech Stack

| Component | Library / Framework |
|------------|--------------------|
| CLI        | [picocli](https://picocli.info) |
| Code Generation | [JavaPoet](https://github.com/square/javapoet) |
| JDBC Metadata | Standard JDBC API |
| Optional JSON Serialization | Jackson |
| Supported Databases | PostgreSQL, MariaDB, MySQL, MSSQL, H2, DB2 |

---

## 🛠️ Example CLI Usage (non-interactive mode, future feature)

```bash
java -jar sword.jar   --db postgres   --host localhost   --port 5432   --user myuser   --pass mypass   --schema public   --package com.example.entities   --out ./generated
```

---

## 🏴‍☠️ Project Structure

```
sword/
 ├─ src/main/java/org/cheetah/sword/
 │   ├─ Main.java               → CLI entry point
 │   ├─ SwordWizard.java        → Interactive setup
 │   ├─ MetadataReader.java     → Extracts table/column metadata
 │   ├─ EntityGenerator.java    → Generates JPA entities
 │   ├─ SqlTypeMapper.java      → Maps SQL → Java types
 │   └─ util/
 │       ├─ NamingUtils.java
 │       └─ JsonTypeHandler.java
 ├─ pom.xml
 ├─ README.md
 └─ LICENSE
```

---

## 🧑‍💻 Author

**S.W.O.R.D.** is developed and maintained by the **Cheetah Engineering Team**  
Lead developer: *Edoardo Pelli*

---

## 🧷 License

MIT License — you are free to use, modify, and distribute this software with attribution.

---

## 🗡️ Motto

> *"Cut through the schema — forge your entities with S.W.O.R.D."*
