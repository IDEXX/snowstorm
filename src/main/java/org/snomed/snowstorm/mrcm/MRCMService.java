package org.snomed.snowstorm.mrcm;

import com.google.common.collect.Sets;
import io.kaicode.elasticvc.api.BranchCriteria;
import io.kaicode.elasticvc.api.VersionControlHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.services.ConceptService;
import org.snomed.snowstorm.core.data.services.QueryService;
import org.snomed.snowstorm.core.data.services.RuntimeServiceException;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.mrcm.model.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.lang.Long.parseLong;

@Service
public class MRCMService {

	@Autowired
	private QueryService queryService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private VersionControlHelper versionControlHelper;

	private Map<String, MRCM> branchMrcmMap;

	@Value("${validation.mrcm.xml.path}")
	private String mrcmXmlPath;

	private Logger logger = LoggerFactory.getLogger(getClass());

	public void loadFromFiles() throws ServiceException {
		this.branchMrcmMap = new MRCMLoader(mrcmXmlPath).loadFromFiles();
	}

	public Collection<ConceptMini> retrieveDomainAttributes(String branchPath, Set<Long> parentIds, List<String> languageCodes) {
		BranchCriteria branchCriteria = versionControlHelper.getBranchCriteria(branchPath);
		Set<Long> allAncestors = queryService.findAncestorIdsAsUnion(branchCriteria, false, parentIds);
		allAncestors.addAll(parentIds);

		Set<Domain> matchedDomains = getClosestMrcm(branchPath).getDomainMap().values().stream().filter(d -> {
			Long domainConceptId = d.getConceptId();
			InclusionType inclusionType = d.getInclusionType();
			if ((inclusionType == InclusionType.SELF || inclusionType == InclusionType.SELF_OR_DESCENDANT)
					&& parentIds.contains(domainConceptId)) {
				return true;
			}
			if ((inclusionType == InclusionType.DESCENDANT || inclusionType == InclusionType.SELF_OR_DESCENDANT)
					&& allAncestors.contains(domainConceptId)) {
				return true;
			}
			return false;
		}).collect(Collectors.toSet());

		Set<Attribute> matchedAttributes = matchedDomains.stream().map(Domain::getAttributes).flatMap(Collection::stream).collect(Collectors.toSet());

		Set<Long> allMatchedAttributeIds = matchedAttributes.stream().map(Attribute::getConceptId).collect(Collectors.toSet());
		Set<Long> descendantTypeAttributes = matchedAttributes.stream().filter(attribute -> attribute.getInclusionType() == InclusionType.DESCENDANT).map(Attribute::getConceptId).collect(Collectors.toSet());
		Set<Long> selfOrDescendantTypeAttributes = matchedAttributes.stream().filter(attribute -> attribute.getInclusionType() == InclusionType.SELF_OR_DESCENDANT).map(Attribute::getConceptId).collect(Collectors.toSet());

		List<Long> descendantAttributes = queryService.findDescendantIdsAsUnion(branchCriteria, false, Sets.union(descendantTypeAttributes, selfOrDescendantTypeAttributes));

		allMatchedAttributeIds.removeAll(descendantAttributes);
		allMatchedAttributeIds.addAll(descendantAttributes);

		return conceptService.findConceptMinis(branchCriteria, allMatchedAttributeIds, languageCodes).getResultsMap().values();
	}

	public Collection<ConceptMini> retrieveAttributeValues(String branchPath, String attributeId, String termPrefix, List<String> languageCodes) {
		Attribute attribute = getClosestMrcm(branchPath).getAttributeMap().get(parseLong(attributeId));
		if (attribute == null) {
			throw new IllegalArgumentException("MRCM Attribute " + attributeId + " not found.");
		}

		QueryService.ConceptQueryBuilder queryBuilder = queryService.createQueryBuilder(false)
				.termPrefix(termPrefix)
				.languageCodes(languageCodes);

		for (Range range : attribute.getRangeSet()) {
			Long conceptId = range.getConceptId();
			InclusionType inclusionType = range.getInclusionType();
			switch (inclusionType) {
				case SELF:
					queryBuilder.self(conceptId);
					break;
				case DESCENDANT:
					queryBuilder.descendant(conceptId);
					break;
				case SELF_OR_DESCENDANT:
					queryBuilder.selfOrDescendant(conceptId);
					break;
			}
		}

		return queryService.search(queryBuilder, branchPath, PageRequest.of(0, 50)).getContent();
	}

	private MRCM getClosestMrcm(final String branchPath) {
		String searchPath = branchPath;
		while (searchPath.contains("/")) {
			MRCM mrcm = branchMrcmMap.get(searchPath);
			if (mrcm != null) {
				if (searchPath.contains("/")) {
					logger.debug("MRCM branch match {}", searchPath);
				}
				return mrcm;
			}
			searchPath = searchPath.substring(0, searchPath.lastIndexOf("/"));
		}
		throw new RuntimeServiceException("Failed to find any relevant MRCM for branch path " + branchPath);
	}

	// Test method
	public static void main(String[] args) throws ServiceException {
		new MRCMService().loadFromFiles();
	}
}
