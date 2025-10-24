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
    name = "alrmMsrConfMig"
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
    date = "2025-10-23T23:12:08.453700+02:00"
)
public class Alrmmsrconfmig {
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
      name = "sourceSystemName"
  )
  private String sourcesystemname;

  @ToString.Include
  @Column(
      name = "alarmId"
  )
  private String alarmid;

  @ToString.Include
  @Column(
      name = "affectedObjectRef"
  )
  private String affectedobjectref;

  @ToString.Include
  @Column(
      name = "category"
  )
  private String category;

  @ToString.Include
  @Column(
      name = "eventTypeId"
  )
  private String eventtypeid;

  @ToString.Include
  @Column(
      name = "alarmSeverity"
  )
  private String alarmseverity;

  @ToString.Include
  @Column(
      name = "mmisAssetCode"
  )
  private String mmisassetcode;

  @ToString.Include
  @Column(
      name = "userId"
  )
  private Integer userid;

  @ToString.Include
  @Column(
      name = "crtTime"
  )
  private OffsetDateTime crttime;

  @ToString.Include
  @Column(
      name = "idMigToAdp"
  )
  private Integer idmigtoadp;
}
