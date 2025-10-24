package org.cheetah.sword.model;

/**
 * Controls the fetch strategy for @ManyToOne and @OneToOne relations.
 */
public enum RelationFetch {
    LAZY,
    EAGER
}