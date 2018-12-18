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

import javax.ws.rs.WebApplicationException;
import javax.ws.rs.core.Response;

import org.tomitribe.crest.api.interceptor.CrestContext;
import org.tomitribe.crest.api.interceptor.CrestInterceptor;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class ExceptionHandler {
    @CrestInterceptor
    public Object onCommand(final CrestContext crestContext) {
        try {
            return crestContext.proceed();
        } catch (final RuntimeException re) {
            log.error(stringify(re), re);
            throw re;
        }
    }

    private String stringify(final RuntimeException re) {
        if (WebApplicationException.class.isInstance(re.getCause())) {
            final WebApplicationException wae = WebApplicationException.class.cast(re.getCause());
            final Response response = wae.getResponse();
            return "HTTP " + response.getStatus() + ": " + (response.hasEntity() ? response.readEntity(String.class) : "-");
        }
        return re.getMessage();
    }
}
