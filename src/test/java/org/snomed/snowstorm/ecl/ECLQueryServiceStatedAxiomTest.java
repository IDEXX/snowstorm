package org.snomed.snowstorm.ecl;

import com.google.common.collect.Sets;
import io.kaicode.elasticvc.api.ComponentService;
import org.junit.Before;
import org.junit.Test;
import org.snomed.snowstorm.core.data.domain.*;
import org.snomed.snowstorm.core.data.services.ServiceException;

import java.util.*;
import java.util.stream.Collectors;

import static org.junit.Assert.assertEquals;
import static org.snomed.snowstorm.core.data.domain.Concepts.*;

/**
 * In this test suite we run all the same ECL query tests again against the stated form
 * but the data is set up using axioms without any stated relationships.
 */
public class ECLQueryServiceStatedAxiomTest extends ECLQueryServiceTest {

	@Before
	public void setup() throws ServiceException {
		branchService.create(MAIN);

		List<Concept> allConcepts = new ArrayList<>();

		allConcepts.add(new Concept(SNOMEDCT_ROOT));
		allConcepts.add(new Concept(ISA).addAxiom(new Relationship(ISA, SNOMEDCT_ROOT)));
		allConcepts.add(new Concept(MODEL_COMPONENT).addAxiom(new Relationship(ISA, SNOMEDCT_ROOT)));
		allConcepts.add(new Concept(FINDING_SITE).addAxiom(new Relationship(ISA, MODEL_COMPONENT)));
		allConcepts.add(new Concept(ASSOCIATED_MORPHOLOGY).addAxiom(new Relationship(ISA, MODEL_COMPONENT)));
		allConcepts.add(new Concept(PROCEDURE_SITE).addAxiom(new Relationship(ISA, MODEL_COMPONENT)));
		allConcepts.add(new Concept(PROCEDURE_SITE_DIRECT).addAxiom(new Relationship(ISA, PROCEDURE_SITE)));
		allConcepts.add(new Concept(LATERALITY).addAxiom(new Relationship(ISA, MODEL_COMPONENT)));
		allConcepts.add(new Concept(RIGHT).addAxiom(new Relationship(ISA, SNOMEDCT_ROOT)));

		allConcepts.add(new Concept(BODY_STRUCTURE).addAxiom(new Relationship(ISA, SNOMEDCT_ROOT)));
		allConcepts.add(new Concept(HEART_STRUCTURE).addAxiom(new Relationship(ISA, BODY_STRUCTURE)));
		allConcepts.add(new Concept(SKIN_STRUCTURE).addAxiom(new Relationship(ISA, BODY_STRUCTURE)));
		allConcepts.add(new Concept(THORACIC_STRUCTURE).addAxiom(new Relationship(ISA, BODY_STRUCTURE)));
		allConcepts.add(new Concept(PULMONARY_VALVE_STRUCTURE).addAxiom(new Relationship(ISA, BODY_STRUCTURE)));
		allConcepts.add(new Concept(RIGHT_VENTRICULAR_STRUCTURE).addAxiom(
				new Relationship(ISA, BODY_STRUCTURE),
				new Relationship(LATERALITY, RIGHT)));

		allConcepts.add(new Concept(STENOSIS).addAxiom(new Relationship(ISA, BODY_STRUCTURE)));
		allConcepts.add(new Concept(HYPERTROPHY).addAxiom(new Relationship(ISA, BODY_STRUCTURE)));
		allConcepts.add(new Concept(HEMORRHAGE).addAxiom(new Relationship(ISA, BODY_STRUCTURE)));

		allConcepts.add(new Concept(CLINICAL_FINDING).addAxiom(new Relationship(ISA, SNOMEDCT_ROOT)));
		allConcepts.add(new Concept(DISORDER).addAxiom(new Relationship(ISA, CLINICAL_FINDING)));
		allConcepts.add(new Concept(BLEEDING).addAxiom(
				new Relationship(ISA, CLINICAL_FINDING),
				new Relationship(ASSOCIATED_MORPHOLOGY, HEMORRHAGE)));

		allConcepts.add(new Concept(BLEEDING_SKIN).addAxiom(
				new Relationship(ISA, BLEEDING),
				new Relationship(ASSOCIATED_MORPHOLOGY, HEMORRHAGE),
				new Relationship(FINDING_SITE, SKIN_STRUCTURE)));

		allConcepts.add(new Concept(PENTALOGY_OF_FALLOT).addAxiom(
				new Relationship(ISA, DISORDER),
				new Relationship(FINDING_SITE, PULMONARY_VALVE_STRUCTURE).setGroupId(1),
				new Relationship(ASSOCIATED_MORPHOLOGY, STENOSIS).setGroupId(1),
				new Relationship(FINDING_SITE, RIGHT_VENTRICULAR_STRUCTURE).setGroupId(2),
				new Relationship(ASSOCIATED_MORPHOLOGY, HYPERTROPHY).setGroupId(2)));

		allConcepts.add(new Concept(PENTALOGY_OF_FALLOT_INCORRECT_GROUPING).addAxiom(
				new Relationship(ISA, DISORDER),
				new Relationship(FINDING_SITE, PULMONARY_VALVE_STRUCTURE).setGroupId(1),
				new Relationship(ASSOCIATED_MORPHOLOGY, STENOSIS).setGroupId(2),// <-- was group 1
				new Relationship(FINDING_SITE, RIGHT_VENTRICULAR_STRUCTURE).setGroupId(2),
				new Relationship(ASSOCIATED_MORPHOLOGY, HYPERTROPHY).setGroupId(1)));// <-- was group 2

		allConcepts.add(new Concept(PROCEDURE).addAxiom(new Relationship(ISA, SNOMEDCT_ROOT)));
		allConcepts.add(new Concept(OPERATION_ON_HEART).addAxiom(
				new Relationship(ISA, PROCEDURE),
				new Relationship(PROCEDURE_SITE, HEART_STRUCTURE)));

		allConcepts.add(new Concept(CHEST_IMAGING).addAxiom(
				new Relationship(ISA, PROCEDURE),
				new Relationship(PROCEDURE_SITE_DIRECT, THORACIC_STRUCTURE)));


		conceptService.create(allConcepts, MAIN);

		allConceptIds = allConcepts.stream().map(Concept::getId).collect(Collectors.toSet());

		memberService.createMembers(MAIN, Sets.newHashSet(
				new ReferenceSetMember(Concepts.CORE_MODULE, Concepts.REFSET_MRCM_ATTRIBUTE_DOMAIN, CLINICAL_FINDING),
				new ReferenceSetMember(Concepts.CORE_MODULE, Concepts.REFSET_MRCM_ATTRIBUTE_DOMAIN, BODY_STRUCTURE)));

		branchCriteria = versionControlHelper.getBranchCriteria(MAIN);

		String bleedingOwlExpression = memberService.findMembers(MAIN, BLEEDING, ComponentService.LARGE_PAGE).getContent().iterator().next().getAdditionalField(ReferenceSetMember.OwlExpressionFields.OWL_EXPRESSION);

		/*
		SubClassOf(
			:131148009
			ObjectIntersectionOf(
				:404684003
				ObjectSomeValuesFrom(
					:609096000 - group - self grouped
					ObjectSomeValuesFrom(
						:116676008
						:50960005))))
		 */

		assertEquals("SubClassOf(:131148009 ObjectIntersectionOf(:404684003 ObjectSomeValuesFrom(:609096000 ObjectSomeValuesFrom(:116676008 :50960005))))", bleedingOwlExpression);

		/*
		SubClassOf(
			:297968009
			ObjectIntersectionOf(
				:131148009
				ObjectSomeValuesFrom(
					:609096000 - group - self grouped
					ObjectSomeValuesFrom(
						:116676008
						:50960005
					)
				)
				ObjectSomeValuesFrom(
					:609096000 - group - self grouped
					ObjectSomeValuesFrom(
						:363698007
						:39937001))))
		 */
		assertEquals("SubClassOf(:297968009 ObjectIntersectionOf(:131148009 ObjectSomeValuesFrom(:609096000 ObjectSomeValuesFrom(:116676008 :50960005)) ObjectSomeValuesFrom(:609096000 ObjectSomeValuesFrom(:363698007 :39937001))))",
				memberService.findMembers(MAIN, BLEEDING_SKIN, ComponentService.LARGE_PAGE).getContent().iterator().next().getAdditionalField(ReferenceSetMember.OwlExpressionFields.OWL_EXPRESSION));
	}

