package com.example.entities;

import jakarta.annotation.Generated;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.lang.Long;
import java.lang.String;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(
    name = "problems"
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
    date = "2025-10-23T23:12:08.605088+02:00"
)
public class Problems {
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
      name = "openedOn"
  )
  private OffsetDateTime openedon;

  @ToString.Include
  @Column(
      name = "userId"
  )
  private Long userid;

  @ToString.Include
  @Column(
      name = "ownersId"
  )
  private Long ownersid;

  @ToString.Include
  @Column(
      name = "problemTypesId"
  )
  private Long problemtypesid;

  @ToString.Include
  @Column(
      name = "caStatus"
  )
  private String castatus;

  @ToString.Include
  @Column(
      name = "suppliersManufacturersId"
  )
  private Long suppliersmanufacturersid;

  @ToString.Include
  @Column(
      name = "partNumber"
  )
  private String partnumber;

  @ToString.Include
  @Column(
      name = "partName"
  )
  private String partname;

  @ToString.Include
  @Column(
      name = "title"
  )
  private String title;

  @ToString.Include
  @Column(
      name = "description"
  )
  private String description;

  @ToString.Include
  @Column(
      name = "dueDate"
  )
  private LocalDate duedate;
}
