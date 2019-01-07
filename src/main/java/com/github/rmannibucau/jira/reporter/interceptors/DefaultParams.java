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
package com.github.rmannibucau.jira.reporter.interceptors;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.tomitribe.crest.api.interceptor.CrestContext;
import org.tomitribe.crest.api.interceptor.CrestInterceptor;
import org.tomitribe.crest.api.interceptor.ParameterMetadata;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class DefaultParams {
    private final Map<String, String> values = new HashMap<>();

    public DefaultParams() {
        final File file = new File(System.getProperty("user.home"), ".defaults.properties");
        if (file.exists()) {
            try (final FileReader reader = new FileReader(file)) {
                final Properties properties = new Properties();
                properties.load(reader);
                properties.stringPropertyNames().forEach(k -> {
                    final String value = properties.getProperty(k);
                    values.put(k, value.startsWith("base64:") ?
                            new String(Base64.getDecoder().decode(value.substring("base64:".length())), StandardCharsets.UTF_8) : value);
                });
            } catch (final IOException e) {
                throw new IllegalStateException(e);
            }
            log.debug("Loaded {}", file);
        } else {
            log.debug("No {} available", file);
        }
    }

    @CrestInterceptor
    public Object onCommand(final CrestContext crestContext) {
        for (int i = 0; i < crestContext.getParameterMetadata().size(); i++) {
            final ParameterMetadata parameterMetadata = crestContext.getParameterMetadata().get(i);
            final String value = values.get(parameterMetadata.getName());
            if (value != null) {
                crestContext.getParameters().set(i, coerce(parameterMetadata, value));
            }
        }
        return crestContext.proceed();
    }

    private Object coerce(final ParameterMetadata parameterMetadata, final String value) {
        if (parameterMetadata.getReflectType() == String.class) {
            return value;
        }
        if (parameterMetadata.getReflectType() == long.class) {
            return Long.parseLong(value.trim());
        }
        if (parameterMetadata.getReflectType() == String[].class) {
            return value.trim().split(",");
        }
        throw new IllegalArgumentException("Unsupported default for " + parameterMetadata.getName());
    }
}
