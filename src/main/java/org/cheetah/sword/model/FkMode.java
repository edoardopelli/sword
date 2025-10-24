package org.cheetah.sword.model;

/**
 * How to generate foreign keys in the generated entities.
 *
 * SCALAR   -> keep FK columns as plain scalar fields (e.g. Long customerId)
 * RELATION -> generate @ManyToOne relations (e.g. Customer customer),
 *             when it's a simple (single-column, non-PK) FK.
 */
public enum FkMode {
    SCALAR,
    RELATION
}