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
    name = "failureClassHierarchy"
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
    date = "2025-10-23T23:12:08.519622+02:00"
)
public class Failureclasshierarchy {
  @ToString.Include
  @Column(
      name = "failureClass"
  )
  private String failureclass;

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
      name = "problemCode"
  )
  private String problemcode;

  @ToString.Include
  @Column(
      name = "causeCode"
  )
  private String causecode;

  @ToString.Include
  @Column(
      name = "remedyCode"
  )
  private String remedycode;

  @ToString.Include
  @Column(
      name = "versionId"
  )
  private Integer versionid;

  @ToString.Include
  @Column(
      name = "userId"
  )
  private Integer userid;

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
}
