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
    name = "location"
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
    date = "2025-10-23T23:12:08.564907+02:00"
)
public class Location {
  @ToString.Include
  @Column(
      name = "parentLocCode"
  )
  private String parentloccode;

  @ToString.Include
  @Column(
      name = "locCode"
  )
  private String loccode;

  @ToString.Include
  @Column(
      name = "subLoc1"
  )
  private String subloc1;

  @ToString.Include
  @Column(
      name = "subLoc2"
  )
  private String subloc2;

  @ToString.Include
  @Column(
      name = "subLoc3"
  )
  private String subloc3;

  @ToString.Include
  @Column(
      name = "subLoc4"
  )
  private String subloc4;

  @ToString.Include
  @Column(
      name = "subLoc5"
  )
  private String subloc5;

  @ToString.Include
  @Column(
      name = "subLoc6"
  )
  private String subloc6;

  @ToString.Include
  @Column(
      name = "subLoc7"
  )
  private String subloc7;

  @ToString.Include
  @Column(
      name = "subLoc8"
  )
  private String subloc8;

  @ToString.Include
  @Column(
      name = "subLoc9"
  )
  private String subloc9;

  @ToString.Include
  @Column(
      name = "description"
  )
  private String description;

  @ToString.Include
  @Column(
      name = "locationType"
  )
  private String locationtype;

  @ToString.Include
  @Column(
      name = "longitude"
  )
  private String longitude;

  @ToString.Include
  @Column(
      name = "latitude"
  )
  private String latitude;

  @ToString.Include
  @Column(
      name = "failureClass"
  )
  private String failureclass;

  @ToString.Include
  @Column(
      name = "locDirection"
  )
  private String locdirection;

  @ToString.Include
  @Column(
      name = "locStartPoint"
  )
  private String locstartpoint;

  @ToString.Include
  @Column(
      name = "locEndPoint"
  )
  private String locendpoint;

  @ToString.Include
  @Column(
      name = "locLrm"
  )
  private String loclrm;

  @ToString.Include
  @Column(
      name = "note"
  )
  private String note;

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

  @Column(
      name = "upIn"
  )
  private byte[] upin;

  @ToString.Include
  @Column(
      name = "userLastMod"
  )
  private Integer userlastmod;
}
