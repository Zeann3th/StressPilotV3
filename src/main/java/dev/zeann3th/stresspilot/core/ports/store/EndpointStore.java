package dev.zeann3th.stresspilot.core.ports.store;

import dev.zeann3th.stresspilot.core.domain.entities.EndpointEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;

public interface EndpointStore {
    EndpointEntity save(EndpointEntity endpointEntity);

    List<EndpointEntity> saveAll(Iterable<EndpointEntity> entities);

    Optional<EndpointEntity> findById(Long id);

    Page<EndpointEntity> findAllByCondition(Long projectId, String name, Pageable pageable);

    List<EndpointEntity> findAllByProjectId(Long projectId);

    void deleteById(Long id);
}
