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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.tomitribe.crest.api.Command;
import org.tomitribe.crest.api.Default;
import org.tomitribe.crest.api.Option;
import org.tomitribe.crest.api.Out;

import com.github.rmannibucau.jira.reporter.interceptors.DefaultParams;
import com.github.rmannibucau.jira.reporter.interceptors.ExceptionHandler;
import com.github.rmannibucau.jira.reporter.service.Cytoscape;
import com.github.rmannibucau.jira.reporter.service.Jira;
import com.github.rmannibucau.jira.reporter.service.Jira.Project;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Command("jira")
public class JiraCommand {

    private static Map<String, Jira.JiraIssue> issues;

    private static String firstIcon;

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
            issues = jira.query(jql, excludedStatuses)
                                                           .collect(toMap(Jira.JiraIssue::getId, identity()));

            Collection<Jira.JiraIssue> values = new ArrayList<>(issues.values());
            for (Jira.JiraIssue i : values) {
                loadDependencies(i, jira, excludedStatuses);
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

    private static void loadDependencies(Jira.JiraIssue issue, Jira jira, final String[] excludedStatuses){
        //Collection<Jira.JiraIssue> links = issue.getFields().getIssuelinks();
        StringBuilder ids = new StringBuilder();

        List<Jira.JiraIssue> missings = issue.getFields().getIssuelinks().stream()
                .flatMap(l -> Stream.of(l.getInwardIssue(), l.getOutwardIssue())
                .filter(Objects::nonNull))
                .filter(i -> !issues.containsKey(i.getId())).collect(Collectors.toList());

        for(Jira.JiraIssue i : missings){
            if(!issues.containsKey(i.getId())){
                List<Jira.JiraIssue> res =
                        jira.query("id = " + i.getId(), excludedStatuses).collect(Collectors.toList());

                if(res.size() == 1) {
                    Jira.JiraIssue jiraIssue = res.get(0);
                    issues.put(jiraIssue.getId(), jiraIssue);
                    if (ids.length() > 0) {
                        ids.append(",");
                    }
                    ids.append(jiraIssue.getId());
                }
            }

        }

        if(ids.length() > 0){
            List<Jira.JiraIssue> deps = jira.query("id in (" + ids + ")", excludedStatuses).collect(Collectors.toList());
            for(Jira.JiraIssue i : deps){
                loadDependencies(i, jira, excludedStatuses);
            }
        }
    }

    private static String getIcon(final Jira.JiraIssue issue) {

        Project prj = issue.getFields().getProject();
        if(prj == null){
            return "";
        }

       String icon = "";
       try {
           icon = ofNullable(issue.getFields().getProject().getAvatarUrls().get("32x32")).orElseGet(() -> {
               final Map<String, String> urls = issue.getFields().getProject().getAvatarUrls();
               return urls.isEmpty() ? "" : urls.values().iterator().next();
           });
       }
       catch(Exception e){
           log.error("Can't retrieve icon => " + e.getMessage());
           e.printStackTrace();
       }

        if (firstIcon == null) {
           firstIcon = icon;
        }

       return icon;
    }
}
