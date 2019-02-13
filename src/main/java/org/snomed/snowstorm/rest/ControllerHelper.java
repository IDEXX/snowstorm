package org.snomed.snowstorm.rest;

import io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil;
import org.elasticsearch.common.Strings;
import org.snomed.snowstorm.core.data.domain.ConceptMini;
import org.snomed.snowstorm.core.data.services.NotFoundException;
import org.snomed.snowstorm.core.pojo.BranchTimepoint;
import org.snomed.snowstorm.rest.pojo.ConceptMiniNestedFsn;
import org.springframework.data.domain.AbstractPageRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.core.SearchAfterPageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.*;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import static io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil.ENCODED_PIPE;
import static io.kaicode.rest.util.branchpathrewrite.BranchPathUriUtil.SLASH;

public class ControllerHelper {

	public static final String DEFAULT_ACCEPT_LANG_HEADER = "en-US;q=0.8,en-GB;q=0.6";

	public static BranchTimepoint parseBranchTimepoint(String branch) {
		String[] parts = BranchPathUriUtil.decodePath(branch).split("@");
		if (parts.length == 1) {
			return new BranchTimepoint(parts[0]);
		} else if (parts.length == 2) {
			return new BranchTimepoint(parts[0], parts[1]);
		} else {
			throw new IllegalArgumentException("Malformed branch path.");
		}
	}

	static ResponseEntity<Void> getCreatedResponse(String id) {
		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.setLocation(ServletUriComponentsBuilder
				.fromCurrentRequest().path("/{id}")
				.buildAndExpand(id).toUri());
		return new ResponseEntity<>(httpHeaders, HttpStatus.CREATED);
	}

	static List<ConceptMiniNestedFsn> nestConceptMiniFsn(Collection<ConceptMini> minis) {
		return minis.stream().map(ConceptMiniNestedFsn::new).collect(Collectors.toList());
	}

	static String requiredParam(String value, String paramName) {
		if (Strings.isNullOrEmpty(value)) {
			throw new IllegalArgumentException(paramName + " is a required parameter.");
		}
		return value;
	}

	static <T> T requiredParam(T value, String paramName) {
		if (value == null) {
			throw new IllegalArgumentException(paramName + " is a required parameter.");
		}
		return value;
	}

	public static <T> T throwIfNotFound(String type, T component) {
		if (component == null) {
			throw new NotFoundException(type + " not found");
		}
		return component;
	}

	public static AbstractPageRequest getPageRequest(@RequestParam(required = false) Integer offset, @RequestParam(required = false, defaultValue = "50") int limit, @RequestParam(required = false) String searchAfter) {
		if (offset!= null && offset % limit != 0) {
			throw new IllegalArgumentException("Offset must be a multiplication of the limit param in order to map to Spring pages.");
		}
		
		if (offset!= null && searchAfter != null) {
			throw new IllegalArgumentException("Offset cannot be used in combination with 'searchAfter'");
		}
		
		//If neither are specified, we'll use "searchAfter" by default
		if (offset == null) {
			return SearchAfterPageRequest.of(searchAfter, limit);
		} else {
			int page = ((offset + limit) / limit) - 1;
			int size = limit;
			return PageRequest.of(page, size);
		}
	}

	public static List<String> getLanguageCodes(String acceptLanguageHeader) {
		List<Locale.LanguageRange> languageRanges = Locale.LanguageRange.parse(acceptLanguageHeader);

		List<String> languageCodes = new ArrayList<>(languageRanges.stream()
				.map(languageRange -> languageRange.getRange().contains("-") ? languageRange.getRange().substring(0, languageRange.getRange().indexOf("-")) : languageRange.getRange())
				.collect(Collectors.toCollection((Supplier<Set<String>>) LinkedHashSet::new)));

		// Always include en at the end if not already present because this is the default language.
		if (!languageCodes.contains("en")) {
			languageCodes.add("en");
		}
		return languageCodes;
	}
}
