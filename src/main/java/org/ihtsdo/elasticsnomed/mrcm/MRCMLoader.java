package org.ihtsdo.elasticsnomed.mrcm;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import org.ihtsdo.elasticsnomed.mrcm.model.load.*;
import org.ihtsdo.elasticsnomed.mrcm.model.load.Attribute;
import org.ihtsdo.elasticsnomed.mrcm.model.load.Domain;
import org.ihtsdo.elasticsnomed.mrcm.model.load.Range;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

public class MRCMLoader {

	private static Logger logger = LoggerFactory.getLogger(MRCMLoader.class);

	public Set<org.ihtsdo.elasticsnomed.mrcm.model.Domain> load() throws IOException {
		Map<Long, org.ihtsdo.elasticsnomed.mrcm.model.Domain> domainMap = new HashMap<>();

		logger.info("Loading MRCM file.");
		XmlMapper mapper = new XmlMapper();
		mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
		// TODO: accept stream from a known location on disk or over REST
		InputStream resourceAsStream = getClass().getResourceAsStream("/mrcm/mrcm.xmi");
		ConceptModel conceptModel = mapper.readValue(resourceAsStream, ConceptModel.class);
		for (Constraints constraints : conceptModel.getConstraints()) {
			Domain loadDomain = constraints.getDomain();
			Long domainConceptId = loadDomain.getConceptId();
			logger.info("domain " + domainConceptId);
			Predicate predicate = constraints.getPredicate();
			Predicate predicate1 = predicate.getPredicate();
			Attribute loadAttribute = predicate1.getAttribute();
			if (loadAttribute != null) {

				org.ihtsdo.elasticsnomed.mrcm.model.Domain domain = domainMap.computeIfAbsent(domainConceptId,
						k -> new org.ihtsdo.elasticsnomed.mrcm.model.Domain(domainConceptId, loadDomain.getInclusionType()));

				org.ihtsdo.elasticsnomed.mrcm.model.Attribute attribute = new org.ihtsdo.elasticsnomed.mrcm.model.Attribute(loadAttribute.getConceptId(), loadAttribute.getInclusionType());
				domain.getAttributes().add(attribute);

				logger.info("attribute " + loadAttribute.getConceptId());
				Range range = predicate1.getRange();
				Long rangeConcepId = range.getConceptId();
				logger.info("range");
				if (rangeConcepId != null) {
					logger.info("- " + rangeConcepId);
					attribute.getRangeSet().add(new org.ihtsdo.elasticsnomed.mrcm.model.Range(rangeConcepId, range.getInclusionType()));
				} else {
					for (Children rangeChild : range.getChildren()) {
						logger.info("+- " + rangeChild.getConceptId());
						attribute.getRangeSet().add(new org.ihtsdo.elasticsnomed.mrcm.model.Range(rangeChild.getConceptId(), rangeChild.getInclusionType()));
					}
				}
			} else {
				logger.info("Lexical constraint will be ignored.");
			}
		}
		return new HashSet<>(domainMap.values());
	}
}