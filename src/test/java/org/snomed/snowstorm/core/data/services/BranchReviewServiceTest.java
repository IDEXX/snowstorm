package org.snomed.snowstorm.core.data.services;

import io.kaicode.elasticvc.api.BranchService;
import org.elasticsearch.ElasticsearchParseException;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.snomed.snowstorm.AbstractTest;
import org.snomed.snowstorm.TestConfig;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.domain.review.BranchReviewConceptChanges;
import org.snomed.snowstorm.core.data.domain.review.MergeReview;
import org.snomed.snowstorm.core.data.domain.review.MergeReviewConceptVersions;
import org.snomed.snowstorm.core.data.domain.review.ReviewStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.snomed.snowstorm.TestConfig.DEFAULT_LANGUAGE_CODES;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = TestConfig.class)
public class BranchReviewServiceTest extends AbstractTest {

	@Autowired
	private BranchReviewService reviewService;

	@Autowired
	private ConceptService conceptService;

	@Autowired
	private BranchService branchService;

	@Autowired
	private BranchMergeService mergeService;

	private Date setupStartTime;

	private static final Long[] EMPTY_ARRAY = new Long[]{};

	@Before
	public void setUp() throws Exception {
		setupStartTime = now();
		branchService.create("MAIN");
		createConcept("10000100", "MAIN");
		branchService.create("MAIN/A");
	}

	@Test
	public void testCreateMergeReview() throws InterruptedException, ServiceException {
		conceptService.create(new Concept(Concepts.SNOMEDCT_ROOT), "MAIN");
		createConcept("116680003", "MAIN");
		createConcept("10000200", "MAIN");
		createConcept("10000300", "MAIN");
		createConcept("10000400", "MAIN");
		createConcept("10000500", "MAIN");
		createConcept("10000600", "MAIN");
		createConcept("700000000", "MAIN");
		createConcept("800000000", "MAIN");
		createConcept("900000000", "MAIN");

		// Rebase A
		mergeService.mergeBranchSync("MAIN", "MAIN/A", Collections.emptySet());


		// Create MAIN/B
		branchService.create("MAIN/B");

		// Update concept 10000100 description only on B
		Concept concept = conceptService.find("10000100", "MAIN/B");
		concept.getDescriptions().iterator().next().setCaseSignificance("INITIAL_CHARACTER_CASE_INSENSITIVE");
		conceptService.update(concept, "MAIN/B");

		// Update concept 10000200 description on B and A
		concept = conceptService.find("10000200", "MAIN/B");
		concept.getDescriptions().iterator().next().setCaseSignificance("INITIAL_CHARACTER_CASE_INSENSITIVE");
		conceptService.update(concept, "MAIN/B");
		concept = conceptService.find("10000200", "MAIN/A");
		concept.getDescriptions().iterator().next().setCaseSignificance("ENTIRE_TERM_CASE_SENSITIVE");
		conceptService.update(concept, "MAIN/A");


		// Update concept 10000300 lang refset only on B
		concept = conceptService.find("10000300", "MAIN/B");
		concept.getDescriptions().iterator().next().getAcceptabilityMapAndClearMembers().put(Concepts.US_EN_LANG_REFSET, Concepts.descriptionAcceptabilityNames.get(Concepts.PREFERRED));
		conceptService.update(concept, "MAIN/B");

		// Update concept 10000400 lang refset on B and A
		concept = conceptService.find("10000400", "MAIN/B");
		concept.getDescriptions().iterator().next().getAcceptabilityMapAndClearMembers().put(Concepts.US_EN_LANG_REFSET, Concepts.descriptionAcceptabilityNames.get(Concepts.PREFERRED));
		conceptService.update(concept, "MAIN/B");
		concept = conceptService.find("10000400", "MAIN/A");
		concept.getDescriptions().iterator().next().getAcceptabilityMapAndClearMembers().put(Concepts.US_EN_LANG_REFSET, Concepts.descriptionAcceptabilityNames.get(Concepts.PREFERRED));
		conceptService.update(concept, "MAIN/A");


		// Update concept 10000500 relationship only on B
		concept = conceptService.find("10000500", "MAIN/B");
		concept.getRelationships().iterator().next().setGroupId(1);
		conceptService.update(concept, "MAIN/B");

		// Update concept 10000600 relationship on B and A
		concept = conceptService.find("10000600", "MAIN/B");
		concept.getRelationships().iterator().next().setGroupId(1);
		conceptService.update(concept, "MAIN/B");
		concept = conceptService.find("10000600", "MAIN/A");
		concept.getRelationships().iterator().next().setGroupId(1);
		conceptService.update(concept, "MAIN/A");


		// Add concept 700000000 axiom only on B
		concept = conceptService.find("700000000", "MAIN/B");
		concept.addAxiom(new Axiom().setRelationships(Collections.singleton(new Relationship(Concepts.ISA, "10000200"))));
		conceptService.update(concept, "MAIN/B");

		// Add concept 800000000 axiom on B and A
		concept = conceptService.find("800000000", "MAIN/B");
		concept.addAxiom(new Axiom().setRelationships(Collections.singleton(new Relationship(Concepts.ISA, "10000200"))));
		conceptService.update(concept, "MAIN/B");
		concept = conceptService.find("800000000", "MAIN/A");
		concept.addAxiom(new Axiom().setRelationships(Collections.singleton(new Relationship(Concepts.ISA, "10000200"))));
		conceptService.update(concept, "MAIN/A");

		// Add concept 900000000 axiom on B and inferred relationship on A - this should be ignored
		concept = conceptService.find("900000000", "MAIN/B");
		concept.addAxiom(new Axiom().setRelationships(Collections.singleton(new Relationship(Concepts.ISA, "10000200"))));
		conceptService.update(concept, "MAIN/B");
		concept = conceptService.find("900000000", "MAIN/A");
		concept.addRelationship(new Relationship(Concepts.ISA, "10000200").setCharacteristicTypeId(Concepts.INFERRED_RELATIONSHIP));
		conceptService.update(concept, "MAIN/A");


		// Promote B to MAIN
		mergeService.mergeBranchSync("MAIN/B", "MAIN", Collections.emptySet());


		MergeReview review = reviewService.createMergeReview("MAIN", "MAIN/A");

		long maxWait = 10;
		long cumulativeWait = 0;
		while (review.getStatus() == ReviewStatus.PENDING && cumulativeWait < maxWait) {
			Thread.sleep(1_000);
			cumulativeWait++;
		}

		assertEquals(ReviewStatus.CURRENT, review.getStatus());

		Collection<MergeReviewConceptVersions> mergeReviewConflictingConcepts = reviewService.getMergeReviewConflictingConcepts(review.getId(), DEFAULT_LANGUAGE_CODES);
		assertEquals(4, mergeReviewConflictingConcepts.size());
		Set<String> conceptIds = mergeReviewConflictingConcepts.stream().map(conceptVersions -> conceptVersions.getSourceConcept().getId()).collect(Collectors.toSet());
		assertTrue(conceptIds.contains("10000200"));
		assertTrue(conceptIds.contains("10000400"));
		assertTrue(conceptIds.contains("10000600"));
		assertTrue(conceptIds.contains("800000000"));
	}

