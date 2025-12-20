package org.cheetah.sword.config;

import java.util.Map;

/**
 * Represents the optional external YAML configuration used to customize
 * generated names and structural decisions.
 *
 * Only explicitly provided values in the YAML override defaults.
 * Missing values must NOT change the default generation behavior.
 *
 * Structure expected (example):
 *
 * entities:
 *   order_discount:
 *     entityName: OrderDiscount
 *     dtoName: OrderDiscountDto
 *     resourceName: OrderDiscountResource
 *     fields:
 *       order_id:
 *         propertyName: orderId
 *         resourceName: orderIdentifier
 *
 * controllers:
 *   basePath: /api
 *   crossOriginAll: true
 *
 * relations:
 *   fkMode: RELATION
 *   relationFetch: LAZY
 *
 * repositories.generate: true
 * services.generate: true
 * controllersGeneration.generate: true
 */
public class YamlGenerationConfig {

    /**
     * Map of tableName -> EntityOverride
     */
    private Map<String, EntityOverride> entities;

    /**
     * Global controller-related overrides.
     */
    private ControllersOverride controllers;

    /**
     * Global FK / relation strategy overrides.
     */
    private RelationsOverride relations;

    /**
     * Repository generation toggle overrides.
     */
    private GenerationToggle repositories;

    /**
     * Service generation toggle overrides.
     */
    private GenerationToggle services;

    /**
     * Controller generation toggle overrides.
     */
    private GenerationToggle controllersGeneration;

    // --- getters / setters ---

    public Map<String, EntityOverride> getEntities() {
        return entities;
    }

    public void setEntities(Map<String, EntityOverride> entities) {
        this.entities = entities;
    }

    public ControllersOverride getControllers() {
        return controllers;
    }

    public void setControllers(ControllersOverride controllers) {
        this.controllers = controllers;
    }

    public RelationsOverride getRelations() {
        return relations;
    }

    public void setRelations(RelationsOverride relations) {
        this.relations = relations;
    }

    public GenerationToggle getRepositories() {
        return repositories;
    }

    public void setRepositories(GenerationToggle repositories) {
        this.repositories = repositories;
    }

    public GenerationToggle getServices() {
        return services;
    }

    public void setServices(GenerationToggle services) {
        this.services = services;
    }

    public GenerationToggle getControllersGeneration() {
        return controllersGeneration;
    }

    public void setControllersGeneration(GenerationToggle controllersGeneration) {
        this.controllersGeneration = controllersGeneration;
    }

    /**
     * Per-table overrides.
     */
    public static class EntityOverride {
        /**
         * Custom simple name for the entity class.
         * Example: "OrderDiscount"
         */
        private String entityName;

        /**
         * Custom simple name for the DTO class.
         * Example: "OrderDiscountDto"
         */
        private String dtoName;

        /**
         * Custom simple name for the Resource class.
         * Example: "OrderDiscountResource"
         */
        private String resourceName;

        /**
         * Overrides for individual columns.
         * Key: physical column name in DB (e.g. "order_id")
         */
        private Map<String, FieldOverride> fields;

        // getters / setters

        public String getEntityName() {
            return entityName;
        }

        public void setEntityName(String entityName) {
            this.entityName = entityName;
        }

        public String getDtoName() {
            return dtoName;
        }

        public void setDtoName(String dtoName) {
            this.dtoName = dtoName;
        }

        public String getResourceName() {
            return resourceName;
        }

        public void setResourceName(String resourceName) {
            this.resourceName = resourceName;
        }

        public Map<String, FieldOverride> getFields() {
            return fields;
        }

        public void setFields(Map<String, FieldOverride> fields) {
            this.fields = fields;
        }
    }

    /**
     * Per-column overrides.
     */
    public static class FieldOverride {
        /**
         * Custom Java property name for Entity/DTO.
         * Example: "parentId"
         */
        private String propertyName;

        /**
         * Custom field name for the exposed Resource class / API contract.
         * Example: "parentCategoryId"
         */
        private String resourceName;

        // getters / setters

        public String getPropertyName() {
            return propertyName;
        }

        public void setPropertyName(String propertyName) {
            this.propertyName = propertyName;
        }

        public String getResourceName() {
            return resourceName;
        }

        public void setResourceName(String resourceName) {
            this.resourceName = resourceName;
        }
    }

    /**
     * Global controller overrides.
     */
    public static class ControllersOverride {
        /**
         * Base path for REST endpoints, e.g. "/api".
         */
        private String basePath;

        /**
         * Whether to generate @CrossOrigin("*") on controllers.
         */
        private Boolean crossOriginAll;

        // getters / setters

        public String getBasePath() {
            return basePath;
        }

        public void setBasePath(String basePath) {
            this.basePath = basePath;
        }

        public Boolean getCrossOriginAll() {
            return crossOriginAll;
        }

        public void setCrossOriginAll(Boolean crossOriginAll) {
            this.crossOriginAll = crossOriginAll;
        }
    }

    /**
     * Global FK / fetch overrides.
     */
    public static class RelationsOverride {
        /**
         * "SCALAR" or "RELATION"
         */
        private String fkMode;

        /**
         * "LAZY" or "EAGER"
         */
        private String relationFetch;

        // getters / setters

        public String getFkMode() {
            return fkMode;
        }

        public void setFkMode(String fkMode) {
            this.fkMode = fkMode;
        }

        public String getRelationFetch() {
            return relationFetch;
        }

        public void setRelationFetch(String relationFetch) {
            this.relationFetch = relationFetch;
        }
    }

    /**
     * Generic yes/no toggle + optional suffix customization for generated layers.
     */
    public static class GenerationToggle {
        /**
         * Whether to generate this layer (repository/service/controller).
         */
        private Boolean generate;

        /**
         * Optional custom suffix for generated class names.
         * Example: "Repo" instead of "Repository".
         * Example: "Manager" instead of "Service".
         */
        private String suffix;

        // getters / setters

        public Boolean getGenerate() {
            return generate;
        }

        public void setGenerate(Boolean generate) {
            this.generate = generate;
        }

        public String getSuffix() {
            return suffix;
        }

        public void setSuffix(String suffix) {
            this.suffix = suffix;
        }
    }
}