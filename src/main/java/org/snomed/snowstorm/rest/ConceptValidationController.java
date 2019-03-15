package org.snomed.snowstorm.rest;

import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import io.swagger.annotations.ApiParam;
import org.ihtsdo.drools.response.InvalidContent;
import org.snomed.snowstorm.core.data.domain.Concept;
import org.snomed.snowstorm.core.data.services.QueryService;
import org.snomed.snowstorm.core.data.services.ServiceException;
import org.snomed.snowstorm.validation.DroolsValidationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import javax.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@Api(tags = "Validation", description = "Drools")
@RequestMapping(produces = "application/json")
public class ConceptValidationController {

	@Autowired
	private DroolsValidationService validationService;

	@ResponseBody
	@RequestMapping(value = "/browser/{branch}/validate/concept", method = RequestMethod.POST)
	@ApiOperation("Validation using the Snomed-Drools project.")
	public List<InvalidContent> validateConcept(@ApiParam(value="The branch path") @PathVariable(value="branch") @NotNull final String branchPath,
			@ApiParam(value="The concept to validate") @RequestBody Concept concept) throws ServiceException {

		String branchPath1 = BranchPathUriUtil.decodePath(branchPath);
		return validationService.validateConcept(branchPath1, concept);
	}

	@ResponseBody
	@RequestMapping(value = "/browser/{branch}/validate/concepts", method = RequestMethod.POST)
	@ApiOperation("Validation using the Snomed-Drools project.")
	public List<InvalidContent> validateConcepts(@ApiParam(value="The branch path") @PathVariable(value="branch") @NotNull final String branchPath,
			@ApiParam(value="The concepts to validate") @RequestBody Set<Concept> concepts) throws ServiceException {

		String branchPath1 = BranchPathUriUtil.decodePath(branchPath);
		return validationService.validateConcepts(branchPath1, concepts);
	}

	@RequestMapping(value = "/validation-maintenance/reload-validation-rules", method = RequestMethod.POST)
	@ApiOperation("Reload SNOMED Drools assertions and test resources.")
	public void reloadDrools() throws ServiceException {
		validationService.newRuleExecutorAndResources();
	}

}
