package org.snomed.snowstorm.core.rf2.export;

import com.google.common.collect.Sets;
import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.BranchService;
import io.kaicode.elasticvc.api.VersionControlHelper;
import org.apache.tomcat.util.http.fileupload.util.Streams;
import org.elasticsearch.index.query.BoolQueryBuilder;
import org.elasticsearch.index.query.QueryBuilder;
import org.elasticsearch.index.query.QueryBuilders;
import org.elasticsearch.search.sort.SortBuilders;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.domain.jobs.ExportConfiguration;
import org.snomed.snowstorm.core.data.repositories.ExportConfigurationRepository;
import org.snomed.snowstorm.core.data.services.NotFoundException;
import org.snomed.snowstorm.core.data.services.QueryService;
import org.snomed.snowstorm.core.rf2.RF2Type;
import org.snomed.snowstorm.core.util.DateUtil;
import org.snomed.snowstorm.core.util.TimerUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.query.NativeSearchQuery;
import org.springframework.data.elasticsearch.core.query.NativeSearchQueryBuilder;
import org.springframework.data.util.CloseableIterator;
import org.springframework.stereotype.Service;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static io.kaicode.elasticvc.api.ComponentService.LARGE_PAGE;
import static org.elasticsearch.index.query.QueryBuilders.*;

@Service
public class ExportService {

	@Autowired
	private VersionControlHelper versionControlHelper;

	@Autowired
	private ElasticsearchOperations elasticsearchTemplate;

	@Autowired
	private QueryService queryService;

	@Autowired
	private ExportConfigurationRepository exportConfigurationRepository;

	@Autowired
	private BranchService branchService;

	private Set<String> refsetTypesRequiredForClassification = Sets.newHashSet(Concepts.REFSET_MRCM_ATTRIBUTE_DOMAIN, Concepts.OWL_EXPRESSION_TYPE_REFERENCE_SET);

	private Logger logger = LoggerFactory.getLogger(getClass());

	public String createJob(ExportConfiguration exportConfiguration) {
		branchService.findBranchOrThrow(exportConfiguration.getBranchPath());
		exportConfiguration.setId(UUID.randomUUID().toString());
		if (exportConfiguration.getFilenameEffectiveDate() == null) {
			exportConfiguration.setFilenameEffectiveDate(DateUtil.DATE_STAMP_FORMAT.format(new Date()));
		}
		exportConfigurationRepository.save(exportConfiguration);
		return exportConfiguration.getId();
	}

	public ExportConfiguration getExportJobOrThrow(String exportId) {
		Optional<ExportConfiguration> config = exportConfigurationRepository.findById(exportId);
		if (!config.isPresent()) {
			throw new NotFoundException("Export job not found.");
		}
		return config.get();
	}

	public void exportRF2Archive(ExportConfiguration exportConfiguration, OutputStream outputStream) throws ExportException {
		synchronized (this) {
			if (exportConfiguration.getStartDate() != null) {
				throw new IllegalStateException("Export already started.");
			}
			exportConfiguration.setStartDate(new Date());
			exportConfigurationRepository.save(exportConfiguration);
		}

		File exportFile = exportRF2ArchiveFile(exportConfiguration.getBranchPath(), exportConfiguration.getFilenameEffectiveDate(),
				exportConfiguration.getType(), exportConfiguration.isConceptsAndRelationshipsOnly());
		try (FileInputStream inputStream = new FileInputStream(exportFile)) {
			Streams.copy(inputStream, outputStream, false);
		} catch (IOException e) {
			throw new ExportException("Failed to copy RF2 data into output stream.", e);
		} finally {
			exportFile.delete();
		}
	}

	public File exportRF2ArchiveFile(String branchPath, String filenameEffectiveDate, RF2Type exportType, boolean forClassification) throws ExportException {
		if (exportType == RF2Type.FULL) {
			throw new IllegalArgumentException("Full RF2 export is not implemented.");
		}

		logger.info("Starting {} export.", exportType);
		Date startTime = new Date();

		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branchPath);