	@Test
	public void testCreateMergeReviewConceptDeletedOnParent() throws InterruptedException, ServiceException {
		// Update concept 10000100 description on A
		Concept concept = conceptService.find("10000100", "MAIN/A");
		concept.getDescriptions().iterator().next().setCaseSignificance("INITIAL_CHARACTER_CASE_INSENSITIVE");
		conceptService.update(concept, "MAIN/A");

		// Delete concept 10000100 on MAIN
		conceptService.deleteConceptAndComponents("10000100", "MAIN", false);

		MergeReview review = reviewService.createMergeReview("MAIN", "MAIN/A");

		long maxWait = 10;
		long cumulativeWait = 0;
		while (review.getStatus() == ReviewStatus.PENDING && cumulativeWait < maxWait) {
			Thread.sleep(1_000);
			cumulativeWait++;
		}

		assertEquals(ReviewStatus.CURRENT, review.getStatus());

		Collection<MergeReviewConceptVersions> mergeReviewConflictingConcepts = reviewService.getMergeReviewConflictingConcepts(review.getId(), DEFAULT_LANGUAGE_CODES);
		assertEquals(1, mergeReviewConflictingConcepts.size());
		Set<String> conceptIds = mergeReviewConflictingConcepts.stream().map(conceptVersions -> conceptVersions.getTargetConcept().getId()).collect(Collectors.toSet());
		assertTrue(conceptIds.contains("10000100"));
	}

