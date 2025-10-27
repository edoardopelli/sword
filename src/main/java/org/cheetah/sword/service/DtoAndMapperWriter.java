package org.cheetah.sword.service;

import com.squareup.javapoet.*;
import org.cheetah.sword.model.FkMode;
import org.cheetah.sword.service.records.ColumnModel;
import org.cheetah.sword.service.records.EntityModel;
import org.cheetah.sword.service.records.SimpleFkModel;
import org.cheetah.sword.util.SqlTypeMapper;
import org.cheetah.sword.wizard.SwordWizard;
import org.springframework.stereotype.Component;

import javax.lang.model.element.Modifier;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.*;

/**
 * Generates:
 * - <EntityName>Dto under SwordWizard.DTO_PKG
 * - <EntityName>Mapper under SwordWizard.MAPPER_PKG
 *
 * DTO generation rules:
 * - One scalar field is generated for each physical database column in the table.
 * - Columns that participate in a composite primary key are also included as independent scalar fields.
 * - Foreign key columns are included as scalar fields.
 * - Collections (OneToMany / ManyToMany) are not part of the DTO and are not generated.
 *
 * Mapper generation rules:
 * - The mapper is generated as an interface with @Mapper(componentModel = "spring").
 * - Two abstract mapping methods are generated:
 *      <Entity>Dto toDto(<Entity> entity);
 *      <Entity>   toEntity(<Entity>Dto dto);
 * - Each mapping method is annotated with @Mappings({...}) and one @Mapping per target field.
 *   Mapping cases:
 *      1. Simple scalar column:
 *         toDto:   dto.field = entity.field
 *         toEntity: entity.field = dto.field
 *
 *      2. Composite primary key (EmbeddedId):
 *         Entity side:
 *             private <EntityName>Id id;
 *         DTO side:
 *             private <pkPart1Type> <pkPart1Name>;
 *             private <pkPart2Type> <pkPart2Name>;
 *         Mapping:
 *             toDto:     dto.pkPart = entity.id.pkPart
 *             toEntity:  entity.id = buildIdFromDto(dto)
 *         Helper buildIdFromDto(dto) is generated in the mapper when PK is composite.
 *
 *      3. Foreign key in FkMode.SCALAR:
 *         Entity side:
 *             private Long parentId;
 *         DTO side:
 *             private Long parentId;
 *         Mapping:
 *             direct 1:1 for that field (handled as a normal scalar).
 *
 *      4. Foreign key in FkMode.RELATION:
 *         Entity side:
 *             @ManyToOne / @OneToOne(...)
 *             @JoinColumn(name = "parent_id")
 *             private Category parentId; // reference to target entity
 *
 *         DTO side:
 *             private Long parentId;     // FK scalar id
 *
 *         Mapping:
 *             toDto:
 *                 dto.parentId = entity.parentId.id
 *                 (i.e. @Mapping(target="parentId", source="parentId.id"))
 *
 *             toEntity:
 *                 entity.parentId = buildParentIdFromDto(dto)
 *                 (i.e. @Mapping(target="parentId",
 *                                expression="java(buildParentIdFromDto(dto))"))
 *
 *         Helper build<RelationFieldName>FromDto(dto) is generated per FK column name,
 *         in order to avoid duplicate helper methods when multiple FKs reference the same target entity.
 *
 * Notes:
 * - The mapper interface also includes default helper methods for composite PK reconstruction
 *   and relation reconstruction.
 * - No OneToMany / ManyToMany collection mappings are generated.
 */
@Component
public class DtoAndMapperWriter {

    private final ResourceMapperWriter resourceMapperWriter;

    private final NamingConfigService namingConfigService;

    public DtoAndMapperWriter(NamingConfigService namingConfigService, ResourceMapperWriter resourceMapperWriter) {
        this.namingConfigService = namingConfigService;
        this.resourceMapperWriter = resourceMapperWriter;
    }

