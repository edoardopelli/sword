package com.example.entities;

import jakarta.annotation.Generated;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.lang.Integer;
import java.lang.Long;
import java.lang.String;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(
    name = "pbs"
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(
    onlyExplicitlyIncluded = true
)
@EqualsAndHashCode(
    onlyExplicitlyIncluded = true
)
@Generated(
    value = "S.W.O.R.D.",
    date = "2025-10-23T23:12:08.587383+02:00"
)
public class Pbs {
  @ToString.Include
  @Column(
      name = "pbsParent"
  )
  private String pbsparent;

  @ToString.Include
  @Column(
      name = "pbsCode"
  )
  private String pbscode;

  @ToString.Include
  @Column(
      name = "technology"
  )
  private String technology;

  @ToString.Include
  @Column(
      name = "subsystem"
  )
  private String subsystem;

  @ToString.Include
  @Column(
      name = "pbsCod1"
  )
  private String pbscod1;

  @ToString.Include
  @Column(
      name = "pbsCod2"
  )
  private String pbscod2;

  @ToString.Include
  @Column(
      name = "pbsCod3"
  )
  private String pbscod3;

  @ToString.Include
  @Column(
      name = "pbsCod4"
  )
  private String pbscod4;

  @ToString.Include
  @Column(
      name = "pbsCod5"
  )
  private String pbscod5;

  @ToString.Include
  @Column(
      name = "pbsCod6"
  )
  private String pbscod6;

  @ToString.Include
  @Column(
      name = "pbsCod7"
  )
  private String pbscod7;

  @ToString.Include
  @Column(
      name = "pbsCod8"
  )
  private String pbscod8;

  @ToString.Include
  @Column(
      name = "pbsCod9"
  )
  private String pbscod9;

  @ToString.Include
  @Column(
      name = "pbsDescription"
  )
  private String pbsdescription;

  @ToString.Include
  @Column(
      name = "itemCode"
  )
  private String itemcode;

  @ToString.Include
  @Column(
      name = "isRotating"
  )
  private String isrotating;

  @ToString.Include
  @Column(
      name = "isSoftware"
  )
  private String issoftware;

  @ToString.Include
  @Column(
      name = "isLru"
  )
  private String islru;

  @ToString.Include
  @Column(
      name = "isConsumable"
  )
  private String isconsumable;

  @ToString.Include
  @Column(
      name = "failureClass"
  )
  private String failureclass;

  @ToString.Include
  @Column(
      name = "pbsNote"
  )
  private String pbsnote;

  @Id
  @EqualsAndHashCode.Include
  @ToString.Include
  @GeneratedValue(
      strategy = GenerationType.IDENTITY
  )
  @Column(
      name = "id"
  )
  private Long id;

  @ToString.Include
  @Column(
      name = "userId"
  )
  private Integer userid;

  @ToString.Include
  @Column(
      name = "versionId"
  )
  private Integer versionid;

  @ToString.Include
  @Column(
      name = "dataStart"
  )
  private OffsetDateTime datastart;

  @ToString.Include
  @Column(
      name = "dataEnd"
  )
  private OffsetDateTime dataend;

  @ToString.Include
  @Column(
      name = "userLastMod"
  )
  private Integer userlastmod;

  @ToString.Include
  @Column(
      name = "classificationCode"
  )
  private String classificationcode;
}
