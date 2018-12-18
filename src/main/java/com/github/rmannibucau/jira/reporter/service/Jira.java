/**
 *
 * <p>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.github.rmannibucau.jira.reporter.service;

import static java.util.stream.Collectors.toSet;
import static javax.ws.rs.core.MediaType.APPLICATION_JSON_TYPE;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;

import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.Response;

import org.apache.johnzon.jaxrs.jsonb.jaxrs.JsonbJaxrsProvider;

import lombok.Data;

public class Jira implements AutoCloseable {

    private final Client client;

    private final WebTarget target;

    private final String authorization;

    public Jira(final String url, final String username, final String password, final long timeout) {
        client = ClientBuilder.newClient().register(new JsonbJaxrsProvider<>());
        target = client.target(url + "/rest/api/2").property("http.connection.timeout", timeout);
        authorization = username != null
                ? "Basic " + Base64.getEncoder().encodeToString((username + ':' + password).getBytes(StandardCharsets.UTF_8))
                : null;
    }

    @Override
    public void close() {
        if (client != null) {
            client.close();
        }
    }

    public Stream<JiraIssue> query(final String jql, final String[] excludedStatuses) {
        final Function<Long, JiraIssues> searchFrom = startAt -> target.path("search").queryParam("jql", jql)
                .queryParam("startAt", startAt).queryParam("fields", "issuelinks,issuetype,summary,status,fixVersions,project")
                .request(APPLICATION_JSON_TYPE).header("Authorization", authorization).get(JiraIssues.class);
        final Function<JiraIssues, Stream<JiraIssues>> paginate = new Function<JiraIssues, Stream<JiraIssues>>() {

            @Override
            public Stream<JiraIssues> apply(final JiraIssues issues) {
                final long nextStartAt = issues.getStartAt() + issues.getMaxResults();
                final Stream<JiraIssues> fetched = Stream.of(issues);
                return issues.getTotal() > nextStartAt ? Stream.concat(fetched, apply(searchFrom.apply(nextStartAt))).parallel()
                        : fetched;
            }
        };
        final Set<String> excludeStatus = Stream.of(excludedStatuses).collect(toSet());
        return Stream.of(searchFrom.apply(0L)).flatMap(paginate::apply).filter(i -> i.issues != null)
                .flatMap(i -> i.issues.stream()).filter(i -> i.fields != null && i.fields.status != null
                        && excludeStatus.stream().noneMatch(it -> it.equalsIgnoreCase(i.fields.status.name)));
    }

    public String getIcon(final String uri) {
        final Response image = client.target(uri).request().header("Authorization", authorization).get();
        String contentType = image.getHeaderString("Content-Type");
        if (contentType.contains(";")) {
            contentType = contentType.substring(0, contentType.indexOf(';'));
        }
        return "data:" + contentType + ";base64," + Base64.getEncoder().encodeToString(image.readEntity(byte[].class));
    }

    @Data
    public static class JiraVersion {

        private String id;

        private String name;

        private boolean released;

        private boolean archived;

        private long projectId;
    }

    @Data
    public static class JiraIssues {

        private long startAt;

        private long maxResults;

        private long total;

        private Collection<JiraIssue> issues;
    }

    @Data
    public static class IssueType {

        private String name;

        private String inward;

        private String outward;

        private String iconUrl;
    }

    @Data
    public static class JiraIssue {

        private String id;

        private String key;

        private String self;

        private Fields fields;
    }

    @Data
    public static class Status {

        private String name;
    }

    @Data
    public static class Project {

        private String key;

        private Map<String, String> avatarUrls;
    }

    @Data
    public static class IssueLinks {

        private String id;

        private String self;

        private IssueType type;

        private JiraIssue outwardIssue;

        private JiraIssue inwardIssue;
    }

    @Data
    public static class Fields {

        private String summary;

        private Project project;

        private IssueType issuetype;

        private Status status;

        private Collection<IssueLinks> issuelinks;

        private Collection<JiraVersion> fixVersions;
    }
}