    /**
     * Generates the DTO type and the Mapper interface for the given entity model.
     *
     * @param rootPath output root path
     * @param model entity metadata (table name, columns, PK columns, FKs)
     * @param dbProduct database product name
     * @param entitySimpleName resolved entity simple name (CamelCase, singular)
     * @param generatedAnn @Generated annotation instance reused across generated types
     * @param fkMode foreign key generation mode (SCALAR or RELATION)
     * @param fkByLocalColumn map of local FK column name -> SimpleFkModel
     * @param embeddedPkColumns set of PK column names when the PK is composite; empty set otherwise
     */
    public void writeDtoAndMapper(
            Path rootPath,
            EntityModel model,
            String dbProduct,
            String entitySimpleName,
            AnnotationSpec generatedAnn,
            FkMode fkMode,
            Map<String, SimpleFkModel> fkByLocalColumn,
            Set<String> embeddedPkColumns
    ) throws IOException {

        boolean compositePk = embeddedPkColumns.size() > 1;
        String embeddedIdClassName = entitySimpleName + "Id";
        String embeddedIdFieldName = "id";

        // Generate DTO
        TypeSpec dtoType = buildDtoType(
                model,
                dbProduct,
                entitySimpleName,
                generatedAnn
        );

        JavaFile dtoFile = JavaFile.builder(SwordWizard.DTO_PKG, dtoType).build();
        Path dtoOut = rootPath
                .resolve(SwordWizard.DTO_PKG.replace('.', '/'))
                .resolve(dtoType.name + ".java");
        Files.createDirectories(dtoOut.getParent());
        Files.deleteIfExists(dtoOut);
        dtoFile.writeTo(rootPath);

        // Generate Mapper interface
        TypeSpec mapperType = buildMapperInterface(
                model,
                entitySimpleName,
                generatedAnn,
                fkMode,
                fkByLocalColumn,
                embeddedPkColumns,
                compositePk,
                embeddedIdClassName,
                embeddedIdFieldName
        );

        JavaFile mapperFile = JavaFile.builder(SwordWizard.MAPPER_PKG, mapperType).build();
        Path mapperOut = rootPath
                .resolve(SwordWizard.MAPPER_PKG.replace('.', '/'))
                .resolve(mapperType.name + ".java");
        Files.createDirectories(mapperOut.getParent());
        Files.deleteIfExists(mapperOut);
        mapperFile.writeTo(rootPath);
    }