	@Test
	public void testCreateMergeReviewConceptDeletedOnChild() throws InterruptedException, ServiceException {
		// Update concept 10000100 description on A
		Concept concept = conceptService.find("10000100", "MAIN");
		concept.getDescriptions().iterator().next().setCaseSignificance("INITIAL_CHARACTER_CASE_INSENSITIVE");
		conceptService.update(concept, "MAIN");

		// Delete concept 10000100 on MAIN
		conceptService.deleteConceptAndComponents("10000100", "MAIN/A", false);

		MergeReview review = reviewService.createMergeReview("MAIN", "MAIN/A");

		long maxWait = 10;
		long cumulativeWait = 0;
		while (review.getStatus() == ReviewStatus.PENDING && cumulativeWait < maxWait) {
			Thread.sleep(1_000);
			cumulativeWait++;
		}

		assertEquals(ReviewStatus.CURRENT, review.getStatus());

		Collection<MergeReviewConceptVersions> mergeReviewConflictingConcepts = reviewService.getMergeReviewConflictingConcepts(review.getId(), DEFAULT_LANGUAGE_CODES);
		assertEquals(1, mergeReviewConflictingConcepts.size());
		Set<String> conceptIds = mergeReviewConflictingConcepts.stream().map(conceptVersions -> conceptVersions.getSourceConcept().getId()).collect(Collectors.toSet());
		assertTrue(conceptIds.contains("10000100"));
	}

	@Test
	public void testCreateConceptChangeReportOnBranchSinceTimepoint() throws Exception {
		try {
			// Assert report contains one new concept on MAIN since start of setup
			assertReportEquals(reviewService.createConceptChangeReportOnBranchForTimeRange("MAIN", setupStartTime, now(), true),
					new Long[]{10000100L}, EMPTY_ARRAY, EMPTY_ARRAY);

			// Assert report contains no concepts on MAIN/A since start of setup
			assertReportEquals(reviewService.createConceptChangeReportOnBranchForTimeRange("MAIN/A", setupStartTime, now(), false),
					EMPTY_ARRAY, EMPTY_ARRAY, EMPTY_ARRAY);

			final Date beforeSecondCreation = now();
			createConcept("10000200", "MAIN/A");

			// Assert report contains no new concepts on MAIN/A since start of setup
			assertReportEquals(reviewService.createConceptChangeReportOnBranchForTimeRange("MAIN/A", setupStartTime, now(), false),
					new Long[]{10000200L}, EMPTY_ARRAY, EMPTY_ARRAY);

			// Assert report contains one new concept on MAIN/A since timeA
			assertReportEquals(reviewService.createConceptChangeReportOnBranchForTimeRange("MAIN/A", beforeSecondCreation, now(), false),
					new Long[]{10000200L}, EMPTY_ARRAY, EMPTY_ARRAY);

			final Date beforeDeletion = now();

			// Delete concept 100 from MAIN
			conceptService.deleteConceptAndComponents("10000100", "MAIN", false);

			final Date afterDeletion = now();

			// Assert report contains one deleted concept on MAIN
			assertReportEquals(reviewService.createConceptChangeReportOnBranchForTimeRange("MAIN", beforeDeletion, now(), true),
					EMPTY_ARRAY, EMPTY_ARRAY, new Long[]{10000100L});


			// Assert report contains no deleted concepts on MAIN before the deletion
			assertReportEquals(reviewService.createConceptChangeReportOnBranchForTimeRange("MAIN", beforeSecondCreation, beforeDeletion, true),
					EMPTY_ARRAY, EMPTY_ARRAY, EMPTY_ARRAY);

			// Assert report contains no deleted concepts on MAIN after the deletion
			assertReportEquals(reviewService.createConceptChangeReportOnBranchForTimeRange("MAIN", afterDeletion, now(), true),
					EMPTY_ARRAY, EMPTY_ARRAY, EMPTY_ARRAY);
		} catch (ElasticsearchParseException e) {
			e.printStackTrace();
		}
	}

	@Test
	public void testDescriptionUpdateOnSameBranchInChangeReport() throws Exception {
		final String path = "MAIN";
		createConcept("10000200", path);
		createConcept("10000300", path);

		Date start = now();

		// Nothing changed since start
		assertReportEquals(reviewService.createConceptChangeReportOnBranchForTimeRange(path, start, now(), true),
				EMPTY_ARRAY, EMPTY_ARRAY, EMPTY_ARRAY);

		final Concept concept = conceptService.find("10000100", path);
		concept.getDescriptions().iterator().next().setCaseSignificanceId(Concepts.ENTIRE_TERM_CASE_SENSITIVE);
		conceptService.update(concept, path);

		// Concept updated from description change
		assertReportEquals(reviewService.createConceptChangeReportOnBranchForTimeRange(path, start, now(), true),
				EMPTY_ARRAY, new Long[] {10000100L}, EMPTY_ARRAY);
	}