	@Test
	// In axioms all non is-a attributes in group 0 become self grouped unless the MRCM Attribute Domain reference set explicitly states that they should never be grouped
	public void attributeGroupCardinality() {
		assertEquals(
				"Match clinical finding with at least one grouped finding site attributes.",
				Sets.newHashSet(PENTALOGY_OF_FALLOT, PENTALOGY_OF_FALLOT_INCORRECT_GROUPING, BLEEDING_SKIN),
				strings(selectConceptIds("<404684003 |Clinical finding|: { 363698007 |Finding site| = * }")));

		assertEquals(
				"Match clinical finding with zero grouped finding site attributes.",
				Sets.newHashSet(DISORDER, BLEEDING),
				strings(selectConceptIds("<404684003 |Clinical finding|: [0..0]{ 363698007 |Finding site| = * }")));

		assertEquals(
				"Match clinical finding with one grouped finding site attributes.",
				Sets.newHashSet(BLEEDING_SKIN),
				strings(selectConceptIds("<404684003 |Clinical finding|: [1..1]{ 363698007 |Finding site| = * }")));

		assertEquals(
				"Match clinical finding with one or two grouped finding site attributes.",
				Sets.newHashSet(PENTALOGY_OF_FALLOT, PENTALOGY_OF_FALLOT_INCORRECT_GROUPING, BLEEDING_SKIN),
				strings(selectConceptIds("<404684003 |Clinical finding|: [1..2]{ 363698007 |Finding site| = * }")));

		assertEquals(
				"Match clinical finding with three or more grouped finding site attributes.",
				Sets.newHashSet(),
				strings(selectConceptIds("<404684003 |Clinical finding|: [3..*]{ 363698007 |Finding site| = * }")));
	}

	@Test
	public void attributeGroupDisjunction() {
		// With axioms the associated morphology is automatically grouped
		// so this assertion checks that finding site has to be in the same group so as to exclude the Bleeding concept.
		assertEquals(
				"Match clinical finding with at least one grouped finding site attributes.",
				Sets.newHashSet(PENTALOGY_OF_FALLOT, PENTALOGY_OF_FALLOT_INCORRECT_GROUPING),// No bleeding because |Associated morphology| must be grouped
				strings(selectConceptIds("<404684003 |Clinical finding|: { 363698007 |Finding site| = * } OR { 116676008 |Associated morphology| = *, 363698007 |Finding site| = *}")));
	}

	@Test
	public void selectMemberOfReferenceSet() {
		// Member of x
		assertEquals(
				Sets.newHashSet(CLINICAL_FINDING, BODY_STRUCTURE),
				strings(selectConceptIds("^" + REFSET_MRCM_ATTRIBUTE_DOMAIN))
		);

		// Member of any reference set
		// All concepts with axioms are members
		assertEquals(allConceptIds.stream().filter(id -> !id.equals(Concepts.SNOMEDCT_ROOT)).collect(Collectors.toSet()), strings(selectConceptIds("^*")));
	}

}
