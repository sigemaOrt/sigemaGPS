package com.sigemaGPS.repositories;

import com.sigemaGPS.models.Posicion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface IPosicionRepository extends JpaRepository<Posicion, Long> {
    Optional<List<Posicion>> findByIdEquipoAndFecha(Long idEquipo, LocalDate fecha);
    Optional<Posicion> findFirstByIdEquipoOrderByFechaDesc(Long idEquipo);
}
