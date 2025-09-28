package ru.javaboys.vibe_data.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@ToString
@Entity
@Table(name = "optimization")
public class Optimization extends BaseEntity {

    @Column(name = "text", columnDefinition = "text", nullable = false)
    private String text;

    @Column(name = "active", columnDefinition = "boolean default true")
    private boolean active = true;

}
