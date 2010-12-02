<%@ include file="/WEB-INF/template/include.jsp" %>

<%@ attribute name="formFieldName" required="true" %>
<%@ attribute name="formFieldId" required="false" %>
<%@ attribute name="searchLabel" required="false" %>
<%@ attribute name="searchLabelCode" required="false" %>
<%@ attribute name="searchLabelArguments" required="false" %>
<%@ attribute name="roles" required="false" %> <%-- deprecated --%>
<%@ attribute name="initialValue" required="false" %> <%-- This should be a personId --%>
<%@ attribute name="linkUrl" required="false" %> <%-- deprecated --%>
<%@ attribute name="callback" required="false" %>
<%@ attribute name="canAddNewPerson" required="false" %> <%-- deprecated --%>
<%@ attribute name="useOnKeyDown" required="false" %> <%-- deprecated --%>

<openmrs:htmlInclude file="/dwr/interface/DWRPersonService.js" />
<openmrs:htmlInclude file="/scripts/jquery/autocomplete/PersonAutoComplete.js" />

<c:if test="${empty formFieldId}">
	<c:set var="formFieldId" value="${formFieldName}_id" />
</c:if>
<c:set var="displayNameInputId" value="${formFieldId}_selection" />

<script type="text/javascript">
	
	$j(document).ready( function() {

		// set up the autocomplete
		new AutoComplete("${displayNameInputId}", new PersonSearchCallback({roles:"${roles}"}).personCallback(), {
			select: function(event, ui) {
				$j('#${formFieldId}').val(ui.item.id);
					
				<c:if test="${not empty callback}">
				if (ui.item.id) {
					// only call the callback if we got a true selection, not a click on an error field
					${callback}("${formFieldName}", ui.item.object);
				}
				</c:if>
			}
		});

		// get the name of the person that they passed in the id for
		<c:if test="${not empty initialValue}">
			$j("#${formFieldId}").val("${initialValue}");
			DWRPersonService.getPerson("${initialValue}", function(person) { $j('#${displayNameInputId}').val(person.personName);});
		</c:if>
		
	})
</script>

<input type="text" id="${displayNameInputId}" />
<input type="hidden" name="${formFieldName}" id="${formFieldId}" />