		try {
			File exportFile = File.createTempFile("export-" + new Date().getTime(), ".zip");
			try (ZipOutputStream zipOutputStream = new ZipOutputStream(new FileOutputStream(exportFile))) {
				// Write Concepts
				int conceptLines = exportComponents(Concept.class, "Terminology/", "sct2_Concept_", filenameEffectiveDate, exportType, zipOutputStream, getContentQuery(exportType, branchCriteria.getEntityBranchCriteria(Concept.class)), null);
				logger.info("{} concept states exported", conceptLines);

				if (!forClassification) {
					// Write Descriptions
					BoolQueryBuilder descriptionBranchCriteria = branchCriteria.getEntityBranchCriteria(Description.class);
					BoolQueryBuilder descriptionContentQuery = getContentQuery(exportType, descriptionBranchCriteria);
					descriptionContentQuery.mustNot(termQuery(Description.Fields.TYPE_ID, Concepts.TEXT_DEFINITION));
					int descriptionLines = exportComponents(Description.class, "Terminology/", "sct2_Description_", filenameEffectiveDate, exportType, zipOutputStream, descriptionContentQuery, null);
					logger.info("{} description states exported", descriptionLines);

					// Write Text Definitions
					BoolQueryBuilder textDefinitionContentQuery = getContentQuery(exportType, descriptionBranchCriteria);
					textDefinitionContentQuery.must(termQuery(Description.Fields.TYPE_ID, Concepts.TEXT_DEFINITION));
					int textDefinitionLines = exportComponents(Description.class, "Terminology/", "sct2_TextDefinition_", filenameEffectiveDate, exportType, zipOutputStream, textDefinitionContentQuery, null);
					logger.info("{} text defintion states exported", textDefinitionLines);
				}

				// Write Stated Relationships
				BoolQueryBuilder relationshipBranchCritera = branchCriteria.getEntityBranchCriteria(Relationship.class);
				BoolQueryBuilder relationshipQuery = getContentQuery(exportType, relationshipBranchCritera);
				relationshipQuery.must(termQuery("characteristicTypeId", Concepts.STATED_RELATIONSHIP));
				int statedRelationshipLines = exportComponents(Relationship.class, "Terminology/", "sct2_StatedRelationship_", filenameEffectiveDate, exportType, zipOutputStream, relationshipQuery, null);
				logger.info("{} stated relationship states exported", statedRelationshipLines);

				// Write Inferred Relationships
				relationshipQuery = getContentQuery(exportType, relationshipBranchCritera);
				// Not 'stated' will include inferred and additional
				relationshipQuery.mustNot(termQuery("characteristicTypeId", Concepts.STATED_RELATIONSHIP));
				int inferredRelationshipLines = exportComponents(Relationship.class, "Terminology/", "sct2_Relationship_", filenameEffectiveDate, exportType, zipOutputStream, relationshipQuery, null);
				logger.info("{} inferred and additional relationship states exported", inferredRelationshipLines);

				// Write Reference Sets
				List<ReferenceSetType> referenceSetTypes = getReferenceSetTypes(branchCriteria.getEntityBranchCriteria(ReferenceSetType.class)).stream()
						.filter(type -> !forClassification || refsetTypesRequiredForClassification.contains(type.getConceptId()))
						.collect(Collectors.toList());

				logger.info("{} Reference Set Types found for this export: {}", referenceSetTypes.size(), referenceSetTypes);

				BoolQueryBuilder memberBranchCriteria = branchCriteria.getEntityBranchCriteria(ReferenceSetMember.class);
				for (ReferenceSetType referenceSetType : referenceSetTypes) {
					List<Long> refsetsOfThisType = new ArrayList<>(queryService.findDescendantIdsAsUnion(branchCriteria, true, Collections.singleton(Long.parseLong(referenceSetType.getConceptId()))));
					refsetsOfThisType.add(Long.parseLong(referenceSetType.getConceptId()));
					for (Long refsetToExport : refsetsOfThisType) {
						BoolQueryBuilder memberQuery = getContentQuery(exportType, memberBranchCriteria);
						memberQuery.must(QueryBuilders.termQuery(ReferenceSetMember.Fields.REFSET_ID, refsetToExport));
						long memberCount = elasticsearchTemplate.count(getNativeSearchQuery(memberQuery), ReferenceSetMember.class);
						if (memberCount > 0) {
							logger.info("Exporting Reference Set {} {} with {} members", refsetToExport, referenceSetType.getName(), memberCount);
							String exportDir = referenceSetType.getExportDir();
							String entryDirectory = !exportDir.startsWith("/") ? "Refset/" + exportDir + "/" : exportDir.substring(1) + "/";
							String entryFilenamePrefix = (!entryDirectory.startsWith("Terminology/") ? "der2_" : "sct2_") + referenceSetType.getFieldTypes() + "Refset_" + referenceSetType.getName() + (refsetsOfThisType.size() > 1 ? refsetToExport : "");
							exportComponents(
									ReferenceSetMember.class,
									entryDirectory,
									entryFilenamePrefix,
									filenameEffectiveDate,
									exportType,
									zipOutputStream,
									memberQuery,
									referenceSetType.getFieldNameList());
						}
					}
				}
			}
			logger.info("{} export complete in {} seconds.", exportType, TimerUtil.secondsSince(startTime));
			return exportFile;
		} catch (IOException e) {
			throw new ExportException("Failed to write RF2 zip file.", e);
		}
	}

	public String getFilename(ExportConfiguration exportConfiguration) {
		return String.format("snomed-%s-%s-%s.zip",
				exportConfiguration.getBranchPath().replace("/", "_"),
				exportConfiguration.getFilenameEffectiveDate(),
				exportConfiguration.getType().getName());
	}

	private BoolQueryBuilder getContentQuery(RF2Type exportType, QueryBuilder branchCriteria) {
		BoolQueryBuilder contentQuery = boolQuery().must(branchCriteria);
		if (exportType == RF2Type.DELTA) {
			contentQuery.mustNot(existsQuery(SnomedComponent.Fields.EFFECTIVE_TIME));
		}
		return contentQuery;
	}

	private <T> int exportComponents(Class<T> componentClass, String entryDirectory, String entryFilenamePrefix, String filenameEffectiveDate,
									 RF2Type exportType, ZipOutputStream zipOutputStream, BoolQueryBuilder contentQuery, List<String> extraFieldNames) {

		String componentFilePath = "SnomedCT_Export/RF2Release/" + entryDirectory + entryFilenamePrefix + String.format("%s_INT_%s.txt", exportType.getName(), filenameEffectiveDate);
		logger.info("Exporting file {}", componentFilePath);
		try {
			// Open zip entry
			zipOutputStream.putNextEntry(new ZipEntry(componentFilePath));

			// Stream components into zip
			try (ExportWriter<T> writer = getExportWriter(componentClass, zipOutputStream, extraFieldNames);
					CloseableIterator<T> componentStream = elasticsearchTemplate.stream(getNativeSearchQuery(contentQuery), componentClass)) {
				writer.writeHeader();
				componentStream.forEachRemaining(writer::write);
				return writer.getContentLinesWritten();
			} finally {
				// Close zip entry
				zipOutputStream.closeEntry();
			}
		} catch (IOException e) {
			throw new ExportException("Failed to write export zip entry '" + componentFilePath + "'", e);
		}
	}

	private <T> ExportWriter<T> getExportWriter(Class<T> componentClass, OutputStream outputStream, List<String> extraFieldNames) throws IOException {
		if (componentClass.equals(Concept.class)) {
			return (ExportWriter<T>) new ConceptExportWriter(getBufferedWriter(outputStream));
		}
		if (componentClass.equals(Description.class)) {
			return (ExportWriter<T>) new DescriptionExportWriter(getBufferedWriter(outputStream));
		}
		if (componentClass.equals(Relationship.class)) {
			return (ExportWriter<T>) new RelationshipExportWriter(getBufferedWriter(outputStream));
		}
		if (componentClass.equals(ReferenceSetMember.class)) {
			return (ExportWriter<T>) new ReferenceSetMemberExportWriter(getBufferedWriter(outputStream), extraFieldNames);
		}
		throw new UnsupportedOperationException("Not able to export component of type " + componentClass.getCanonicalName());
	}

	private List<ReferenceSetType> getReferenceSetTypes(QueryBuilder branchCriteria) {
		BoolQueryBuilder contentQuery = getContentQuery(RF2Type.SNAPSHOT, branchCriteria);
		return elasticsearchTemplate.queryForList(new NativeSearchQueryBuilder()
				.withQuery(contentQuery)
				.withSort(SortBuilders.fieldSort(ReferenceSetType.Fields.NAME))
				.withPageable(LARGE_PAGE)
				.build(), ReferenceSetType.class);
	}

	private NativeSearchQuery getNativeSearchQuery(BoolQueryBuilder contentQuery) {
		return new NativeSearchQueryBuilder()
				.withQuery(contentQuery)
				.withPageable(LARGE_PAGE)
				.build();
	}

	private BufferedWriter getBufferedWriter(OutputStream outputStream) {
		return new BufferedWriter(new OutputStreamWriter(outputStream));
	}
}
