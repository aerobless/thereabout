package com.sixtymeters.thereabout.shared.icons;

import jakarta.persistence.*;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "website_icon")
@Getter
@Setter
public class WebsiteIconEntity {
  @Id
  @Column(length = 64)
  private String id;

  @Column(columnDefinition = "MEDIUMBLOB")
  private byte[] imageData;

  @Column(length = 50)
  private String contentType;

  private Instant checkedAt;
}
