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

import static java.util.stream.Collectors.joining;

import java.util.ArrayList;
import java.util.Collection;

import javax.json.bind.Jsonb;
import javax.json.bind.JsonbBuilder;
import javax.json.bind.JsonbConfig;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
public class Cytoscape {

    private final Elements elements = new Elements();

    public String asHtml(final String title) {
        final StringBuilder builder = new StringBuilder("<!DOCTYPE html>\n");
        builder.append("<html>\n");
        builder.append("<head>\n");
        builder.append("<meta charset=utf-8 />\n");
        builder.append("<title>").append(title).append("</title>\n");
        builder.append("<style>\n");
        builder.append("body { \n");
        builder.append("  font: 14px helvetica neue, helvetica, arial, sans-serif;\n");
        builder.append("  background: black;\n");
        builder.append("}\n");
        builder.append("\n");
        builder.append("#cy {\n");
        builder.append("  height: 100%;\n");
        builder.append("  width: 100%;\n");
        builder.append("  position: absolute;\n");
        builder.append("  left: 0;\n");
        builder.append("  top: 0;\n");
        builder.append("}\n");
        builder.append("\n");
        builder.append("#info {\n");
        builder.append("  color: #c88;\n");
        builder.append("  font-size: 1em;\n");
        builder.append("  position: absolute;\n");
        builder.append("  z-index: -1;\n");
        builder.append("  left: 1em;\n");
        builder.append("  top: 1em;\n");
        builder.append("}\n");
        builder.append("</style>\n");
        builder.append("</head>\n");
        builder.append("<body>\n");
        builder.append("<div id=\"detail\"></div>\n");
        builder.append("<div id=\"cy\"></div>\n");
        builder.append("<script src=\"https://cdnjs.cloudflare.com/ajax/libs/cytoscape/3.2.22/cytoscape.min.js\" ");
        builder.append("  integrity=\"sha256-Bqs25OhKdh8ooPMp5xt7cUlfLylUhYzSG6OIAD0DJzM=\" crossorigin=\"anonymous\"></script>\n");
        builder.append("<script>\n");
        builder.append("var elements = \n");
        try (final Jsonb jsonb = JsonbBuilder.create(new JsonbConfig().withFormatting(true))) {
            builder.append(jsonb.toJson(elements));
        } catch (final Exception e) {
            throw new IllegalStateException(e);
        }
        builder.append(";\n");
        builder.append("\n");
        builder.append("var cy = cytoscape({\n");
        builder.append("  container: document.getElementById('cy'),\n");
        builder.append("  elements: elements,\n");
        builder.append("  boxSelectionEnabled: false,\n");
        builder.append("  autounselectify: true,\n");
        builder.append("  style: cytoscape.stylesheet()\n");
        builder.append("    .selector('node')\n");
        builder.append("      .css({\n");
        builder.append("        'content': 'data(name)',\n");
        builder.append("        'text-valign': 'center',\n");
        builder.append("        'color': 'white',\n");
        builder.append("        'text-outline-width': 1,\n");
        builder.append("        'background-color': 'grey'\n");
        builder.append("      })\n");
        builder.append(elements.getNodes().stream().map(Node::getData).filter(it -> !it.getIcon().isEmpty())
                .map(it -> "    .selector('#" + it.getId() + "').css({ 'background-image': '" + it.getIcon() + "' })\n")
                .collect(joining()));
        builder.append(" ,\n");
        builder.append("  layout: {\n");
        builder.append("    name: 'cose'\n");
        builder.append("  }\n");
        builder.append("}).on('tap', 'node', function(){\n");
        builder.append("  try {\n");
        builder.append("    window.open( this.data('href') );\n");
        builder.append("  } catch (e) {\n");
        builder.append("    window.location.href = this.data('href');\n");
        builder.append("  }\n");
        builder.append("});");
        builder.append("</script>\n");
        builder.append("</body>\n");
        builder.append("</html>\n");
        return builder.toString();
    }

    @Data
    @AllArgsConstructor
    public static class NodeData {

        private String id;

        private String icon;

        private String summary;

        private String name;

        private String href;
    }

    @Data
    @AllArgsConstructor
    public static class EdgeData {

        private String source;

        private String target;
    }

    @Data
    @AllArgsConstructor
    public static class Node {

        private NodeData data;
    }

    @Data
    @AllArgsConstructor
    public static class Edge {

        private EdgeData data;
    }

    @Data
    @AllArgsConstructor
    public static class Elements {

        private final Collection<Node> nodes = new ArrayList<>();

        private final Collection<Edge> edges = new ArrayList<>();

        public void addNode(final NodeData node) {
            nodes.add(new Node(node));
        }

        public void addEdge(final EdgeData edge) {
            edges.add(new Edge(edge));
        }
    }
}