    /**
     * Builds the <EntityName>Dto class.
     *
     * For each database column:
     * - A private field is generated using the naming strategy (resolveColumnName).
     * - The Java type is inferred via SqlTypeMapper.
     * - Lombok annotations are added (Data, NoArgsConstructor, AllArgsConstructor, Builder,
     *   ToString(onlyExplicitlyIncluded=true), EqualsAndHashCode(onlyExplicitlyIncluded=true)).
     * - Each field is annotated with @ToString.Include and @EqualsAndHashCode.Include.
     *
     * No JPA annotations are generated for DTOs.
     */
    private TypeSpec buildDtoType(
            EntityModel model,
            String dbProduct,
            String entitySimpleName,
            AnnotationSpec generatedAnn
    ) {

        String dtoSimpleName = entitySimpleName + "Dto";

        AnnotationSpec toStringAnn = AnnotationSpec.builder(ClassName.get("lombok", "ToString"))
                .addMember("onlyExplicitlyIncluded", "$L", true)
                .build();

        AnnotationSpec eqHashAnn = AnnotationSpec.builder(ClassName.get("lombok", "EqualsAndHashCode"))
                .addMember("onlyExplicitlyIncluded", "$L", true)
                .build();

        TypeSpec.Builder dto = TypeSpec.classBuilder(dtoSimpleName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(ClassName.get("lombok", "Data"))
                .addAnnotation(ClassName.get("lombok", "NoArgsConstructor"))
                .addAnnotation(ClassName.get("lombok", "AllArgsConstructor"))
                .addAnnotation(ClassName.get("lombok", "Builder"))
                .addAnnotation(toStringAnn)
                .addAnnotation(eqHashAnn)
                .addAnnotation(generatedAnn);

        for (ColumnModel col : model.columns().values()) {
            String physicalName = col.name();
            String fieldName = namingConfigService.resolveColumnName(model.table(), physicalName);

            TypeName javaType = SqlTypeMapper.map(
                    col.dataType(),
                    col.typeName(),
                    col.nullable(),
                    dbProduct
            );

            FieldSpec field = FieldSpec.builder(javaType, fieldName, Modifier.PRIVATE)
                    .addAnnotation(ClassName.get("lombok", "ToString").nestedClass("Include"))
                    .addAnnotation(ClassName.get("lombok", "EqualsAndHashCode").nestedClass("Include"))
                    .build();

            dto.addField(field);
        }

        return dto.build();
    }

    /**
     * Builds the <EntityName>Mapper interface.
     *
     * The generated interface contains:
     * - @Mapper(componentModel = "spring")
     * - @Generated(...)
     * - @Mappings on toDto(...) and toEntity(...)
     * - Default helper methods for composite PK reconstruction and relation reconstruction.
     *
     * Mapping behavior:
     *
     * toDto(entity):
     * - For a normal scalar column "foo":
     *       dto.foo = entity.foo
     *
     * - For a PK column that is part of a composite EmbeddedId "id":
     *       dto.foo = entity.id.foo
     *
     * - For a FK column in RELATION mode:
     *       dto.<fkLogicalField> = entity.<fkLogicalField>.id
     *   Example:
     *       entity field:  private Category parentId;
     *       dto field:     private Long parentId;
     *       mapping:       @Mapping(target="parentId", source="parentId.id")
     *
     *
     * toEntity(dto):
     * - For a normal scalar column "foo":
     *       entity.foo = dto.foo
     *
     * - For a composite PK:
     *       entity.id = buildIdFromDto(dto)
     *
     * - For a FK column in RELATION mode:
     *       entity.<fkLogicalField> = build<FKLogicalField>FromDto(dto)
     *   Example:
     *       entity field:  private Category parentId;
     *       dto field:     private Long parentId;
     *       mapping:       @Mapping(
     *                          target="parentId",
     *                          expression="java(buildParentIdFromDto(dto))"
     *                      )
     */
    private TypeSpec buildMapperInterface(
            EntityModel model,
            String entitySimpleName,
            AnnotationSpec generatedAnn,
            FkMode fkMode,
            Map<String, SimpleFkModel> fkByLocalColumn,
            Set<String> embeddedPkColumns,
            boolean compositePk,
            String embeddedIdClassName,
            String embeddedIdFieldName
    ) {

        String mapperSimpleName = entitySimpleName + "Mapper";
        String dtoSimpleName = entitySimpleName + "Dto";

        ClassName mapperAnn = ClassName.get("org.mapstruct", "Mapper");
        ClassName mappingsAnn = ClassName.get("org.mapstruct", "Mappings");
        ClassName mappingAnn = ClassName.get("org.mapstruct", "Mapping");

        ClassName entityType = ClassName.get(SwordWizard.ENTITY_PKG, entitySimpleName);
        ClassName dtoType = ClassName.get(SwordWizard.DTO_PKG, dtoSimpleName);

        ClassName embeddedIdType = compositePk
                ? ClassName.get(SwordWizard.ENTITY_PKG, embeddedIdClassName)
                : null;

        TypeSpec.Builder mapper = TypeSpec.interfaceBuilder(mapperSimpleName)
                .addModifiers(Modifier.PUBLIC)
                .addAnnotation(
                        AnnotationSpec.builder(mapperAnn)
                                .addMember("componentModel", "$S", "spring")
                                .build()
                )
                .addAnnotation(generatedAnn);

        /*
         * Build @Mapping annotations for toDto().
         *
         * For each DTO field (derived from each DB column):
         *
         * - If PK is composite and this column is part of PK:
         *       @Mapping(target="<pkField>", source="id.<pkField>")
         *
         * - Else if fkMode == RELATION and this column is a FK:
         *       @Mapping(target="<fkField>", source="<fkField>.id")
         *   Note: <fkField> is the logical property name that appears both in the entity
         *   as relation field and in the DTO as scalar FK id.
         *
         * - Else:
         *       @Mapping(target="<field>", source="<field>")
         */
        List<AnnotationSpec> toDtoFieldMappings = new ArrayList<>();

        for (String colName : model.columns().keySet()) {
            String logicalFieldName = namingConfigService.resolveColumnName(model.table(), colName);
            boolean isPkCol = embeddedPkColumns.contains(colName);

            if (compositePk && isPkCol) {
                String sourcePath = embeddedIdFieldName + "." + logicalFieldName;
                AnnotationSpec m = AnnotationSpec.builder(mappingAnn)
                        .addMember("target", "$S", logicalFieldName)
                        .addMember("source", "$S", sourcePath)
                        .build();
                toDtoFieldMappings.add(m);
                continue;
            }

            if (fkMode == FkMode.RELATION && fkByLocalColumn.containsKey(colName)) {
                // dto.logicalFieldName = entity.logicalFieldName.id
                String sourcePath = logicalFieldName + ".id";

                AnnotationSpec m = AnnotationSpec.builder(mappingAnn)
                        .addMember("target", "$S", logicalFieldName)
                        .addMember("source", "$S", sourcePath)
                        .build();
                toDtoFieldMappings.add(m);
                continue;
            }

            // direct scalar mapping
            AnnotationSpec m = AnnotationSpec.builder(mappingAnn)
                    .addMember("target", "$S", logicalFieldName)
                    .addMember("source", "$S", logicalFieldName)
                    .build();
            toDtoFieldMappings.add(m);
        }

        /*
         * Build @Mapping annotations for toEntity().
         *
         * - For normal scalar fields:
         *       @Mapping(target="<field>", source="<field>")
         *
         * - For composite PK:
         *       @Mapping(target="id", expression="java(buildIdFromDto(dto))")
         *
         * - For RELATION-mode FK fields:
         *       @Mapping(target="<fkField>", expression="java(build<FkField>FromDto(dto))")
         */
        List<AnnotationSpec> toEntityFieldMappings = new ArrayList<>();

        for (String colName : model.columns().keySet()) {
            String logicalFieldName = namingConfigService.resolveColumnName(model.table(), colName);
            boolean isPkCol = embeddedPkColumns.contains(colName);

            if (compositePk && isPkCol) {
                // composite PK handled once via buildIdFromDto(dto)
                continue;
            }

            if (fkMode == FkMode.RELATION && fkByLocalColumn.containsKey(colName)) {
                // handled below with expression
                continue;
            }

            AnnotationSpec m = AnnotationSpec.builder(mappingAnn)
                    .addMember("target", "$S", logicalFieldName)
                    .addMember("source", "$S", logicalFieldName)
                    .build();
            toEntityFieldMappings.add(m);
        }

        if (compositePk) {
            for (String pkCol : embeddedPkColumns) {
                String pkField = namingConfigService.resolveColumnName(model.table(), pkCol);

                // id.<field> = dto.<field>
                String targetPath = embeddedIdFieldName + "." + pkField;
                System.out.println("TargetPath: "+targetPath);
                System.out.println("SourcePath: "+pkField);
                
                AnnotationSpec m = AnnotationSpec.builder(mappingAnn)
                        .addMember("target", "$S", targetPath)
                        .addMember("source", "$S", pkField)
                        .build();

                toEntityFieldMappings.add(m);
            }
        }

        if (fkMode == FkMode.RELATION && !fkByLocalColumn.isEmpty() && !compositePk) {
            for (Map.Entry<String, SimpleFkModel> entry : fkByLocalColumn.entrySet()) {
                String localCol = entry.getKey();
                SimpleFkModel fk = entry.getValue();

                // logical name for this FK column, used:
                // - as DTO scalar field name
                // - as entity relation field name
                String logicalFieldName = namingConfigService.resolveColumnName(model.table(), localCol);

                // create expression using helper build<LogicalFieldName>FromDto(dto)
                String helperName = "build" + upperFirst(logicalFieldName) + "FromDto";

                AnnotationSpec m = AnnotationSpec.builder(mappingAnn)
                        .addMember("target", "$S", logicalFieldName)
                        .addMember("expression", "$S", "java(" + helperName + "(dto))")
                        .build();
                toEntityFieldMappings.add(m);
            }
        }

        AnnotationSpec toDtoMappingsAnn = AnnotationSpec.builder(mappingsAnn)
                .addMember("value", buildArrayInitializer(mappingAnn, toDtoFieldMappings))
                .build();

        MethodSpec toDtoMethod = MethodSpec.methodBuilder("toDto")
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .addAnnotation(toDtoMappingsAnn)
                .returns(dtoType)
                .addParameter(ParameterSpec.builder(entityType, "entity").build())
                .build();

        AnnotationSpec toEntityMappingsAnn = AnnotationSpec.builder(mappingsAnn)
                .addMember("value", buildArrayInitializer(mappingAnn, toEntityFieldMappings))
                .build();

        MethodSpec toEntityMethod = MethodSpec.methodBuilder("toEntity")
                .addModifiers(Modifier.PUBLIC, Modifier.ABSTRACT)
                .addAnnotation(toEntityMappingsAnn)
                .returns(entityType)
                .addParameter(ParameterSpec.builder(dtoType, "dto").build())
                .build();

        mapper.addMethod(toDtoMethod);
        mapper.addMethod(toEntityMethod);

        /*
         * Helper default method for composite primary key reconstruction.
         *
         * Generates:
         *
         * default <EmbeddedIdType> buildIdFromDto(<DtoType> dto) {
         *     <EmbeddedIdType> idObj = new <EmbeddedIdType>();
         *     idObj.setPkField(dto.getPkField());
         *     ...
         *     return idObj;
         * }
         */
//        if (compositePk && embeddedIdType != null) {
//            MethodSpec.Builder idBuilderMethod = MethodSpec.methodBuilder("buildIdFromDto")
//                    .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
//                    .returns(embeddedIdType)
//                    .addParameter(dtoType, "dto")
//                    .addStatement("$T idObj = new $T()", embeddedIdType, embeddedIdType);
//
//            for (String pkCol : embeddedPkColumns) {
//                String pkField = namingConfigService.resolveColumnName(model.table(), pkCol);
//                String setter = "set" + upperFirst(pkField);
//                String getter = "get" + upperFirst(pkField);
//                idBuilderMethod.addStatement("idObj.$L(dto.$L())", setter, getter);
//            }
//
//            idBuilderMethod.addStatement("return idObj");
//            mapper.addMethod(idBuilderMethod.build());
//        }

        /*
         * Helper default methods for building relation stubs in RELATION mode.
         *
         * For each FK column (single-column FK):
         *
         * default Category buildParentIdFromDto(CategoryDto dto) {
         *     if (dto.getParentId() == null) return null;
         *     Category rel = new Category();
         *     rel.setId(dto.getParentId());
         *     return rel;
         * }
         *
         * Method names are based on the logical FK field name, so that two different
         * FK columns referencing the same target entity produce different helper names.
         */
        if (fkMode == FkMode.RELATION && !fkByLocalColumn.isEmpty()) {
            for (Map.Entry<String, SimpleFkModel> entry : fkByLocalColumn.entrySet()) {
                String localCol = entry.getKey();
                SimpleFkModel fk = entry.getValue();

                String logicalFieldName = namingConfigService.resolveColumnName(model.table(), localCol);
                String helperName = "build" + upperFirst(logicalFieldName) + "FromDto";

                ClassName relationType = ClassName.get(
                        SwordWizard.ENTITY_PKG,
                        namingConfigService.resolveEntityName(fk.targetTable())
                );

                String dtoGetter = "get" + upperFirst(logicalFieldName);
                String relSetterId = "setId";

                MethodSpec helper = MethodSpec.methodBuilder(helperName)
                        .addModifiers(Modifier.PUBLIC, Modifier.DEFAULT)
                        .returns(relationType)
                        .addParameter(ParameterSpec.builder(dtoType, "dto").build())
                        .beginControlFlow("if (dto.$L() == null)", dtoGetter)
                        .addStatement("return null")
                        .endControlFlow()
                        .addStatement("$T rel = new $T()", relationType, relationType)
                        .addStatement("rel.$L(dto.$L())", relSetterId, dtoGetter)
                        .addStatement("return rel")
                        .build();

                mapper.addMethod(helper);
            }
        }

        return mapper.build();
    }

    /**
     * Builds the CodeBlock for @Mappings.value = { @Mapping(...), @Mapping(...), ... }.
     * If there are no mappings, an empty array literal "{}" is emitted.
     */
    private CodeBlock buildArrayInitializer(ClassName mappingAnn, List<AnnotationSpec> mappings) {
        CodeBlock.Builder b = CodeBlock.builder();
        b.add("{");
        for (int i = 0; i < mappings.size(); i++) {
            b.add("\n  $L", mappings.get(i));
            if (i < mappings.size() - 1) {
                b.add(",");
            }
        }
        if (!mappings.isEmpty()) {
            b.add("\n");
        }
        b.add("}");
        return b.build();
    }

    private static String upperFirst(String s) {
        if (s == null || s.isBlank()) return s;
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    @SuppressWarnings("unused")
    private static String nowIso() {
        return OffsetDateTime.now().toString();
    }
}