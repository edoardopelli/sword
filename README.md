# S.W.O.R.D. — Schema-Wide Object Reverse Designer

S.W.O.R.D. è un tool **CLI** in **Java 21 + Spring Boot** che esegue reverse engineering di uno schema SQL e genera:

- un file **YAML** che descrive il modello del database (**senza** informazioni di connessione)
- codice Java: **JPA Entities (Jakarta)**, **DTO**, **Resources**, **Repositories**, **Services**, **Controllers** e **MapStruct mappers**
- supporto **PK composite** con `@EmbeddedId`
- supporto PostgreSQL **json/jsonb** (binding corretto in insert/update)

---

## Come funziona (overview)

Il flusso generato è:

```
Controller (Resource API)
  -> Mapper (Resource <-> DTO)   [MapStruct]
    -> Service (DTO)
      -> Mapper (Entity <-> DTO) [MapStruct]
        -> Repository (Entity)   [Spring Data JPA]
          -> DB
```

- Il **Repository** lavora su **Entity**
- Il **Service** espone **DTO**
- Il **Controller** espone **Resource**
- I **Mapper** trasformano gli oggetti tra i layer

---

## Naming e regole principali

### snake_case -> camelCase
Se una colonna è `customer_id` la property Java diventa `customerId`.

### Relazioni
- Le relazioni **ManyToOne** e **OneToOne** sono generate nelle **Entity** con fetch configurabile (default `LAZY`).
- In **DTO/Resource**:
  - **NON** vengono mappate `OneToMany` e `ManyToMany`
  - in caso di FK, nel DTO/Resource vengono riportati **gli id** (campi FK) e non l’oggetto referenziato

### Primary key
- PK singola: `@Id`
- PK composta: `@EmbeddedId` + classe `<TableName>Id` `@Embeddable`

### Auto-increment / identity / sequence
Per tabelle con PK singola, il tool può persistere in YAML gli hint:
- `idGeneration: NONE | IDENTITY | SEQUENCE`
- `sequenceName` quando `SEQUENCE`

Così la pipeline **YAML → Code** non perde la strategia di generazione.

---

## Supporto PostgreSQL json/jsonb

Per colonne con `jdbcTypeName` `json` o `jsonb`:
- tipo Java in **Entity/DTO/Resource**: `Map<String, Object>`
- in Entity vengono aggiunte:
  - `@Column(columnDefinition = "jsonb")` (o `"json"`)
  - `@JdbcTypeCode(SqlTypes.JSON)` (Hibernate 6)

Questo evita errori del tipo:

> column "metadata" is of type jsonb but expression is of type character varying

---

## Modalità di utilizzo

### 1) DB → (YAML opzionale) → Code
- Il tool chiede i parametri di connessione
- Si connette al DB e mostra l’elenco degli **schema** (se disponibile)
- Dopo la scelta dello schema, mostra l’elenco dei **catalog** (se disponibile)
- Introspeziona tabelle/colonne/PK/FK
- Opzionalmente:
  - scrive lo YAML
  - genera subito il codice

### 2) YAML → Code (senza connessione al DB per generare)
- Fornisci un file YAML
- Il tool genera il codice usando lo YAML come sorgente di verità
- Lo YAML **non** contiene credenziali o URL di connessione

---

## Build del tool

Requisiti:
- Java 21+
- Maven 3.9+

Build:

```bash
mvn clean install -DskipTests
```

Esecuzione:

```bash
java -jar target/sword-0.1.0-SNAPSHOT.jar
```

---

## Wizard CLI: cosa chiede

### Avvio
All’avvio il wizard chiede se hai già uno YAML:

- `N` → modalità DB → YAML/Code
- `Y` → modalità YAML → Code

### Modalità DB
1. DB type (PostgreSQL / MariaDB / MySQL / …)
2. host / port (default per DB) / database / username / password (password non visibile)
3. dopo la connessione:
   - elenco **schema** (scegli per numero o nome)
   - elenco **catalog** (scegli per numero o nome)
4. table pattern (`%` default)
5. include views (Y/N)
6. output directory + base package
7. scrittura YAML (opzionale)
8. generazione codice (opzionale)

### Modalità YAML
1. path YAML
2. output directory (default da YAML se presente)
3. base package (default da YAML)
4. generazione codice

---

## YAML: struttura essenziale

Lo YAML descrive:
- `model` (basePackage, schema/catalog usati per la generazione JPA)
- `tables` (columns, PK, FK)
- `naming` (override per entity/dto/resource e per property names)
- `resourceOverrides` (override opzionali lato resource: rename e/o type)

Esempio minimale (estratto):

```yaml
model:
  basePackage: org.example.generated
  schema: ecommerce

tables:
  - name: reviews
    primaryKeyColumns: [ id ]
    columns:
      - name: id
        propertyName: id
        jdbcTypeName: int4
        nullable: false
        autoIncrement: true
        idGeneration: IDENTITY
      - name: metadata
        propertyName: metadata
        jdbcTypeName: jsonb
        nullable: true
```

> Importante: nello YAML **non** ci sono informazioni di connessione al DB.

---

## Output generato

Il tool genera (in base al tuo `basePackage`):
- Entities + EmbeddedId
- DTO
- Resource
- Repository (Spring Data JPA)
- Service (DTO)
- Controller (Resource)
- MapStruct mappers (Entity<->DTO, DTO<->Resource)

### Endpoint REST
- PK singola:
  - `GET /{resource}/{id}`
  - `PUT /{resource}/{id}`
  - `DELETE /{resource}/{id}`
- PK composta:
  - `GET /{resource}/{pk1}/{pk2} ...`
  - `PUT /{resource}/{pk1}/{pk2} ...`
  - `DELETE /{resource}/{pk1}/{pk2} ...`

---

## Troubleshooting

### 1) JSONB: “expression is of type character varying”
Verifica che nella Entity generata sul campo json/jsonb ci siano:
- `@Column(columnDefinition = "jsonb")`
- `@JdbcTypeCode(SqlTypes.JSON)`
e che il tipo Java sia `Map<String, Object>`.

### 2) Spring Boot prova ad autoconfigurare il DataSource del tool
Se vedi errori come:
> Failed to configure a DataSource: 'url' attribute is not specified

assicurati che il tool non abbia configurazioni `spring.datasource.*` obbligatorie e che il wizard crei il DataSource **programmaticamente**.

### 3) “no main manifest attribute”
Costruisci il jar con Maven (Spring Boot repackage):

```bash
mvn clean package
java -jar target/sword-0.1.0-SNAPSHOT.jar
```

---

## Note
- La distinzione tra **schema** e **catalog** varia tra DB e driver JDBC: il wizard prova a listare entrambi con `DatabaseMetaData` e mantiene fallback manuale.
- La determinazione ONE_TO_ONE vs MANY_TO_ONE è best-effort (dipende da PK/unique indexes e metadati del driver).
