package com.conviso.x9.api;

/** Raw GraphQL documents used by {@link ConvisoApiClient}, kept out of the client for readability. */
final class GraphQLQueries {

    static final String PROJECTS_QUERY =
        "query projects($page: Int, $limit: Int, $params: ProjectSearch, $sortBy: String, $descending: Boolean) {\n" +
        "  projects(\n" +
        "    page: $page\n" +
        "    limit: $limit\n" +
        "    params: $params\n" +
        "    sortBy: $sortBy\n" +
        "    descending: $descending\n" +
        "  ) {\n" +
        "    collection {\n" +
        "      id\n" +
        "      pid\n" +
        "      label\n" +
        "      status\n" +
        "      createdAt\n" +
        "      startDate\n" +
        "      endDate\n" +
        "      estimatedHours\n" +
        "      tags { name }\n" +
        "      projectType { id label }\n" +
        "      environmentCompromised\n" +
        "      assets { id name }\n" +
        "      allocatedAnalyst { portalUser { name email } }\n" +
        "      requirementsProgress { done open pending total }\n" +
        "    }\n" +
        "    metadata { totalCount totalPages }\n" +
        "  }\n" +
        "}";

    static final String ACTIVITIES_QUERY =
        "query Activities($sortBy: ActivitySortByEnum, $descending: Boolean, $pagination: PaginationInput!, $attachmentActionsOnly: Boolean, $params: ActivitiesSearch!) {\n" +
        "  activities(params: $params, sortBy: $sortBy, descending: $descending) {\n" +
        "    collection {\n" +
        "      id\n" +
        "      title\n" +
        "      status\n" +
        "      permittedStatus\n" +
        "      description\n" +
        "      history(pagination: $pagination, attachmentActionsOnly: $attachmentActionsOnly) { metadata { totalCount } }\n" +
        "      reference\n" +
        "      updatedAt\n" +
        "      portalUser { avatarUrl email name }\n" +
        "      check { description id label }\n" +
        "      reason\n" +
        "      assignedUsers { avatarUrl name email }\n" +
        "    }\n" +
        "    metadata { currentPage limitValue totalCount totalPages }\n" +
        "  }\n" +
        "}";

    static final String ISSUES_QUERY =
        "query Issues($pagination: PaginationInput!, $filters: IssuesFiltersInput, $companyId: ID!, $sortOptions: [IssueSortOptionInput!]) {\n" +
        "  issues(\n" +
        "    pagination: $pagination\n" +
        "    filters: $filters\n" +
        "    companyId: $companyId\n" +
        "    sortOptions: $sortOptions\n" +
        "  ) {\n" +
        "    collection {\n" +
        "      id\n" +
        "      title\n" +
        "      type\n" +
        "      status\n" +
        "      severity\n" +
        "      asset {\n" +
        "        id\n" +
        "        name\n" +
        "      }\n" +
        "      ... on VulnerabilityInterface {\n" +
        "        compromisedEnvironment\n" +
        "      }\n" +
        "      createdAt\n" +
        "      updatedAt\n" +
        "      aiAgentAnalysis {\n" +
        "        fpAnalyzed\n" +
        "      }\n" +
        "    }\n" +
        "    metadata {\n" +
        "      currentPage\n" +
        "      limitValue\n" +
        "      totalCount\n" +
        "      totalPages\n" +
        "    }\n" +
        "  }\n" +
        "}";

    static final String UPDATE_ACTIVITY_STATUS_MUTATION =
        "mutation UpdateActivityStatus($input: UpdateActivityStatusInput!) {\n" +
        "  updateActivityStatus(input: $input) {\n" +
        "    clientMutationId\n" +
        "    errors\n" +
        "    activity {\n" +
        "      id\n" +
        "      status\n" +
        "      permittedStatus\n" +
        "    }\n" +
        "  }\n" +
        "}";

    static final String ADD_ACTIVITY_ATTACHMENT_MUTATION =
        "mutation AddActivityAttachment($input: AddActivityAttachmentInput!) {\n" +
        "  addActivityAttachment(input: $input) {\n" +
        "    clientMutationId\n" +
        "    errors\n" +
        "    activity {\n" +
        "      id\n" +
        "      status\n" +
        "    }\n" +
        "  }\n" +
        "}";

    static final String CREATE_WEB_VULNERABILITY_MUTATION =
        "mutation CreateWebVulnerability($input: CreateWebVulnerabilityInput!) {\n" +
        "  createWebVulnerability(input: $input) {\n" +
        "    issue {\n" +
        "      id\n" +
        "    }\n" +
        "  }\n" +
        "}";

    static final String VULNERABILITY_TEMPLATES_QUERY =
        "query vulnerabilitiesTemplatesByCompanyId($companyId: ID!, $page: Int, $limit: Int, $search: String, $sortBy: VulnerabilityTemplateSortByEnum, $descending: Boolean) {\n" +
        "  vulnerabilitiesTemplatesByCompanyId(\n" +
        "    id: $companyId\n" +
        "    page: $page\n" +
        "    limit: $limit\n" +
        "    search: $search\n" +
        "    sortBy: $sortBy\n" +
        "    descending: $descending\n" +
        "  ) {\n" +
        "    collection {\n" +
        "      description\n" +
        "      id\n" +
        "      title\n" +
        "      criticity\n" +
        "      createdAt\n" +
        "      updatedAt\n" +
        "      company { label }\n" +
        "    }\n" +
        "    metadata { totalCount totalPages }\n" +
        "  }\n" +
        "}";

    static final String VULNERABILITY_TEMPLATE_BY_ID_QUERY =
        "query vulnerabilityTemplate($id: ID!) {\n" +
        "  vulnerabilityTemplate(id: $id) {\n" +
        "    categoryList\n" +
        "    criticity\n" +
        "    probability\n" +
        "    description\n" +
        "    impact\n" +
        "    impactResume\n" +
        "    notification\n" +
        "    patternList\n" +
        "    reference\n" +
        "    solution\n" +
        "    title\n" +
        "  }\n" +
        "}";

    static final String CREATE_ATTACHMENT_MUTATION =
        "mutation CreateAttachment($archive: Upload!, $companyId: ID!, $projectId: Int, $issueId: Int) {\n" +
        "  createAttachment(\n" +
        "    input: {companyId: $companyId, archive: $archive, issueId: $issueId, projectId: $projectId}\n" +
        "  ) {\n" +
        "    attachment {\n" +
        "      id\n" +
        "      presignedUrl\n" +
        "      archiveFilename\n" +
        "      archiveSize\n" +
        "      createdAt\n" +
        "      portalUser { email }\n" +
        "    }\n" +
        "  }\n" +
        "}";

    private GraphQLQueries() {
    }
}
