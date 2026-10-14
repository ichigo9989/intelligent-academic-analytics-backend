package academic_analytics_backend.repository;

import academic_analytics_backend.entity.TrainedModelArtifact;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface TrainedModelArtifactRepository extends JpaRepository<TrainedModelArtifact, String> {
    @Query("select m.id as id, m.algorithm as algorithm, m.modelVersion as modelVersion, m.cvMae as cvMae, "
            + "m.mae as mae, m.rmse as rmse, m.r2 as r2, "
            + "m.datasetRows as datasetRows, m.trainRows as trainRows, m.testRows as testRows, m.trainedAt as trainedAt "
            + "from TrainedModelArtifact m where m.id = :id")
    Optional<ModelMetadata> findMetadataById(@Param("id") String id);

    interface ModelMetadata {
        String getId();
        String getAlgorithm();
        String getModelVersion();
        Double getCvMae();
        Double getMae();
        Double getRmse();
        Double getR2();
        Integer getDatasetRows();
        Integer getTrainRows();
        Integer getTestRows();
        String getTrainedAt();
    }
}
