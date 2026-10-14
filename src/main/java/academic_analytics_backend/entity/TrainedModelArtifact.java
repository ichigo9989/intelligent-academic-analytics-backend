package academic_analytics_backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

@Entity
@Table(name = "ml_model_artifacts")
public class TrainedModelArtifact {
    public static final String ACTIVE_MODEL_ID = "active";

    @Id
    @Column(length = 20, nullable = false)
    private String id = ACTIVE_MODEL_ID;

    @Lob
    @Column(name = "model_bytes", nullable = false, columnDefinition = "LONGBLOB")
    private byte[] modelBytes;

    @Column(nullable = false, length = 40)
    private String algorithm;
    @Column(length = 120)
    private String label;
    @Column(name = "feature_columns", length = 255)
    private String featureColumns;
    @Column(name = "target_column", length = 80)
    private String targetColumn;
    @Column(name = "model_version", length = 40)
    private String modelVersion;
    @Column(name = "cv_mae")
    private Double cvMae;
    @Column(nullable = false)
    private Double mae;
    @Column(nullable = false)
    private Double rmse;
    @Column(nullable = false)
    private Double r2;
    @Column(nullable = false)
    private Integer datasetRows;
    @Column(nullable = false)
    private Integer trainRows;
    @Column(nullable = false)
    private Integer testRows;
    @Column(nullable = false, length = 40)
    private String trainedAt;

    public TrainedModelArtifact() {}
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public byte[] getModelBytes() { return modelBytes; }
    public void setModelBytes(byte[] modelBytes) { this.modelBytes = modelBytes; }
    public String getAlgorithm() { return algorithm; }
    public void setAlgorithm(String algorithm) { this.algorithm = algorithm; }
    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
    public String getFeatureColumns() { return featureColumns; }
    public void setFeatureColumns(String featureColumns) { this.featureColumns = featureColumns; }
    public String getTargetColumn() { return targetColumn; }
    public void setTargetColumn(String targetColumn) { this.targetColumn = targetColumn; }
    public String getModelVersion() { return modelVersion; }
    public void setModelVersion(String modelVersion) { this.modelVersion = modelVersion; }
    public Double getCvMae() { return cvMae; }
    public void setCvMae(Double cvMae) { this.cvMae = cvMae; }
    public Double getMae() { return mae; }
    public void setMae(Double mae) { this.mae = mae; }
    public Double getRmse() { return rmse; }
    public void setRmse(Double rmse) { this.rmse = rmse; }
    public Double getR2() { return r2; }
    public void setR2(Double r2) { this.r2 = r2; }
    public Integer getDatasetRows() { return datasetRows; }
    public void setDatasetRows(Integer datasetRows) { this.datasetRows = datasetRows; }
    public Integer getTrainRows() { return trainRows; }
    public void setTrainRows(Integer trainRows) { this.trainRows = trainRows; }
    public Integer getTestRows() { return testRows; }
    public void setTestRows(Integer testRows) { this.testRows = testRows; }
    public String getTrainedAt() { return trainedAt; }
    public void setTrainedAt(String trainedAt) { this.trainedAt = trainedAt; }
}
