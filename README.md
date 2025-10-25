
# 🗡️ S.W.O.R.D. — Schema-Wide Object Reverse Designer

S.W.O.R.D. automatically generates entities, DTOs, repositories, services, resources, mappers, and controllers from an existing database schema.

---

## 1. Overview

The generator connects to a relational database and inspects metadata to produce a complete Spring Boot‑style data layer.

You can configure:
- Base package and output directory
- FK representation (scalar IDs or relations)
- Fetch strategy for relations (lazy or eager)
- Whether to generate DTOs, Repositories, Services, Controllers
- Optional YAML mapping file to control naming

---

## 2. Supported databases

| Database | Notes |
|-----------|--------|
| PostgreSQL | Default schema `public` |
| MySQL / MariaDB | Catalog-based |
| SQL Server | Default schema `dbo` |
| DB2 | Default schema `SAMPLE` |
| H2 | Default schema `PUBLIC` |

---

## 3. Entity generation rules

- Each table becomes a Java class annotated with `@Entity`.
- Composite primary keys generate a separate `@Embeddable` ID class.
- Table names are converted to singular CamelCase class names.
- Column names become lowerCamelCase fields.
- Foreign keys are represented as:
  - Scalar fields (e.g., `Long customerId`) when FK mode = SCALAR.
  - JPA relations (`@ManyToOne`, `@OneToOne`) when FK mode = RELATION.
- Optional fetch type per relation (LAZY or EAGER).

---

## 4. Naming configuration (YAML)

You can optionally provide a YAML file to customize naming for tables and columns.

Example:

```yaml
tables:
  USERS:
    entity: User
    columns:
      ID_USER: idUser
      FIRST_NAME: firstName
      LAST_NAME: lastName
```

For more advanced mapping between DTOs and Resources:

```yaml
tables:
  USERS:
    entity: User
    columns:
      FIRST_NAME:
        dto: firstName
        resource: givenName
      LAST_NAME:
        dto: lastName
        resource: familyName
```

If a structured mapping is used (with `dto:` and `resource:`), S.W.O.R.D. will generate
different field names and wire them automatically inside the generated `XResourceMapper`.

---

## 5. DTOs and mappers

Each entity has:
- A DTO class under `...dtos`
- A DTO mapper under `...mappers`

Example DTO:

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Generated(...)
public class UserDto {
    private Long idUser;
    private String firstName;
    private String lastName;
    private Boolean active;
}
```

Example mapper:

```java
@Mapper(componentModel = "spring")
@Generated(...)
public interface UserMapper {
    UserDto toDto(User entity);
    User toEntity(UserDto dto);
}
```

Rules:
- DTOs mirror entity fields (excluding `@OneToMany`, `@ManyToMany`).
- FKs are represented as scalar IDs.

---

## 6. Repositories

If enabled, each entity generates a Spring Data repository under `...repositories`.

Example:

```java
@Repository
@Generated(...)
public interface UsersRepository extends JpaRepository<User, Long> {
    Page<User> findByFirstName(String firstName, Pageable pageable);
    Page<User> findByActive(Boolean active, Pageable pageable);
}
```

- One finder per non-PK scalar column.
- PK and relation fields are excluded.
- All methods return `Page<Entity>`.

---

## 7. Services

If enabled, each entity has a corresponding Service class under `...services`.

Example:

```java
@Service
@RequiredArgsConstructor
@Generated(...)
public class UsersService {

    private final UsersRepository repository;
    private final UserMapper mapper;

    public PageDto<UserDto> findAll(int pageNumber, int maxRecordsPerPage) { ... }
    public UserDto findById(Long id) { ... } // returns null if not found
    public UserDto save(UserDto dto) { ... }
    public UserDto update(Long id, UserDto dto) { ... }
    public void delete(Long id) { ... }
}
```

A shared `PageDto<T>` class is generated:

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Generated(...)
public class PageDto<T> {
    private List<T> content;
    private int pageNumber;
    private int pageSize;
    private long totalElements;
    private int totalPages;
}
```

---

## 8. Resources and Resource Mappers

### 8.1 Resource classes

Each entity gets a REST resource representation under `...resources`.

Example:

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Generated(...)
public class UserResource {
    private Long idUser;
    private String firstName;
    private String lastName;
    private Boolean active;
}
```

### 8.2 PageResource

REST APIs return paginated responses using `PageResource<T>`:

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Generated(...)
public class PageResource<T> {
    private List<T> content;
    private int pageNumber;
    private int pageSize;
    private long totalElements;
    private int totalPages;
}
```

### 8.3 ResourceMapper

Generated per entity as `XResourceMapper`, using `default` methods:

```java
@Mapper(componentModel = "spring")
@Generated(...)
public interface UserResourceMapper {

    default UserResource toResource(UserDto dto) {
        if (dto == null) return null;
        UserResource res = new UserResource();
        res.setIdUser(dto.getIdUser());
        res.setFirstName(dto.getFirstName());
        res.setLastName(dto.getLastName());
        res.setActive(dto.getActive());
        return res;
    }

    default UserDto toDto(UserResource resource) {
        if (resource == null) return null;
        UserDto dto = new UserDto();
        dto.setIdUser(resource.getIdUser());
        dto.setFirstName(resource.getFirstName());
        dto.setLastName(resource.getLastName());
        dto.setActive(resource.getActive());
        return dto;
    }
}
```

This explicit body generation allows S.W.O.R.D. to support YAML‑defined renames between DTO and Resource fields.

---

## 9. Controllers

Each entity’s controller (e.g., `UsersController`) lives under `...controllers`.

Endpoints:

```java
// GET /api/users?pageNumber=&maxRecordsPerPage=
ResponseEntity<PageResource<UserResource>> findAll(...)

// GET /api/users/{id}
ResponseEntity<UserResource> findById(...)

// POST /api/users
ResponseEntity<UserResource> create(@RequestBody UserResource resource)

// PUT /api/users/{id}
ResponseEntity<UserResource> update(@PathVariable id, @RequestBody UserResource resource)

// DELETE /api/users/{id}
ResponseEntity<Void> delete(@PathVariable id)

// GET /api/users/by/firstName/{value}?pageNumber=&maxRecordsPerPage=
ResponseEntity<PageResource<UserResource>> findByFirstName(...)
```

Controllers delegate to their corresponding Services and handle mapping between DTOs and Resources.

---

## 10. Package layout

Example base package: `org.cheetah.fracas.entities`

```
org.cheetah.fracas.entities       → Entities
org.cheetah.fracas.dtos           → DTOs
org.cheetah.fracas.mappers        → DTO Mappers
org.cheetah.fracas.repositories   → Repositories
org.cheetah.fracas.services       → Services + PageDto
org.cheetah.fracas.resources      → Resources + PageResource + ResourceMappers
org.cheetah.fracas.controllers    → REST Controllers
```

---

## 11. Summary

S.W.O.R.D. builds an end-to-end Spring Boot structure directly from a database schema.

You can choose:
- Which layers to generate (Entity, DTO, Repository, Service, Controller)
- Whether to use scalar or relational FKs
- Lazy or eager fetch for relations
- Custom names through YAML mapping

Each generated class is annotated with `@Generated("S.W.O.R.D.")` and safely overwritable in future regenerations.
