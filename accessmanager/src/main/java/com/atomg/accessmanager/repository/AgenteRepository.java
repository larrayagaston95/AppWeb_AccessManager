package com.atomg.accessmanager.repository;

import com.atomg.accessmanager.model.Agente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AgenteRepository extends JpaRepository<Agente, Long> {

    Optional<Agente> findByClavePrefijo(String clavePrefijo);

    List<Agente> findAllByOrderByEmpresaIdAscNombreAsc();
}
