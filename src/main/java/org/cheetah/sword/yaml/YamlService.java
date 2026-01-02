package org.cheetah.sword.yaml;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import org.cheetah.sword.model.ColumnModel;
import org.cheetah.sword.model.DbModel;
import org.cheetah.sword.model.ForeignKeyModel;
import org.cheetah.sword.model.IdGeneration;
import org.cheetah.sword.model.RelationCardinality;
import org.cheetah.sword.model.TableModel;
import org.cheetah.sword.util.NameUtil;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

@Service
public class YamlService {

    private final ObjectMapper mapper = new ObjectMapper(new YAMLFactory());

    public void write(Path path, String basePackage, DbModel dbModel, String outputDir) {
        YamlSpec spec = new YamlSpec();

        YamlSpec.Model model = new YamlSpec.Model();
        model.setBasePackage(basePackage);
        model.setSchema(dbModel.getSchema());
        model.setCatalog(dbModel.getCatalog());
        spec.setModel(model);

        YamlSpec.Generation gen = new YamlSpec.Generation();
        gen.setOutputDir(outputDir);
        spec.setGeneration(gen);

        // Write naming defaults so user can edit later.
        YamlSpec.Naming naming = new YamlSpec.Naming();
        spec.setNaming(naming);

        for (TableModel t : dbModel.getTables()) {
            YamlSpec.Table yt = new YamlSpec.Table();
            yt.setName(t.getName());
            yt.setPrimaryKeyColumns(t.getPrimaryKeyColumns());

            for (ColumnModel c : t.getColumns()) {
                YamlSpec.Column yc = new YamlSpec.Column();
                yc.setName(c.getName());
                yc.setPropertyName(c.getPropertyName());
                yc.setJdbcType(c.getJdbcType());
                yc.setJdbcTypeName(c.getJdbcTypeName());
                yc.setNullable(c.isNullable());
                yc.setSize(c.getSize());
                yc.setScale(c.getScale());

                // Persist ID generation hints (optional).
                yc.setAutoIncrement(c.isAutoIncrement());
                yc.setIdGeneration(c.getIdGeneration() != null ? c.getIdGeneration().name() : null);
                yc.setSequenceName(c.getSequenceName());

                yt.getColumns().add(yc);
            }

            if (t.getForeignKeys() != null) {
                for (ForeignKeyModel fk : t.getForeignKeys()) {
                    YamlSpec.ForeignKey yfk = new YamlSpec.ForeignKey();
                    yfk.setName(fk.getName());
                    yfk.setFromColumns(fk.getFromColumns());
                    yfk.setToTable(fk.getToTable());
                    yfk.setToColumns(fk.getToColumns());
                    yfk.setCardinality(fk.getCardinality().name());
                    yt.getForeignKeys().add(yfk);
                }
            }

            spec.getTables().add(yt);

            // Defaults: <Table>Entity / <Table>DTO / <Table>Resource
            YamlSpec.TableNaming tn = new YamlSpec.TableNaming();
            String base = NameUtil.toUpperCamel(t.getName());
            tn.setEntityName(base + "Entity");
            tn.setDtoName(base + "DTO");
            tn.setResourceName(base + "Resource");

            // Default column mappings (db column -> java property)
            for (ColumnModel c : t.getColumns()) {
                tn.getColumns().put(c.getName(), c.getPropertyName());
            }

            naming.getTables().put(t.getName(), tn);
        }

        try {
            Files.createDirectories(path.getParent() == null ? Path.of(".") : path.getParent());
            mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), spec);
        } catch (Exception ex) {
            throw new IllegalStateException("YAML write failed: " + path, ex);
        }
    }

    public YamlSpec read(Path path) {
        try {
            YamlSpec spec = mapper.readValue(path.toFile(), YamlSpec.class);
            validate(spec);
            return spec;
        } catch (Exception ex) {
            throw new IllegalStateException("YAML read failed: " + path, ex);
        }
    }

    public void validate(YamlSpec spec) {
        if (spec == null || spec.getModel() == null) {
            throw new IllegalArgumentException("Invalid YAML: missing model section.");
        }
        if (spec.getModel().getBasePackage() == null || spec.getModel().getBasePackage().isBlank()) {
            throw new IllegalArgumentException("Invalid YAML: model.basePackage is required.");
        }
        if (spec.getTables() == null) {
            throw new IllegalArgumentException("Invalid YAML: tables section is required.");
        }
    }

    public DbModel toDbModel(YamlSpec spec) {
        DbModel.DbModelBuilder db = DbModel.builder()
                .schema(spec.getModel().getSchema())
                .catalog(spec.getModel().getCatalog());

        for (YamlSpec.Table t : spec.getTables()) {
            TableModel.TableModelBuilder tb = TableModel.builder()
                    .name(t.getName())
                    .primaryKeyColumns(t.getPrimaryKeyColumns());

            for (YamlSpec.Column c : t.getColumns()) {
                IdGeneration idGen = parseIdGeneration(c.getIdGeneration(), t.getName(), c.getName());
                String seqName = (idGen == IdGeneration.SEQUENCE) ? c.getSequenceName() : null;

                tb.column(ColumnModel.builder()
                        .name(c.getName())
                        .propertyName(c.getPropertyName() != null && !c.getPropertyName().isBlank()
                                ? c.getPropertyName()
                                : NameUtil.toLowerCamel(c.getName()))
                        .jdbcType(c.getJdbcType())
                        .jdbcTypeName(c.getJdbcTypeName())
                        .nullable(c.isNullable())
                        .size(c.getSize())
                        .scale(c.getScale())

                        // Restore ID generation hints (optional).
                        .autoIncrement(c.isAutoIncrement())
                        .idGeneration(idGen)
                        .sequenceName(seqName)

                        .build());
            }

            if (t.getForeignKeys() != null) {
                for (YamlSpec.ForeignKey fk : t.getForeignKeys()) {
                    tb.foreignKey(ForeignKeyModel.builder()
                            .name(fk.getName())
                            .fromColumns(fk.getFromColumns())
                            .toTable(fk.getToTable())
                            .toColumns(fk.getToColumns())
                            .cardinality(RelationCardinality.valueOf(fk.getCardinality()))
                            .build());
                }
            }

            db.table(tb.build());
        }

        return db.build();
    }

    private static IdGeneration parseIdGeneration(String raw, String tableName, String columnName) {
        if (raw == null || raw.isBlank()) {
            return IdGeneration.NONE;
        }
        String v = raw.trim().toUpperCase(Locale.ROOT);
        try {
            return IdGeneration.valueOf(v);
        } catch (Exception ex) {
            throw new IllegalArgumentException(
                    "Invalid YAML: unsupported idGeneration '" + raw + "' for " + tableName + "." + columnName
                            + ". Allowed values: NONE, IDENTITY, SEQUENCE",
                    ex);
        }
    }
}