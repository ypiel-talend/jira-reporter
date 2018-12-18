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
package com.github.rmannibucau.jira.reporter.command;

import static java.util.Optional.ofNullable;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

import org.tomitribe.crest.api.Command;
import org.tomitribe.crest.api.Default;
import org.tomitribe.crest.api.Option;
import org.tomitribe.crest.api.Out;

import com.github.rmannibucau.jira.reporter.interceptors.DefaultParams;
import com.github.rmannibucau.jira.reporter.interceptors.ExceptionHandler;
import com.github.rmannibucau.jira.reporter.service.Cytoscape;
import com.github.rmannibucau.jira.reporter.service.Jira;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Command("jira")
public class JiraCommand {

    @Command(interceptedBy = {
            DefaultParams.class,
            ExceptionHandler.class
    })
    public static void report(@Option("base-url") final String url,
                              @Option("username") @Default("${env.USER}") final String username,
                              @Option("password") final String password,
                              @Option("jql") final String jql,
                              @Option("exclude-status") final String[] excludedStatuses,
                              @Option("timeout") @Default("60000") final long timeout,
                              @Option("output") @Default("stdout") final String output,
                              @Option("title") @Default("Report") final String title,
                              @Out final PrintStream stdout) throws FileNotFoundException {
        try (final Jira jira = new Jira(url, username, password, timeout);
             final PrintStream out = "stdout".equalsIgnoreCase(output) ?
                     new PrintStream(stdout) {
                         @Override
                         public void close() {
                             flush();
                         }
                     } : new PrintStream(new FileOutputStream(output))) {
            final Cytoscape cytoscape = new Cytoscape();

            // start by loading matching issues
            final Map<String, Jira.JiraIssue> issues = jira.query(jql, excludedStatuses)
                                                           .collect(toMap(Jira.JiraIssue::getId, identity()));
            // ensure we have all linked issues
            final String missingIds = issues.values().stream()
                  .filter(it -> it.getFields().getIssuelinks() != null)
                  .flatMap(it -> it.getFields().getIssuelinks().stream())
                  .flatMap(l -> Stream.of(l.getInwardIssue(), l.getOutwardIssue()).filter(Objects::nonNull))
                  .filter(i -> !issues.containsKey(i.getId()))
                  .map(Jira.JiraIssue::getId)
                  .collect(joining(", "));
            if (!missingIds.isEmpty()) {
                jira.query("id in (" + missingIds + ")", excludedStatuses)
                    .forEach(i -> issues.put(i.getId(), i));
            }

            // load icons (as few times as possible)
            final Map<String, String> projectIcons = issues.values().stream()
                    .map(JiraCommand::getIcon)
                    .distinct().parallel()
                    .collect(toMap(identity(), jira::getIcon));

            // build the graph
            issues.values().forEach(issue -> {
                    cytoscape.getElements().addNode(new Cytoscape.NodeData(
                            issue.getId(), projectIcons.get(getIcon(issue)),
                            issue.getFields().getSummary(),
                            issue.getKey(),
                            url + "/browse/" + issue.getKey()
                    ));
                    ofNullable(issue.getFields().getIssuelinks()).ifPresent(links -> links.forEach(link -> {
                        if (link.getInwardIssue() != null && issues.containsKey(link.getInwardIssue().getId())) {
                            cytoscape.getElements().addEdge(new Cytoscape.EdgeData(link.getInwardIssue().getId(), issue.getId()));
                        }
                        if (link.getOutwardIssue() != null && issues.containsKey(link.getOutwardIssue().getId())) {
                            cytoscape.getElements().addEdge(new Cytoscape.EdgeData(issue.getId(), link.getOutwardIssue().getId()));
                        }
                    }));
                });
            out.println(cytoscape.asHtml(title));
            log.info("Created report at '{}'", output);
        }
    }

    private static String getIcon(final Jira.JiraIssue issue) {
        return ofNullable(issue.getFields().getProject().getAvatarUrls().get("32x32")).orElseGet(() -> {
            final Map<String, String> urls = issue.getFields().getProject().getAvatarUrls();
            return urls.isEmpty() ? "" : urls.values().iterator().next();
        });
    }
}