	@Test
	public void testDescriptionUpdateOnChildBranchInChangeReport() throws Exception {
		final String path = "MAIN/A";
		createConcept("10000200", path);
		createConcept("10000300", path);

		Date start = now();

		// Nothing changed since start
		assertReportEquals(reviewService.createConceptChangeReportOnBranchForTimeRange(path, start, now(), false),
				EMPTY_ARRAY, EMPTY_ARRAY, EMPTY_ARRAY);

		final Concept concept = conceptService.find("10000100", path);
		final Description description = concept.getDescriptions().iterator().next();
		description.setCaseSignificanceId(Concepts.ENTIRE_TERM_CASE_SENSITIVE);
		conceptService.update(concept, path);

		// Concept updated from description change
		assertReportEquals(reviewService.createConceptChangeReportOnBranchForTimeRange(path, start, now(), false),
				EMPTY_ARRAY, new Long[] {10000100L}, EMPTY_ARRAY);
	}

	@Test
	public void testLangRefsetUpdateOnChildBranchInChangeReport() throws Exception {
		final String path = "MAIN/A";

		Date start = now();

		// Nothing changed since start
		assertReportEquals(reviewService.createConceptChangeReportOnBranchForTimeRange(path, start, now(), false),
				EMPTY_ARRAY, EMPTY_ARRAY, EMPTY_ARRAY);

		final Concept concept = conceptService.find("10000100", path);
		final Description description = concept.getDescriptions().iterator().next();
		description.clearLanguageRefsetMembers();
		description.setAcceptabilityMap(Collections.singletonMap(Concepts.US_EN_LANG_REFSET,
				Concepts.descriptionAcceptabilityNames.get(Concepts.PREFERRED)));
		conceptService.update(concept, path);

		// Concept updated from lang refset change
		assertReportEquals(reviewService.createConceptChangeReportOnBranchForTimeRange(path, start, now(), false),
				EMPTY_ARRAY, new Long[] {10000100L}, EMPTY_ARRAY);
	}

	@Test
	public void testLangRefsetDeletionOnChildBranchInChangeReport() throws Exception {
		final String path = "MAIN/A";

		Date start = now();

		// Nothing changed since start
		assertReportEquals(reviewService.createConceptChangeReportOnBranchForTimeRange(path, start, now(), false),
				EMPTY_ARRAY, EMPTY_ARRAY, EMPTY_ARRAY);

		final Concept concept = conceptService.find("10000100", path);
		final Description description = concept.getDescriptions().iterator().next();
		description.clearLanguageRefsetMembers();
		description.setAcceptabilityMap(new HashMap<>());
		conceptService.update(concept, path);

		// Concept updated from lang refset change
		assertReportEquals(reviewService.createConceptChangeReportOnBranchForTimeRange(path, start, now(), false),
				EMPTY_ARRAY, new Long[] {10000100L}, EMPTY_ARRAY);
	}

	@Test
	public void testLangRefsetDeletionOnBranchInChangeReport() throws Exception {
		final String path = "MAIN";

		Date start = now();

		// Nothing changed since start
		assertReportEquals(reviewService.createConceptChangeReportOnBranchForTimeRange(path, start, now(), true),
				EMPTY_ARRAY, EMPTY_ARRAY, EMPTY_ARRAY);

		final Concept concept = conceptService.find("10000100", path);
		final Description next = concept.getDescriptions().iterator().next();
		next.clearLanguageRefsetMembers();
		next.setAcceptabilityMap(new HashMap<>());
		conceptService.update(concept, path);

		// Concept updated from lang refset change
		assertReportEquals(reviewService.createConceptChangeReportOnBranchForTimeRange(path, start, now(), true),
				EMPTY_ARRAY, new Long[] {10000100L}, EMPTY_ARRAY);
	}

	private Date now() {
		return new Date();
	}

	private void assertReportEquals(BranchReviewConceptChanges report, Long[] conceptsCreated, Long[] conceptsUpdated, Long[] conceptsDeleted) {
		Assert.assertArrayEquals("Concepts Created", conceptsCreated, report.getNewConcepts().toArray());
		Assert.assertArrayEquals("Concepts Updated", conceptsUpdated, report.getChangedConcepts().toArray());
		Assert.assertArrayEquals("Concepts Deleted", conceptsDeleted, report.getDeletedConcepts().toArray());
	}

	private void createConcept(String conceptId, String path) throws ServiceException {
		conceptService.create(
				new Concept(conceptId)
						.addDescription(
								new Description("Heart")
										.setCaseSignificance("CASE_INSENSITIVE")
										.setAcceptabilityMap(Collections.singletonMap(Concepts.US_EN_LANG_REFSET,
												Concepts.descriptionAcceptabilityNames.get(Concepts.ACCEPTABLE)))
						)
						.addRelationship(
								new Relationship(Concepts.ISA, Concepts.SNOMEDCT_ROOT)
						),
				path);
	}

}
